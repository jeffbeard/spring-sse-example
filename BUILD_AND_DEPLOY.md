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

# Build image in Minikube's Docker (recommended for local dev)
eval $(minikube docker-env)
./gradlew jibDockerBuild

# Deploy application using Kustomize
./scripts/deploy-minikube-kustomize.sh
```

## Pipeline Components

### GitHub Actions CI/CD

**Triggers:**
- Push to `main` branch (creates production tags)
- Push to branches matching `feature/docker*` or `feature/minikube*` (creates dev tags)
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

#### `scripts/deploy-minikube-kustomize.sh`
- Validates Minikube is running
- Deploys using Kustomize overlays in `k8s/overlays/minikube/`
- Auto-detects Docker credentials
- Provides connection instructions and port-forward setup
- Falls back to port-forward if Ingress tunnel unavailable

### Kubernetes Structure (Kustomize-based)

**Base Manifests (`k8s/base/`):**
- **deployment.yaml**: Application deployment with health checks
- **service.yaml**: ClusterIP service for internal access
- **configmap.yaml**: Base application configuration
- **kustomization.yaml**: Base Kustomize configuration

**Environment Overlays (`k8s/overlays/`):**
- **minikube/**: Local development patches (1 replica, debug logging)
- **dev/**: Development environment patches (2 replicas, ALB ingress)
- **prod/**: Production patches (3 replicas, strict security, monitoring)

## Version Management

The pipeline uses semantic versioning from `build.gradle`:
```gradle
version = '1.0.0'
```

Image tagging strategy:
- **Production (main branch)**: Clean semantic version (e.g., `1.0.0`)
- **Development (feature branches)**: Timestamped versions (e.g., `1.0.0-dev-abc1234`)
- **Kubernetes labels**: Environment-specific versioning for tracking deployments

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
# Check pod status (replace <env> with minikube/dev/prod)
kubectl get pods -n spring-sse-<env>

# View logs
kubectl logs -f deployment/spring-sse-app -n spring-sse-<env>

# Port forward for testing
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-<env>
kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-<env>

# Test Kustomize build
kubectl kustomize k8s/overlays/<env>
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

## Multi-Environment Deployment

### EKS Development Environment
```bash
# Build and push image
./scripts/build-and-push.sh

# Deploy to EKS dev
export EKS_CLUSTER_NAME=my-dev-cluster
ENVIRONMENT=dev ./scripts/deploy-eks-kustomize.sh
```

### EKS Production Environment
```bash
# Deploy to EKS production (uses clean semantic versioning)
export EKS_CLUSTER_NAME=my-prod-cluster
ENVIRONMENT=prod ./scripts/deploy-eks-kustomize.sh
```

**Environment Differences:**
- **Minikube**: 1 replica, debug logging, port-forward access
- **Dev**: 2 replicas, ALB ingress, multi-version support
- **Prod**: 3 replicas, strict security, monitoring integration