# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## 🔒 SECURITY RESTRICTIONS

**CRITICAL: AI agents are STRICTLY PROHIBITED from:**
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
- `EventService` manages SSE connections with thread-safe `CopyOnWriteArrayList` and a bounded broadcast executor
- Periodic background tasks generate heartbeat and notification events
- Custom events can be triggered via POST `/api/trigger-event`; the body is a JSON string, rejected with `400` if blank or longer than 1000 characters
- `RootRedirectConfig` redirects `/` to `/test.html`
- `CorsConfig` configures CORS for `/api/**` via `app.cors.allowed-origins`

**Connection Management (`SseProperties` / `app.sse.*`):**
- Global connection cap (`app.sse.max-connections`, default `1000`); when full, `/api/events` returns `503 Service Unavailable` with `Retry-After: 5`
- Finite emitter timeout (`app.sse.timeout-ms`, default `300000` / 5 minutes; matched by `spring.mvc.async.request-timeout=300000`)
- Per-emitter send timeout (`app.sse.send-timeout-ms`, default `5000` ms) on a bounded pool (`app.sse.broadcast-threads`, default `4`) so slow consumers are dropped without stalling other clients
- Automatic cleanup on connection completion, timeout, error, or `@PreDestroy` shutdown

## Common Development Commands

**Build and Run:**
```bash
./gradlew build          # Build the application
./gradlew bootRun        # Run the development server (port 8080)
./gradlew test           # Run tests
```

**Docker and Container Commands:**

**IMPORTANT: This project uses Gradle Jib for Docker build and push.**

```bash
# Complete build and push workflow (PREFERRED METHOD)
./gradlew clean                           # Clean old artifacts
./gradlew build                           # Build and test project
DOCKER_USERNAME=jeffbeard ./gradlew jib   # Build Docker image and push to Docker Hub

# Alternative: Build image locally only (no push)
./gradlew jibDockerBuild # Build local Docker image

# Verify spring-boot-devtools never reaches the image (runs in CI on every branch)
./gradlew verifyNoDevtoolsInImage

# Run with Docker (uses jeffbeard Docker Hub repository)
docker run -p 8080:8080 jeffbeard/spring-sse-example:1.3.0
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
- DevTools enabled for hot reload during development (excluded from container images and disabled in `production` profile)
- CORS configured via `app.cors.allowed-origins` (`*` in development; override with explicit origins in production)
- Static HTML test client available at `/test.html` (and `/` redirects to `/test.html`)
- SSE events include: `connected`, `heartbeat` (30s intervals), `notification` (15s intervals), `custom`

## Observability

`OBSERVABILITY.md` describes the metrics path in the Argo CD lab. In short: `deploy/` is built with Kustomize (`deploy/kustomization.yaml`), the app exposes `/actuator/prometheus` there only, `deploy/servicemonitor.yaml` configures the scrape, and the Grafana dashboard lives as JSON in `deploy/dashboards/`. Change the dashboard by editing that file, not in the Grafana UI.

The lab cluster is OrbStack, so Service DNS names open directly in a browser on the Mac (for example `http://monitoring-grafana.monitoring.svc.cluster.local/`); `OBSERVABILITY.md` lists the URLs. `EventService` exports the `sse_*` metrics documented there.

## Supply Chain Pinning

**Dependency updates are reported, never applied automatically.** Dependabot *alerts* are enabled at the repository level and notify by email. Dependabot *version updates* (the `.github/dependabot.yml` that opens upgrade PRs) are deliberately NOT configured — automated upgrade PRs create more work than they save here. Do not add that file back.

Consequence: pins do not refresh themselves. Bump them deliberately, e.g. when an alert or a Trivy finding warrants it.

- **GitHub Actions** are pinned by commit SHA with a trailing `# v4` comment. Refresh with:
  `gh api repos/<owner>/<repo>/git/ref/tags/<tag> -q .object.sha` (dereference annotated tags via `git/tags/<sha>`).
- **Jib base image** is pinned by digest in `build.gradle`; refresh with the command in the comment beside the pin.
- **Image scanning**: the CI `image` job builds a local tarball, verifies devtools is absent, then runs Trivy. It reports HIGH and CRITICAL but fails only on CRITICAL — HIGH findings in a base image are frequently unactionable, and failing on them trains people to ignore the job. This is what catches a stale base image, since nothing bumps it automatically.

## Versioning and Release Conventions

**Semantic Versioning (SemVer):**
- Follow semantic versioning format: `MAJOR.MINOR.PATCH`
- **MAJOR**: Breaking changes that are not backward compatible
- **MINOR**: New features that are backward compatible
- **PATCH**: Bug fixes that are backward compatible
- Examples: `1.0.0`, `1.2.3`, `2.0.0-beta.1`

## Branch and Commit Conventions

