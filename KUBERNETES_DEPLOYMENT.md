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

## Deployment Scripts

### Interactive Deployment (Minikube/Development)
```bash
./scripts/deploy-minikube.sh
```
- Deploys to Minikube with automatic environment detection
- Falls back to port-forwarding if Ingress tunnel unavailable
- Provides interactive browser opening
- Suitable for local development

### Automated Deployment (CI/CD)
```bash
./scripts/deploy-minikube-auto.sh
```
- Fully automated deployment for CI/CD pipelines
- No user interaction required
- Provides service URLs for testing
- Includes automated health checks

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
kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-example &
kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-example &
```

**URLs**:
- Application: `http://localhost:8080/api/events`
- Test Page: `http://localhost:8080/test.html`
- Health: `http://localhost:8081/actuator/health`

## Production Configuration

### EKS Deployment
For Amazon EKS, the Ingress configuration includes both nginx and ALB controller annotations:

```yaml
# k8s/ingress.yaml includes:
annotations:
  # AWS Load Balancer Controller (for EKS)
  alb.ingress.kubernetes.io/scheme: internet-facing
  alb.ingress.kubernetes.io/target-type: ip
  # nginx Ingress Controller (for other environments)  
  nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
  nginx.ingress.kubernetes.io/proxy-buffering: "off"
```

### Multi-Environment Support
The Ingress configuration supports both:
- **Default routing** (works with direct IP access)
- **Host-based routing** (for production domains)

## Security Features

- **Container Security**: Non-root user (1001), dropped capabilities
- **Network Policy**: ClusterIP service limits internal access
- **Resource Limits**: Memory (512Mi) and CPU (500m) constraints
- **Health Probes**: Automatic restart on failure
- **CORS**: Configurable origins (restrict in production)

## Monitoring & Troubleshooting

### Status Commands
```bash
# Check deployment status
kubectl get pods -n spring-sse-example

# View logs
kubectl logs -f deployment/spring-sse-app -n spring-sse-example

# Check service endpoints
kubectl get service -n spring-sse-example -o wide

# Verify Ingress
kubectl get ingress -n spring-sse-example -o wide
```

### Common Issues

1. **Ingress Not Accessible**: 
   - Ensure ingress controller is running
   - For Minikube: Run `sudo minikube tunnel`
   - Check ingress addon: `minikube addons list | grep ingress`

2. **Pod Not Starting**:
   - Check image pull: `kubectl describe pod -n spring-sse-example`
   - Verify resource availability: `kubectl top nodes`

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

- Kubernetes manifests: `k8s/`
- Deployment scripts: `scripts/`
- Configuration: `k8s/configmap.yaml`
- Monitoring: `/actuator/health`, `/actuator/metrics`