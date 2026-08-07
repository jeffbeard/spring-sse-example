#!/bin/bash
# Deploy Spring Boot SSE application to Minikube (Fully Automated)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}
DOCKER_USERNAME=${DOCKER_USERNAME:-com.example}

# Extract version from build.gradle if not provided
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG=$(grep "version = " build.gradle | cut -d "'" -f 2)
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="1.0.0"
        echo -e "${YELLOW}Warning: Could not extract version from build.gradle, using default: $IMAGE_TAG${NC}"
    fi
fi

echo -e "${YELLOW}Deploying Spring Boot SSE application to Minikube (Automated)...${NC}"

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

# Export variables for envsubst
export DOCKER_REGISTRY
export DOCKER_USERNAME  
export IMAGE_TAG

# Apply base manifests
echo -e "${YELLOW}Applying base manifests...${NC}"
envsubst < k8s/configmap.yaml | kubectl apply -f -
envsubst < k8s/deployment.yaml | kubectl apply -f -
kubectl apply -f k8s/service.yaml

# Apply Ingress configuration
echo -e "${YELLOW}Applying Ingress configuration...${NC}"
kubectl apply -f k8s/ingress.yaml

# Apply minikube-specific overrides
echo -e "${YELLOW}Applying Minikube-specific overrides...${NC}"
kubectl apply -f k8s/minikube-resources.yaml

# Wait for deployment to be ready
echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=300s deployment/spring-sse-app -n spring-sse-example

# Wait for Ingress to be ready
echo -e "${YELLOW}Waiting for Ingress to be ready...${NC}"
kubectl wait --for=condition=ready ingress/spring-sse-ingress -n spring-sse-example --timeout=300s

# Get Ingress IP automatically
INGRESS_IP=$(minikube ip)
echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
echo -e "${GREEN}🚀 Service automatically available via Ingress:${NC}"
echo "  Ingress IP: $INGRESS_IP"
echo "  Application: http://$INGRESS_IP/api/events"
echo "  Test Page: http://$INGRESS_IP/test.html"
echo "  Management: http://$INGRESS_IP/actuator/health"
echo ""

echo -e "${GREEN}✅ Automatic deployment complete!${NC}"
echo -e "${GREEN}The application is now accessible via the URLs above.${NC}"
echo ""
echo -e "${YELLOW}To test the deployment:${NC}"
echo "  curl http://$INGRESS_IP/actuator/health"
echo "  curl -N http://$INGRESS_IP/api/events"
echo "  curl -X POST -H 'Content-Type: application/json' -d '\"Hello from automation!\"' http://$INGRESS_IP/api/trigger-event"
echo ""
echo -e "${YELLOW}To check status:${NC}"
echo "  kubectl get pods -n spring-sse-example"
echo "  kubectl logs -f deployment/spring-sse-app -n spring-sse-example"