**IMPORTANT**: Always follow these conventions when creating branches and commits.
- **NEVER make changes directly on `main`.** Always create and switch to a branch (`feature/<short-name>` or `bugfix/<short-name>`) before editing, adding, or deleting any files.

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
chore: update Spring Boot to 4.2.0
docs: update API documentation
```

## Testing Strategy

**Current Test Suite:**
- `EventControllerSimpleTest`: Direct controller unit testing without mocking
- `EventControllerIntegrationTest`: Standalone `MockMvc` controller tests including 503 capacity handling
- `EventServiceTest`: Core SSE connection management and JSON payload formatting tests
- `EventServiceLimitsTest`: Connection cap and finite emitter timeout tests
- `EventServiceBroadcastTest`: Slow-consumer eviction and non-starvation broadcast tests
- `EventServiceLifecycleTest`: `@PreDestroy` executor shutdown and emitter cleanup tests
- `EventServiceMetricsTest`: Micrometer `sse.*` gauge and counter tests
- `CorsConfigTest`: `MockMvc` CORS preflight allow/reject tests
- `DevToolsProductionProfileTest`: Verifies DevTools is disabled under the `production` profile
- `RootRedirectTest`: Verifies `/` redirects (`302`) to `/test.html`
- `SseIntegrationTest`: Full `@SpringBootTest(webEnvironment = RANDOM_PORT)` end-to-end SSE streaming tests

**Test Coverage:** JaCoCo reports generated with 50% minimum threshold.

## Key Implementation Details

- Uses `CorsConfig` (`app.cors.allowed-origins`) instead of `@CrossOrigin` — restrict origins in production
- SSE connection lifecycle managed with completion/timeout/error callbacks and `@PreDestroy` shutdown
- Background `ScheduledExecutorService` generates periodic events; bounded `ExecutorService` fans out broadcasts with per-send timeouts
- Broadcast event payloads formatted as JSON with `message` and `timestamp` fields via Jackson `ObjectMapper`

## Complete Build Pipeline

### Pipeline Overview
The build pipeline provides automation from code to deployment:

1. **CI/CD Pipeline** (`.github/workflows/ci.yml`):
   - **Test Job**: Runs tests, generates coverage reports, uploads artifacts
   - **Manifests Job**: Runs `scripts/verify-manifests.sh`
   - **Image Job**: Builds local Jib tarball, runs `verifyNoDevtoolsInImage`, and scans with Trivy
   - **Docker Job**: Builds and pushes Docker images to Docker Hub (triggered on `main` and `feature/docker*` branches)

2. **Kubernetes Manifests**:
   - `deploy/`: **Active deployment configuration.** Argo CD on the local OrbStack cluster syncs `deploy/` (built with Kustomize via `deploy/kustomization.yaml`: Deployment, Service, Prometheus `ServiceMonitor`, and Grafana dashboard ConfigMap) into the `spring-sse-example` namespace. Shared platform infrastructure (Argo CD, `kube-prometheus-stack`) lives in `~/projects/k8s-lab-infra`.
   - There is no other manifest set. The EKS overlays that used to live in `k8s/` were never deployed and have been removed; configuration for an AWS target will be built in `deploy/` and the platform project when that target is chosen.

3. **Verification Scripts**:
   - `scripts/verify-manifests.sh`: renders `deploy/` offline and checks the image tag against `build.gradle`, the actuator exposure, and the dashboard JSON.
   - `scripts/verify-references.sh`: checks that scripts and docs point at files that exist.

### Setting Up the Pipeline

**IMPORTANT SECURITY NOTE:**
- AI agents are NEVER allowed to access, read, modify, or inspect repository secrets
- AI agents must NEVER attempt to view GitHub secrets, environment variables containing credentials, or any sensitive configuration
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
- Push to `main` or `feature/docker*` branches triggers Docker build and push
- Uses environment-aware image tagging: semantic version for prod, timestamped/SHA for dev
- Gracefully skips Docker push if credentials not configured

## Container and Kubernetes Deployment (Argo CD Lab)

Local deployment is the Argo CD lab on OrbStack (`deploy/`), not a deploy script. Argo CD syncs `deploy/` from `main` into the `spring-sse-example` namespace. The OrbStack cluster shares the host Docker engine, so a local image build is enough:

```bash
# Build the image the lab manifest references (spring-sse-example:<version>)
./gradlew jibDockerBuild

# Same tag rebuilt? Argo CD sees no git change, so restart the pod by hand
kubectl rollout restart deployment/spring-sse-example -n spring-sse-example

# Render the Argo CD kustomization locally
kubectl kustomize deploy

# Check pod status and logs in the lab namespace
kubectl get pods -n spring-sse-example
kubectl logs -f deployment/spring-sse-example -n spring-sse-example
```
