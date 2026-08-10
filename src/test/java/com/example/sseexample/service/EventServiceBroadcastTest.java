package com.example.sseexample.service;

import com.example.sseexample.config.SseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the async broadcast added for issue #15: a slow consumer must not stall
 * delivery to the others, and must not cause healthy consumers to be dropped.
 */
class EventServiceBroadcastTest {

    /** Emitter whose send blocks until released, simulating a slow consumer. */
    private static class BlockingEmitter extends SseEmitter {
        private final CountDownLatch release;
        private final AtomicBoolean sendStarted = new AtomicBoolean(false);
        private final AtomicBoolean sendCompleted = new AtomicBoolean(false);

        BlockingEmitter(CountDownLatch release) {
            super(60_000L);
            this.release = release;
        }

        @Override
        public void send(SseEventBuilder builder) {
            sendStarted.set(true);
            // A send blocked on a full socket buffer does not unblock on interrupt, so
            // this fake deliberately ignores interruption rather than exiting on cancel.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                try {
                    if (release.await(20, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    // Deliberately swallowed; see comment above.
                }
            }
            sendCompleted.set(true);
        }

        @Override
        public void completeWithError(Throwable ex) {
            // No async request bound in a unit test; record only.
        }
    }

    /** Emitter that returns from send immediately. */
    private static class FastEmitter extends SseEmitter {
        private final AtomicBoolean received = new AtomicBoolean(false);

        FastEmitter() {
            super(60_000L);
        }

        @Override
        public void send(SseEventBuilder builder) {
            received.set(true);
        }
    }

    private static SseProperties props(long sendTimeoutMs, int broadcastThreads) {
        return new SseProperties(10, 300_000L, sendTimeoutMs, broadcastThreads);
    }

    @Test
    void broadcastEvent_SlowConsumer_IsDroppedAndDoesNotBlockOthers() throws Exception {
        EventService service = new EventService(props(100L, 4), false);
        CountDownLatch release = new CountDownLatch(1);
        BlockingEmitter slow = new BlockingEmitter(release);
        FastEmitter fast = new FastEmitter();
        service.admit(slow);
        service.admit(fast);

        long start = System.nanoTime();
        service.broadcastEvent("test", "payload");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(fast.received.get(), "fast consumer should have received the event");
        assertTrue(elapsedMs < 4_000, "broadcast should not wait out the slow consumer, took " + elapsedMs + "ms");
        assertFalse(service.releaseEmitter(slow), "slow consumer should already have been dropped");
        assertTrue(service.releaseEmitter(fast), "fast consumer should still be registered");
        release.countDown();
        service.shutdown();
    }

    @Test
    void broadcastEvent_ConsumerQueuedBehindSlowOne_IsNotDropped() throws Exception {
        // Single broadcast thread: the second emitter's send never starts before the
        // timeout elapses. It is starved, not slow, so it must survive.
        EventService service = new EventService(props(100L, 1), false);
        CountDownLatch release = new CountDownLatch(1);
        BlockingEmitter slow = new BlockingEmitter(release);
        FastEmitter queued = new FastEmitter();
        service.admit(slow);
        service.admit(queued);

        service.broadcastEvent("test", "payload");

        assertFalse(queued.received.get(), "precondition: queued send should not have run yet");
        assertTrue(service.releaseEmitter(queued), "starved consumer must not be dropped");
        release.countDown();
        service.shutdown();
    }
}
