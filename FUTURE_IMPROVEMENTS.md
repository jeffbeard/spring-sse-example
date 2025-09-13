# Future Improvements Task List

This document contains recommendations and questions to address for improving the CI/CD pipeline and deployment architecture.

## High Priority Tasks

### 1. ArgoCD Integration for GitOps
- [ ] Install ArgoCD in Minikube for local testing
- [ ] Set up ArgoCD in EKS clusters (dev and prod)
- [ ] Create ArgoCD Application manifests for each environment
- [ ] Configure automatic sync for dev, manual for prod
- [ ] Set up rollback policies and health checks
- [ ] Document ArgoCD deployment process

### 2. Enhanced CI/CD Pipeline
- [ ] Add integration/e2e tests to pipeline
- [ ] Implement smoke tests after deployment
- [ ] Add deployment gates between environments
- [ ] Set up GitHub environments with approval requirements for prod
- [ ] Add automatic rollback on failed health checks
- [ ] Implement blue-green or canary deployment strategies

### 3. Secrets Management
- [ ] Evaluate options: AWS Secrets Manager, Kubernetes Secrets, Sealed Secrets, or Vault
- [ ] Implement chosen solution for database credentials, API keys
- [ ] Update deployment scripts to handle secrets
- [ ] Document secret rotation process

## Medium Priority Tasks

### 4. Monitoring and Observability
- [ ] Deploy Prometheus and Grafana to clusters
- [ ] Create dashboards for SSE metrics
- [ ] Set up alerting rules for critical issues
- [ ] Implement distributed tracing (Jaeger/Zipkin)
- [ ] Add custom metrics for business KPIs

### 5. Helm Charts (Alternative to Kustomize)
- [ ] Evaluate if Helm provides benefits over Kustomize
- [ ] Create Helm charts if beneficial
- [ ] Set up Helm repository (ChartMuseum or GitHub Pages)
- [ ] Document Helm deployment process

### 6. Container Registry Strategy
- [ ] Evaluate moving from Docker Hub to Amazon ECR
- [ ] Set up ECR repositories with lifecycle policies
- [ ] Update CI/CD to push to ECR
- [ ] Implement vulnerability scanning

## Questions to Address

### Infrastructure & Architecture
1. **Ingress Controller Strategy**
   - Currently using nginx for Minikube and ALB for EKS
   - Question: Should we standardize on one approach?
   - Consider: nginx ingress controller on EKS vs native ALB

2. **Service Type & Load Balancing**
   - Currently using ClusterIP with Ingress
   - Question: Is this the right choice for SSE long-lived connections?
   - Consider: NodePort or LoadBalancer type for simpler setup

3. **Database/Persistence**
   - Application appears stateless currently
   - Question: Will you need database connections?
   - If yes: RDS integration, connection pooling, migrations strategy

### Security & Compliance
4. **CORS Configuration**
   - Currently allows `*` in dev/minikube
   - TODO: Define actual allowed origins for each environment
   - Implement proper CORS headers for production

5. **SSL/TLS Certificates**
   - TODO: Set up ACM certificates for production
   - Configure HTTPS redirect
   - Implement proper TLS termination

6. **Network Policies**
   - TODO: Define network policies for pod-to-pod communication
   - Implement egress rules for external services
   - Set up service mesh (Istio/Linkerd) if needed

### Development Workflow
7. **Branch Strategy & Deployments**
   - Current: feature/bugfix branches trigger Docker builds
   - Question: Should feature branches auto-deploy to dev?
   - Consider: Preview environments for PRs

8. **Multi-Version Support in Dev**
   - Requirement: Multiple versions in dev simultaneously
   - TODO: Implement version-based routing (header/path based)
   - Consider: Separate namespaces per version vs routing rules

### Performance & Scaling
9. **Autoscaling**
   - TODO: Configure HPA (Horizontal Pod Autoscaler)
   - Define scaling metrics (CPU, memory, custom metrics)
   - Test scaling behavior under load

10. **SSE Connection Management**
    - Question: Expected number of concurrent connections?
    - TODO: Configure connection limits and timeouts
    - Implement connection pooling if needed

## Implementation Priority

### Phase 1 (Immediate)
1. ArgoCD setup and testing in Minikube
2. Secrets management solution
3. CORS configuration for production

### Phase 2 (Short-term)
1. Monitoring setup (Prometheus/Grafana)
2. SSL/TLS configuration
3. Enhanced CI/CD with tests

### Phase 3 (Medium-term)
1. Autoscaling configuration
2. Network policies
3. Multi-version routing in dev

### Phase 4 (Long-term)
1. Service mesh evaluation
2. Advanced deployment strategies (blue-green/canary)
3. Full GitOps automation

## Notes

- Each task should be implemented and tested in Minikube first
- Document all changes in KUBERNETES_DEPLOYMENT.md
- Update CLAUDE.md with new commands and workflows
- Consider creating runbooks for common operations

## Success Metrics

- Deployment frequency: Target daily deployments to dev
- Mean time to recovery (MTTR): < 30 minutes
- Deployment success rate: > 95%
- Zero-downtime deployments to production
- Automated rollback success rate: 100%