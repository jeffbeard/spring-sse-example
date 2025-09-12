# Build and Deploy Guide

This document provides step-by-step instructions for building, containerizing, and deploying the Spring SSE Example application.

## Quick Start

### Prerequisites
- Java 17+
- Docker Desktop
- Minikube (for local K8s deployment)
- Docker Hub account (for pushing images)

### 1. Build and Test
```bash
./gradlew test build
```

### 2. Build Docker Image Locally
```bash
./gradlew jibDockerBuild
```

### 3. Build and Push to Docker Hub
```bash
# Set credentials
export DOCKER_USERNAME=<your-dockerhub-username>
export DOCKER_TOKEN=<your-personal-access-token>

# Build and push
./scripts/build-and-push.sh
```

### 4. Deploy to Minikube
```bash
# Start minikube if not running
minikube start

# Deploy application
./scripts/deploy-minikube.sh
```

## Pipeline Components

### GitHub Actions CI/CD

**Triggers:**
- Push to `main` branch
- Push to branches matching `feature/docker*` or `feature/minikube*`
- Pull requests to `main`

**Jobs:**
1. **test**: Runs tests and generates coverage reports
2. **docker**: Builds and pushes Docker images (only on qualifying branches)

**Required Secrets:**
- `DOCKER_USERNAME`: Docker Hub username
- `DOCKER_TOKEN`: Docker Hub Personal Access Token

### Local Scripts

#### `scripts/build-and-push.sh`
- Extracts version from `build.gradle`
- Builds Docker image using Jib
- Pushes to Docker Hub
- Requires `DOCKER_USERNAME` and `DOCKER_TOKEN` environment variables

#### `scripts/deploy-minikube.sh`
- Validates Minikube is running
- Deploys using Kubernetes manifests in `k8s/`
- Provides connection instructions
- Uses environment variable substitution for image tags

### Kubernetes Manifests (`k8s/`)

- **namespace.yaml**: Creates dedicated namespace
- **configmap.yaml**: Application configuration
- **deployment.yaml**: Application deployment with health checks
- **service.yaml**: LoadBalancer service
- **minikube-resources.yaml**: Minikube-specific resource overrides

## Version Management

The pipeline uses semantic versioning from `build.gradle`:
```gradle
version = '1.0.0'
```

This version is automatically used for:
- Docker image tags
- Kubernetes deployment labels
- Build artifacts

## Security Features

### Container Security
- Non-root user (uid 1001)
- Read-only root filesystem
- Dropped capabilities
- Resource limits and requests

### Health Checks
- Liveness probe on `/actuator/health`
- Readiness probe on `/actuator/health/readiness`
- Startup delays and retry logic

## Troubleshooting

### Docker Build Issues
```bash
# Clean and rebuild
./gradlew clean build jibDockerBuild

# Check built image
docker images | grep spring-sse-example
```

### Kubernetes Deployment Issues
```bash
# Check pod status
kubectl get pods -n spring-sse-example

# View logs
kubectl logs -f deployment/spring-sse-app -n spring-sse-example

# Port forward for testing
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-example
```

### CI/CD Issues
- Verify GitHub repository secrets are configured
- Check workflow logs in GitHub Actions tab
- Ensure branch naming matches trigger patterns

## Testing the Deployment

Once deployed, test the SSE endpoints:

```bash
# Get service URL (Minikube)
minikube service spring-sse-service -n spring-sse-example --url

# Test SSE stream
curl -N http://localhost:8080/api/events

# Trigger custom event
curl -X POST -H "Content-Type: application/json" \
     -d '"Hello from pipeline!"' \
     http://localhost:8080/api/trigger-event

# Access web test client
open http://localhost:8080/test.html
```

## Next Steps: EKS Deployment

For production EKS deployment, see `scripts/deploy-eks.sh` and the EKS-specific documentation in `CLAUDE.md`.