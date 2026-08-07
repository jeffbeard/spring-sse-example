# Kubernetes Deployment Guide

This guide covers deploying the Spring Boot SSE application to Kubernetes environments with proper production configurations.

## Overview

The application can be deployed using two main approaches:
1. **Development**: Using port-forwarding for local access  
2. **Production**: Using Ingress controllers for external access

## Architecture

- **Service Type**: ClusterIP (recommended for production with Ingress)
- **Ingress Controller**: nginx (configurable for EKS/AKS/GKE)
- **Security**: Non-root containers, read-only filesystem, resource limits
- **Health Checks**: Liveness and readiness probes
- **Configuration**: ConfigMap for application settings

## Deployment Scripts (Kustomize-based)

### Minikube Development
```bash
./scripts/deploy-minikube-kustomize.sh
```
- Deploys using Kustomize overlays (`k8s/overlays/minikube/`)
- Namespace: `spring-sse-minikube`
- 1 replica with debug logging
- Auto-detects Docker credentials
- Falls back to port-forwarding if Ingress tunnel unavailable

### EKS Development Environment
```bash
ENVIRONMENT=dev ./scripts/deploy-eks-kustomize.sh
```
- Deploys using Kustomize overlays (`k8s/overlays/dev/`)
- Namespace: `spring-sse-dev`
- 2 replicas with ALB ingress
- Supports multiple concurrent versions

### EKS Production Environment
```bash
ENVIRONMENT=prod ./scripts/deploy-eks-kustomize.sh
```
- Deploys using Kustomize overlays (`k8s/overlays/prod/`)
- Namespace: `spring-sse-prod`
- 3 replicas with strict security policies
- Manual deployment recommended

## Access Methods

### 1. Ingress Access (Production)
**Prerequisites**: 
- Ingress controller installed
- For Minikube: `sudo minikube tunnel` running

**URLs**:
- Application: `http://<INGRESS_IP>/api/events`
- Test Page: `http://<INGRESS_IP>/test.html`  
- Health: `http://<INGRESS_IP>/actuator/health`

### 2. Port-Forward Access (Development)
**Commands**:
```bash
# Replace <env> with minikube/dev/prod
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-<env> &
kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-<env> &
```

**URLs**:
- Application: `http://localhost:8080/api/events`
- Test Page: `http://localhost:8080/test.html`
- Health: `http://localhost:8081/actuator/health`

## Production Configuration

### EKS Deployment
For Amazon EKS, the Ingress configurations are environment-specific:

**Development (`k8s/overlays/dev/ingress.yaml`):**
```yaml
annotations:
  alb.ingress.kubernetes.io/scheme: internet-facing
  alb.ingress.kubernetes.io/target-type: ip
  alb.ingress.kubernetes.io/target-group-attributes: stickiness.enabled=true
```

**Production (`k8s/overlays/prod/ingress.yaml`):**
```yaml
annotations:
  alb.ingress.kubernetes.io/scheme: internet-facing
  alb.ingress.kubernetes.io/target-type: ip
  # SSL/TLS configuration (TODO: Add certificate ARN)
  # alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:region:account:certificate/cert-id
```

### Multi-Environment Support
Each environment has specific configurations:
- **Minikube**: nginx ingress with local access
- **Dev**: ALB ingress with sticky sessions for testing multiple versions
- **Production**: ALB ingress with SSL/TLS, custom domains, and WAF integration

## Security Features

- **Container Security**: Non-root user (1001), dropped capabilities
- **Network Policy**: ClusterIP service limits internal access
- **Resource Limits**: Memory (512Mi) and CPU (500m) constraints
- **Health Probes**: Automatic restart on failure
- **CORS**: Configurable origins (restrict in production)

## Monitoring & Troubleshooting

### Status Commands
```bash
# Check deployment status (replace <env> with minikube/dev/prod)
kubectl get pods -n spring-sse-<env>

# View logs
kubectl logs -f deployment/spring-sse-app -n spring-sse-<env>

# Check service endpoints
kubectl get service -n spring-sse-<env> -o wide

# Verify Ingress
kubectl get ingress -n spring-sse-<env> -o wide

# Test Kustomize build
kubectl kustomize k8s/overlays/<env>
```

### Common Issues

1. **Ingress Not Accessible**: 
   - Ensure ingress controller is running
   - For Minikube: Run `sudo minikube tunnel`
   - Check ingress addon: `minikube addons list | grep ingress`

2. **Pod Not Starting**:
   - Check image pull: `kubectl describe pod -n spring-sse-<env>`
   - Verify resource availability: `kubectl top nodes`
   - Check Kustomize build: `kubectl kustomize k8s/overlays/<env>`

3. **SSE Connection Issues**:
   - Verify proxy timeouts in Ingress configuration
   - Check CORS settings in ConfigMap
   - Test direct service access via port-forward

## Testing the Deployment

### Health Check
```bash
curl -s http://<SERVICE_URL>/actuator/health | jq .
```

### SSE Stream Test
```bash
# Start SSE stream
curl -N http://<SERVICE_URL>/api/events

# In another terminal, trigger event
curl -X POST -H "Content-Type: application/json" \
     -d '"Test message"' http://<SERVICE_URL>/api/trigger-event
```

### Load Testing
```bash
# Multiple concurrent SSE connections
for i in {1..10}; do
  curl -N http://<SERVICE_URL>/api/events &
done

# Trigger events
curl -X POST -H "Content-Type: application/json" \
     -d '"Load test event"' http://<SERVICE_URL>/api/trigger-event
```

## Environment Variables

### Build-time
```bash
DOCKER_REGISTRY=docker.io           # Container registry
DOCKER_USERNAME=<your-username>     # Registry username  
IMAGE_TAG=<version>                 # Image version (from build.gradle)
```

### Runtime (ConfigMap)
```yaml
server.port: "8080"
management.server.port: "8081" 
management.endpoints.web.exposure.include: "health,info,metrics"
spring.web.cors.allowed-origins: "*"  # Restrict in production
logging.level.com.example: "INFO"
```

## Next Steps for Production

1. **SSL/TLS**: Configure HTTPS with cert-manager
2. **Monitoring**: Add Prometheus metrics and Grafana dashboards
3. **Scaling**: Configure HPA based on CPU/memory usage
4. **Persistence**: Add persistent volumes if needed
5. **Security**: Implement network policies and pod security standards
6. **GitOps**: Integrate with ArgoCD or Flux for continuous deployment

## Resources

- **Base manifests**: `k8s/base/` (deployment, service, configmap)
- **Environment overlays**: `k8s/overlays/{minikube,dev,prod}/`
- **Deployment scripts**: `scripts/deploy-*-kustomize.sh`
- **Configuration**: Environment-specific patches in overlays
- **Monitoring**: `/actuator/health`, `/actuator/metrics` (port 8081)

## Kustomize Commands

```bash
# Build manifests for specific environment
kubectl kustomize k8s/overlays/minikube
kubectl kustomize k8s/overlays/dev
kubectl kustomize k8s/overlays/prod

# Apply directly with kustomize
kubectl apply -k k8s/overlays/<env>

# Delete environment
kubectl delete -k k8s/overlays/<env>
```