# Junie Guidelines for Spring SSE Example

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

## Project Structure

- `src/main/java/com/example/sseexample/` - Main application code
  - `SseExampleApplication.java` - Application entry point
  - `controller/EventController.java` - REST endpoints for SSE
  - `service/EventService.java` - Business logic for SSE connections
- `src/main/resources/` - Configuration and static resources
  - `application.properties` - Application configuration
  - `static/test.html` - Test client for SSE
- `src/test/java/com/example/sseexample/` - Test code
  - `controller/` - Controller tests
  - `integration/` - Integration tests
  - `service/` - Service tests

## Development Guidelines

### Common Development Commands

```bash
./gradlew build          # Build the application
./gradlew bootRun        # Run the development server (port 8080)
./gradlew test           # Run tests
```

### Testing Guidelines

When working with this project, Junie should:
- Run tests to verify changes using `./gradlew test`
- Ensure all tests pass before submitting changes
- Maintain test coverage of at least 50% (JaCoCo threshold)
- Consider the testing strategy outlined in CLAUDE.md

### Branch and Commit Conventions

**Branch Naming:**
- `feature/<short-name>` - New features or enhancements
- `bugfix/<short-name>` - Bug fixes

**Commit Message Format:**
```
<type>: <description>

[optional body]
```

**Allowed Commit Types:**
- `feature:` - New functionality or enhancements
- `bugfix:` - Bug fixes
- `chore:` - Maintenance tasks, dependency updates
- `docs:` - Documentation changes
- `refactor:` - Code improvements without changing functionality
- `test:` - Adding or updating tests
- `perf:` - Performance improvements
- `ci:` - CI/CD pipeline changes

### Code Style Guidelines

- Follow standard Java/Spring Boot conventions
- Use proper exception handling for SSE connections
- Ensure thread-safety in SSE connection management
- Document public APIs with Javadoc
- Use meaningful variable and method names

## Implementation Notes

- Application runs on port 8080 by default
- DevTools enabled for hot reload during development
- CORS configured for all origins (development only)
- Static HTML test client available at `/test.html`
- SSE events include: `connected`, `heartbeat` (30s intervals), `notification` (15s intervals), `custom`
- Uses `@CrossOrigin(origins = "*")` for development - restrict in production
- SSE connection lifecycle managed with completion/timeout/error callbacks
- Background `ScheduledExecutorService` generates periodic events
- Event data formatted as JSON with message and timestamp fields
