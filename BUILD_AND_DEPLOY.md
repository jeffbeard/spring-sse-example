# Build and Deploy Guide

This document provides step-by-step instructions for building, containerizing, and deploying the Spring SSE Example application.

## Quick Start

### Prerequisites
- Java 17+
- Docker Desktop
- A local Kubernetes cluster with Argo CD (for local deployment; the lab uses OrbStack)
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

# Build and push (Gradle Jib; there is no build-and-push script)
./gradlew clean build
DOCKER_USERNAME=<your-dockerhub-username> ./gradlew jib
```

### 4. Deploy Locally (Argo CD lab)

Argo CD on the local cluster syncs `deploy/` from `main`, so merging to `main` is the deploy step. The image is built into local Docker and never pushed:

```bash
./gradlew jibDockerBuild
```

See `OBSERVABILITY.md` for the URLs, metrics and dashboard.

#### What this repository provides, and what it expects

Everything specific to this application is in `deploy/` and is applied by Argo CD:
the Deployment and Service, the ServiceMonitor that configures scraping, and the
Grafana dashboard.

The cluster and its shared services are not managed here. This repository assumes
they already exist:

- a Kubernetes cluster (the lab uses OrbStack, which shares the host Docker engine)
- Argo CD (the lab runs v3.6.2, installed from the upstream manifest)
- the `kube-prometheus-stack` Helm chart (the lab runs 93.7.0, release `monitoring`
  in namespace `monitoring`), which supplies Prometheus, the Prometheus Operator
  CRDs that `ServiceMonitor` needs, and Grafana with the dashboard sidecar
- one Argo CD `Application` pointing at this repository

Two settings of that chart matter to this repository:

- `prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues: false`, so
  Prometheus picks up `deploy/servicemonitor.yaml` without a `release` label
- the Grafana dashboard sidecar watching all namespaces for the label
  `grafana_dashboard: "1"`, which is the chart default and what the dashboard
  ConfigMap relies on

That `Application` is the only link between the platform and this repository. It
is applied once to the cluster and is kept with the lab setup, not here. For
reference, it looks like this:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: spring-sse-example
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/jeffbeard/spring-sse-example.git
    targetRevision: main
    path: deploy
  destination:
    server: https://kubernetes.default.svc
    namespace: spring-sse-example
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

Building the cluster and installing those shared services belongs in a separate
platform project; see `TODO.md`.

## Pipeline Components

### GitHub Actions CI/CD

**Triggers:**
- Push to `main` branch (creates production tags)
- Push to branches matching `feature/docker*` (creates dev tags)
- Pull requests to `main`

**Jobs:**
1. **test**: Runs tests and generates coverage reports
2. **manifests**: Runs `scripts/verify-manifests.sh` against `deploy/`
3. **image**: Builds the image as a tarball, checks devtools is absent, scans with Trivy
4. **docker**: Builds and pushes Docker images (only on qualifying branches)

**Required Secrets:**
- `DOCKER_USERNAME`: Docker Hub username
- `DOCKER_TOKEN`: Docker Hub Personal Access Token

### Local Scripts

#### `./gradlew jib`
- Reads the version from `build.gradle`
- Builds the image with Jib and pushes it to Docker Hub
- Requires `DOCKER_USERNAME`, and `DOCKER_TOKEN` unless already logged in
- Base image is digest-pinned; `./gradlew verifyNoDevtoolsInImage` checks the result

### Kubernetes Manifests

`deploy/` is the only manifest set, built with Kustomize:

- **app.yaml**: Deployment (with startup, liveness and readiness probes) and Service
- **servicemonitor.yaml**: Prometheus scrape configuration
- **dashboards/**: Grafana dashboard JSON, packaged as a ConfigMap
- **kustomization.yaml**: ties them together

## Version Management

The pipeline uses semantic versioning from `build.gradle`:
```gradle
version = '1.0.0'
```

Image tagging strategy:
- **Production (main branch)**: Clean semantic version (e.g., `1.0.0`)
- **Development (feature branches)**: Timestamped versions (e.g., `1.0.0-dev-abc1234`)

## Security Features

### Container Security
- Non-root user (uid 1001), set in the image by Jib
- Digest-pinned base image, scanned with Trivy in CI
- Pod security context and resource limits are not set yet (issue #63)

### Health Checks
- Startup and liveness probes on `/actuator/health/liveness`
- Readiness probe on `/actuator/health/readiness`

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
kubectl logs -f deployment/spring-sse-example -n spring-sse-example

# Port forward for testing (actuator shares the application port)
kubectl port-forward -n spring-sse-example svc/spring-sse-example 8080:80

# Test the Kustomize build and the checks CI runs
kubectl kustomize deploy
./scripts/verify-manifests.sh
```

### CI/CD Issues
- Verify GitHub repository secrets are configured
- Check workflow logs in GitHub Actions tab
- Ensure branch naming matches trigger patterns

## Testing the Deployment

Once deployed, test the SSE endpoints:

```bash
# Local lab: port-forward the Service (or use the direct URLs in OBSERVABILITY.md)
kubectl port-forward -n spring-sse-example svc/spring-sse-example 8080:80

# Test SSE stream
curl -N http://localhost:8080/api/events

# Trigger custom event
curl -X POST -H "Content-Type: application/json" \
     -d '"Hello from pipeline!"' \
     http://localhost:8080/api/trigger-event

# Access web test client
open http://localhost:8080/test.html
```

## Other Environments

The local lab is the only environment. Earlier EKS overlays were removed because
they had never been deployed; configuration for an AWS target will be added to
`deploy/` when that target is chosen (see `TODO.md`).
