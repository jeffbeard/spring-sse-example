# Spring Boot SSE Example

A demonstration of Server-Sent Events (SSE) implementation using Spring Boot, showcasing real-time event streaming to web clients.

## Features

- **Real-time Event Streaming**: Server-sent events via HTTP SSE protocol
- **Multiple Event Types**: Heartbeat, notifications, and custom events
- **Concurrent Connections**: Thread-safe handling of multiple SSE clients
- **Interactive Web Client**: HTML test interface for SSE demonstration
- **Hot Reload**: Spring Boot DevTools for development efficiency

## Quick Start

### Prerequisites

- Java 17 or higher
- No additional installation required (uses Gradle wrapper)

### Running the Application

1. Clone and navigate to the project directory
2. Start the application:
   ```bash
   ./gradlew bootRun
   ```
3. Open your browser to `http://localhost:8080/test.html`

## API Endpoints

### SSE Stream
```
GET /api/events
Content-Type: text/event-stream
```
Establishes an SSE connection and streams real-time events.

### Trigger Custom Event
```
POST /api/trigger-event
Content-Type: application/json
Body: "Your custom message"
```
Broadcasts a custom event to all connected SSE clients.

### Health Check
```
GET /api/
```
Returns server status message.

## Event Types

- **connected**: Sent when a client first connects
- **heartbeat**: Periodic server heartbeat (every 30 seconds)
- **notification**: Sample notifications (every 15 seconds)
- **custom**: User-triggered events via POST endpoint

## Testing

### Command Line Testing
```bash
# Stream SSE events (press Ctrl+C to stop)
curl -N http://localhost:8080/api/events

# Send custom event
curl -X POST -H "Content-Type: application/json" \
     -d '"Hello from curl!"' \
     http://localhost:8080/api/trigger-event
```

### Web Interface Testing
Visit `http://localhost:8080/test.html` for an interactive web interface that demonstrates:
- Connecting/disconnecting from SSE stream
- Real-time event display
- Sending custom events

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web Client    │────│ EventController │────│  EventService   │
│                 │    │                 │    │                 │
│ EventSource API │◄───│ SseEmitter      │◄───│ Periodic Tasks  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

- **EventController**: REST endpoints for SSE and event triggering
- **EventService**: Connection management and event broadcasting
- **SseEmitter**: Spring's SSE implementation for streaming events

## Development

### Build Commands
```bash
./gradlew build          # Build the application
./gradlew test           # Run tests
./gradlew bootRun        # Run development server
```

### Configuration
- **Port**: 8080 (configurable in `application.properties`)
- **CORS**: Enabled for all origins (development mode)
- **DevTools**: Automatic restart and live reload enabled

## Use Cases

This example demonstrates SSE patterns suitable for:
- Real-time notifications
- Live dashboards
- Chat applications
- System monitoring displays
- Live data feeds

## License

This project is provided as an educational example.