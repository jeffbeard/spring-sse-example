# SSE Connection Limits and Lifecycle Design

**Date:** 2026-08-10
**Issues:** #15 (DoS: unbounded SSE connections with infinite timeouts), #26 (EventService scheduler never shut down)
**Branch:** `bugfix/sse-connection-limits`

## Problem

`EventService` accepts unlimited SSE connections, each with an infinite timeout, and broadcasts to them synchronously from a single scheduler thread:

- `new SseEmitter(0L)` — connections never time out, so dead or idle clients are reclaimed only when the TCP layer notices.
- `CopyOnWriteArrayList<SseEmitter> emitters` has no cap. An attacker opens connections until the JVM exhausts memory or the container hits its limit.
- `broadcastEvent()` calls `emitter.send()` inline inside `removeIf`. One slow consumer blocks the scheduler thread and stalls delivery to every other client (head-of-line blocking).
- `ScheduledExecutorService` is never shut down. Its non-daemon thread delays JVM shutdown and leaks across test contexts.
- `k8s/base/configmap.yaml` sets `spring.mvc.async.request-timeout: "0"`, reinforcing the infinite timeout in every deployed environment.

## Goals

1. Bound the total number of concurrent SSE connections.
2. Give every emitter a finite timeout so abandoned connections are reclaimed.
3. Stop one slow client from stalling delivery to all others.
4. Shut the schedulers down cleanly on context close.
5. Keep all limits configurable per environment; no recompile to retune.

## Non-Goals

- **Per-IP connection limits.** Behind an ALB or ingress the client address arrives in `X-Forwarded-For`, which is spoofable until trusted-proxy handling is configured. A per-IP cap built on an untrusted header provides weaker protection than it appears to. Track separately if wanted.
- Authentication or authorization on the SSE endpoint (see #13 for actuator auth).
- Backpressure queues per connection. The send-timeout drop is sufficient for this workload.

## Design

### Configuration

New `SseProperties` record bound with `@ConfigurationProperties(prefix = "app.sse")`:

| Property | Default | Purpose |
|---|---|---|
| `app.sse.max-connections` | 1000 | Global ceiling on concurrent emitters |
| `app.sse.timeout-ms` | 300000 | Emitter timeout (5 minutes) |
| `app.sse.send-timeout-ms` | 5000 | Per-emitter budget for a single send |
| `app.sse.broadcast-threads` | 4 | Size of the broadcast executor pool |

The existing `CorsConfig` binds a single value with `@Value`. Four related values justify a typed record instead; the record is also directly constructible in unit tests without a Spring context.

### Connection admission

`createEventStream()` builds `new SseEmitter(props.timeoutMs())` and registers the existing completion, timeout, and error callbacks.

The capacity check and the list insertion happen together under one lock. A bare `if (emitters.size() >= max) ... emitters.add(...)` is a check-then-act race: concurrent requests all observe `size() == max - 1` and all insert, so the cap leaks. Holding a private lock across both statements makes admission atomic. Broadcasts do not take this lock.

Removal goes through a single helper using the return value of `CopyOnWriteArrayList.remove(Object)`, which is `true` for exactly one caller. All three lifecycle callbacks can fire for the same emitter, so relying on that boolean — rather than a separate counter — keeps the accounting correct without extra state.

When admission is refused, the service throws `SseCapacityExceededException`. `EventController` handles it with `@ExceptionHandler`, returning **503 Service Unavailable** with a `Retry-After: 5` header. This keeps the controller method's return type as `SseEmitter` rather than forcing `ResponseEntity<SseEmitter>` on the success path.

### Broadcast

`broadcastEvent()` serializes the payload once, then submits each emitter's send to a bounded executor of daemon threads. Each resulting future is awaited with `get(sendTimeoutMs, MILLISECONDS)`.

- Success: emitter stays.
- Execution failure or `IOException`: `completeWithError`, remove the emitter.
- Timeout: cancel the future, then evict **only if the task had actually started**.

The started-check matters. With a pool of N threads and more than N slow clients, tasks for healthy clients sit in the queue while slow ones hold the threads. `Future.get(timeout)` counts queue time as well as execution time, so a fast client queued behind slow ones would time out and be evicted for someone else's fault. Each broadcast task therefore sets an `AtomicBoolean started` as its first action; on timeout the emitter is dropped only when that flag is set. A queued-but-never-started task is cancelled and its emitter left in place, to be retried on the next broadcast.

Worst-case broadcast wall time is bounded by the send-timeout multiplied by the number of full thread-pool batches occupied by slow clients — not by the total client count, and not by a per-client sum. A stalled consumer is evicted instead of blocking the scheduler indefinitely.

### Shutdown

A `@PreDestroy` method shuts down both executors with `shutdownNow()`, calls `complete()` on every remaining emitter, and clears the list. This addresses the leak in #26 and prevents cross-test contamination.

### Cleanups (#26)

- Remove the stray `"Melba told me a secret"` sample message.
- Replace `Math.random()` with `ThreadLocalRandom.current().nextInt(...)`.

### Configuration files

- `src/main/resources/application.properties`: add the four `app.sse.*` defaults and set `spring.mvc.async.request-timeout=300000` so the MVC async timeout matches the emitter timeout.
- `k8s/base/configmap.yaml`: change `spring.mvc.async.request-timeout` from `"0"` to `"300000"` and add `app.sse.max-connections` and `app.sse.timeout-ms` so environments can retune without a rebuild.

## Testing

Written test-first.

**`EventServiceTest`**
- With `max-connections = 2`, the third `createEventStream()` throws `SseCapacityExceededException`.
- After completing one emitter, a new stream is admitted again.
- `emitter.getTimeout()` equals the configured `timeout-ms`.
- An emitter whose `send` blocks past `send-timeout-ms` is removed, and a fast emitter in the same broadcast still receives the event.
- With `broadcast-threads = 1` and one slow emitter, a fast emitter queued behind it is **not** evicted — its task never started, so the timeout is not its fault.
- After `@PreDestroy`, both executors report terminated.

**`EventControllerIntegrationTest`**
- At capacity, `GET /api/events` returns 503 with a `Retry-After` header.

**Existing tests to update**
- `createEventStream_ShouldReturnValidSseEmitter` and `createEventStream_ShouldReturnEmitterWithZeroTimeout` assert `assertEquals(0L, emitter.getTimeout())`. Both must assert the configured finite timeout; the second is renamed to match its new meaning.
- `periodicEvents_ShouldBeScheduled` constructs a service with periodic events and never disposes it. Add teardown that invokes the shutdown method.

## Risks

- **Client reconnects every 5 minutes.** Browser `EventSource` reconnects automatically, so `test.html` needs no change, but each reconnect re-emits a `connected` event. Acceptable; the alternative is unbounded connection lifetime.
- **Default cap of 1000** is a starting point, not a measured limit. It is configurable per environment precisely so it can be lowered once real memory-per-connection numbers exist.
