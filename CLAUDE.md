# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application demonstrating Server-Sent Events (SSE) implementation using Spring Web and Spring Boot DevTools. The application streams real-time events to connected clients via HTTP SSE protocol.

## Core Architecture

**SSE Event Flow:**
- `EventController` exposes `/api/events` SSE endpoint using `SseEmitter`
- `EventService` manages SSE connections with thread-safe `CopyOnWriteArrayList`
- Periodic background tasks generate heartbeat and notification events
- Custom events can be triggered via POST `/api/trigger-event`

**Connection Management:**
- Each SSE connection gets an `SseEmitter` with infinite timeout (0L)
- Automatic cleanup on connection completion, timeout, or error
- Concurrent connection support with thread-safe operations

## Common Development Commands

**Build and Run:**
```bash
./gradlew build          # Build the application
./gradlew bootRun        # Run the development server (port 8080)
./gradlew test           # Run tests
```

**Testing SSE Endpoints:**
```bash
# Test SSE stream (will show real-time events)
curl -N http://localhost:8080/api/events

# Trigger custom event
curl -X POST -H "Content-Type: application/json" \
     -d '"Your custom message"' \
     http://localhost:8080/api/trigger-event

# Access web test client
open http://localhost:8080/test.html
```

## Development Notes

- Application runs on port 8080 by default
- DevTools enabled for hot reload during development
- CORS configured for all origins (development only)
- Static HTML test client available at `/test.html`
- SSE events include: `connected`, `heartbeat` (30s intervals), `notification` (15s intervals), `custom`

## Branch and Commit Conventions

**IMPORTANT**: Always follow these conventions when creating branches and commits.

**Branch Naming:**
- `feature/<short-name>` - New features or enhancements
- `bugfix/<short-name>` - Bug fixes

**Commit Message Format:**
```
<type>: <description>

[optional body]
```

**Allowed Commit Types (use exactly these prefixes):**
- `feature:` - New functionality or enhancements
- `bugfix:` - Bug fixes
- `chore:` - Maintenance tasks, dependency updates
- `docs:` - Documentation changes
- `refactor:` - Code improvements without changing functionality
- `test:` - Adding or updating tests
- `perf:` - Performance improvements
- `ci:` - CI/CD pipeline changes

**Examples:**
```
feature: add SSE authentication middleware
bugfix: fix memory leak in connection cleanup
chore: update Spring Boot to 3.3.6
docs: update API documentation
```

## Testing Strategy

**Current Test Suite (100% passing):**
- `SimpleTest`: Basic functionality smoke tests
- `EventControllerSimpleTest`: Direct controller testing without mocking
- `EventServiceTest`: SSE connection management with disabled periodic events

**Test Coverage:** JaCoCo reports generated with 50% minimum threshold.

## Future Test Improvements (TODO)

**When Mockito/Spring compatibility improves, consider adding:**

1. **Enhanced Integration Tests:**
   ```java
   @SpringBootTest(webEnvironment = RANDOM_PORT)
   @TestPropertySource(properties = "spring.profiles.active=test")
   // Full application context tests with TestRestTemplate
   ```

2. **MockMvc API Tests:**
   ```java
   @WebMvcTest(EventController.class)
   // Focused controller layer testing with MockMvc
   ```

3. **SSE Streaming Tests:**
   ```java
   // Real HTTP client tests for SSE endpoint streaming
   // Test connection lifecycle, event parsing, reconnection
   ```

4. **Concurrent Connection Tests:**
   ```java
   // Test multiple simultaneous SSE connections
   // Verify thread-safety under load
   ```

## Key Implementation Details

- Uses `@CrossOrigin(origins = "*")` for development - restrict in production
- SSE connection lifecycle managed with completion/timeout/error callbacks  
- Background `ScheduledExecutorService` generates periodic events
- Event data formatted as JSON with message and timestamp fields