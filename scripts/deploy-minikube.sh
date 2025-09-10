#!/bin/bash
# Deploy Spring Boot SSE application to Minikube

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}
DOCKER_USERNAME=${DOCKER_USERNAME:-com.example}
IMAGE_TAG=${IMAGE_TAG:-0.0.1-SNAPSHOT}

echo -e "${YELLOW}Deploying Spring Boot SSE application to Minikube...${NC}"

# Check if minikube is running
if ! minikube status > /dev/null 2>&1; then
    echo -e "${RED}Error: Minikube is not running. Please start minikube first with: minikube start${NC}"
    exit 1
fi

# Configure kubectl context
kubectl config use-context minikube

# Create namespace
echo -e "${YELLOW}Creating namespace...${NC}"
kubectl apply -f k8s/namespace.yaml

# Apply base manifests
echo -e "${YELLOW}Applying base manifests...${NC}"
envsubst < k8s/configmap.yaml | kubectl apply -f -
envsubst < k8s/deployment.yaml | kubectl apply -f -
kubectl apply -f k8s/service.yaml

# Apply minikube-specific overrides
echo -e "${YELLOW}Applying Minikube-specific overrides...${NC}"
kubectl apply -f k8s/minikube-resources.yaml

# Wait for deployment to be ready
echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=300s deployment/spring-sse-app -n spring-sse-example

# Get service URL
echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
echo -e "${GREEN}Service is available at:${NC}"
minikube service spring-sse-service -n spring-sse-example --url

echo -e "${YELLOW}To access the application:${NC}"
echo "1. Run: minikube service spring-sse-service -n spring-sse-example"
echo "2. Or use port forwarding: kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-example"
echo "3. Test SSE endpoint: curl -N http://localhost:8080/api/events"
echo "4. Access test page: http://localhost:8080/test.html"

echo -e "${YELLOW}To check pod status:${NC}"
echo "kubectl get pods -n spring-sse-example"

echo -e "${YELLOW}To view logs:${NC}"
echo "kubectl logs -f deployment/spring-sse-app -n spring-sse-example"