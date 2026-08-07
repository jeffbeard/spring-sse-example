# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🔒 SECURITY RESTRICTIONS

**CRITICAL: Claude Code is STRICTLY PROHIBITED from:**
- Accessing, reading, viewing, or inspecting ANY repository secrets
- Using commands like `gh secret list`, `gh secret view`, or similar secret-related operations
- Attempting to read environment variables containing credentials or sensitive information
- Viewing or modifying any configuration that might contain secrets or sensitive data
- Any operations that could expose credentials, tokens, passwords, or other sensitive information

All secret management, credential configuration, and sensitive data handling must be performed manually by authorized users only.

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

**Docker and Container Commands:**

**IMPORTANT: This project uses Gradle Jib for Docker build and push - NOT the build-and-push.sh script.**

```bash
# Complete build and push workflow (PREFERRED METHOD)
./gradlew clean                           # Clean old artifacts
./gradlew build                           # Build and test project
DOCKER_USERNAME=jeffbeard ./gradlew jib   # Build Docker image and push to Docker Hub

# Alternative: Build image locally only (no push)
./gradlew jibDockerBuild # Build local Docker image

# Run with Docker (uses jeffbeard Docker Hub repository)
docker run -p 8080:8080 jeffbeard/spring-sse-example:1.0.1

# Legacy script (NOT USED in this project)
# ./scripts/build-and-push.sh
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

## Versioning and Release Conventions

**Semantic Versioning (SemVer):**
- Follow semantic versioning format: `MAJOR.MINOR.PATCH`
- **MAJOR**: Breaking changes that are not backward compatible
- **MINOR**: New features that are backward compatible
- **PATCH**: Bug fixes that are backward compatible
- Examples: `1.0.0`, `1.2.3`, `2.0.0-beta.1`

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

## Complete Build Pipeline

### Pipeline Overview
The build pipeline provides complete automation from code to deployment:

1. **CI/CD Pipeline** (`.github/workflows/ci.yml`):
   - **Test Job**: Runs tests, generates coverage reports
   - **Docker Job**: Builds and pushes Docker images to Docker Hub (triggered on main and feature branches)
   
2. **Local Scripts**:
   - `scripts/deploy-minikube.sh`: Deploy to local Minikube cluster

3. **Kubernetes Manifests** (`k8s/`):
   - Production-ready manifests with health checks, resource limits, security contexts

### Setting Up the Pipeline

**IMPORTANT SECURITY NOTE:**
- Claude Code is NEVER allowed to access, read, modify, or inspect repository secrets
- Claude Code must NEVER attempt to view GitHub secrets, environment variables containing credentials, or any sensitive configuration
- All secret management must be handled manually by authorized users only

**1. Configure Docker Hub Secrets (Manual - User Only):**
Repository owners must manually add these GitHub repository secrets:
- `DOCKER_USERNAME`: Your Docker Hub username
- `DOCKER_TOKEN`: Your Docker Hub Personal Access Token

**2. Local Development Setup:**
```bash
# Set environment variables for local builds (user configuration only)
export DOCKER_USERNAME=<your-dockerhub-username>
export DOCKER_TOKEN=<your-personal-access-token>
```

### Pipeline Workflows

**Automated CI/CD (GitHub Actions):**
- Push to `main` or `feature/docker*` or `feature/minikube*` branches triggers Docker build and push
- Uses environment-aware image tagging: semantic version for prod, timestamped for dev
- Gracefully skips Docker push if credentials not configured

**Manual Local Build and Deploy:**
```bash
# Build and push to Docker Hub (PREFERRED METHOD - uses Gradle Jib)
./gradlew clean
./gradlew build
DOCKER_USERNAME=jeffbeard ./gradlew jib

# Deploy to Minikube (Kustomize-based)
./scripts/deploy-minikube-kustomize.sh

# Deploy to EKS dev environment
ENVIRONMENT=dev ./scripts/deploy-eks-kustomize.sh

# Deploy to EKS prod environment
ENVIRONMENT=prod ./scripts/deploy-eks-kustomize.sh
```

## Container and Kubernetes Deployment

**Environment Variables for Docker/Kubernetes:**
```bash
DOCKER_REGISTRY=docker.io           # Docker registry (default: docker.io)
DOCKER_USERNAME=<your-username>     # Docker Hub username (auto-detected if logged in)
DOCKER_TOKEN=<your-access-token>    # Docker Hub Personal Access Token (optional if logged in)
ENVIRONMENT=dev|prod                # Target environment for EKS deployments
EKS_CLUSTER_NAME=<cluster-name>     # EKS cluster name for deployments
```

**Minikube Deployment:**
```bash
# Start minikube
minikube start

# Build image directly in minikube Docker (no push needed)
eval $(minikube docker-env)
./gradlew jibDockerBuild

# Deploy to minikube using Kustomize
./scripts/deploy-minikube-kustomize.sh

# Access via port-forward
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-minikube
```

**EKS Deployment:**
```bash
# Configure AWS CLI and create EKS cluster
aws eks create-cluster --name my-cluster --version 1.28 --role-arn <cluster-role-arn>

# Build and push image (uses Gradle Jib with existing Docker login)
./gradlew clean && ./gradlew build && DOCKER_USERNAME=jeffbeard ./gradlew jib

# Deploy to EKS dev environment
export EKS_CLUSTER_NAME=my-cluster
ENVIRONMENT=dev ./scripts/deploy-eks-kustomize.sh

# Deploy to EKS prod environment (manual approval recommended)
ENVIRONMENT=prod ./scripts/deploy-eks-kustomize.sh
```

**Kubernetes Resources (Kustomize-based):**
- **Namespaces**: Environment-specific (`spring-sse-minikube`, `spring-sse-dev`, `spring-sse-prod`)
- **Base Manifests**: `k8s/base/` (deployment, service, configmap)
- **Environment Overlays**: `k8s/overlays/{minikube,dev,prod}/` with specific patches
- **Deployment**: 1 replica (Minikube), 2 replicas (dev), 3 replicas (prod)
- **Service**: ClusterIP with Ingress for external access
- **ConfigMap**: Environment-specific configuration patches
- **Resource Limits**: Optimized per environment (256Mi/250m for Minikube, 512Mi/500m for dev, 1Gi/1000m for prod)
- **Health Checks**: Liveness and readiness probes on management port 8081
- **Security**: Non-root user (1001), read-only filesystem, dropped capabilities

**Monitoring and Troubleshooting:**
```bash
# Check pod status (replace <env> with minikube/dev/prod)
kubectl get pods -n spring-sse-<env>

# View logs
kubectl logs -f deployment/spring-sse-app -n spring-sse-<env>

# Port forward for local access
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-<env>
kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-<env>

# Check health endpoint
curl http://localhost:8081/actuator/health

# Deploy using Kustomize directly
kubectl kustomize k8s/overlays/<env> | kubectl apply -f -
```