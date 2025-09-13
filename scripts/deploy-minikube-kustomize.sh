#!/bin/bash
# Deploy Spring Boot SSE application to Minikube using Kustomize
# This script uses the new Kustomize structure for deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}

# Try to get Docker username from current login if not set
if [ -z "$DOCKER_USERNAME" ]; then
    # Check if user is logged into Docker
    if docker info 2>/dev/null | grep -q "Username"; then
        DOCKER_USERNAME=$(docker info 2>/dev/null | grep "Username" | awk '{print $2}')
        echo -e "${GREEN}Using Docker Hub username from current login: $DOCKER_USERNAME${NC}"
    else
        # Try to get from docker config
        if [ -f ~/.docker/config.json ]; then
            DOCKER_USERNAME=$(cat ~/.docker/config.json | grep -A 5 '"auths"' | grep "docker.io\|hub.docker.com" -A 2 | grep "auth" | head -1 | cut -d'"' -f4 | base64 -d | cut -d: -f1 2>/dev/null)
        fi

        if [ -z "$DOCKER_USERNAME" ]; then
            DOCKER_USERNAME="jeffbeard"
            echo -e "${YELLOW}Warning: Could not detect Docker username, using default: $DOCKER_USERNAME${NC}"
            echo -e "${YELLOW}Set DOCKER_USERNAME environment variable to override${NC}"
        else
            echo -e "${GREEN}Detected Docker username from config: $DOCKER_USERNAME${NC}"
        fi
    fi
fi

ENVIRONMENT="minikube"

# Extract version from build.gradle if not provided
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG=$(grep "version = " build.gradle | cut -d "'" -f 2)
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="1.0.0"
        echo -e "${YELLOW}Warning: Could not extract version from build.gradle, using default: $IMAGE_TAG${NC}"
    fi
fi

echo -e "${YELLOW}Deploying Spring Boot SSE application to Minikube using Kustomize...${NC}"
echo "Environment: $ENVIRONMENT"
echo "Image: $DOCKER_REGISTRY/$DOCKER_USERNAME/spring-sse-example:$IMAGE_TAG"

# Check if minikube is running
if ! minikube status > /dev/null 2>&1; then
    echo -e "${RED}Error: Minikube is not running. Please start minikube first with: minikube start${NC}"
    exit 1
fi

# Configure kubectl context
kubectl config use-context minikube

# Export variables for envsubst in kustomize
export DOCKER_REGISTRY
export DOCKER_USERNAME
export IMAGE_TAG

# Build kustomize manifests and apply
echo -e "${YELLOW}Building and applying Kustomize manifests...${NC}"
kubectl kustomize k8s/overlays/$ENVIRONMENT | envsubst | kubectl apply -f -

# Wait for deployment to be ready
echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=300s deployment/spring-sse-app -n spring-sse-$ENVIRONMENT

# Wait for Ingress to get an IP address
echo -e "${YELLOW}Waiting for Ingress to be ready...${NC}"
kubectl wait --for=condition=ready ingress/spring-sse-ingress -n spring-sse-$ENVIRONMENT --timeout=300s || echo "Ingress not ready, will use port-forwarding"

echo -e "${GREEN}✅ Deployment completed successfully!${NC}"

# Check if Ingress is working
INGRESS_IP=$(minikube ip)
if curl -s --connect-timeout 2 "http://$INGRESS_IP/" > /dev/null 2>&1; then
    echo -e "${GREEN}🚀 Application accessible via Ingress:${NC}"
    echo "  Ingress IP: $INGRESS_IP"
    echo "  Application: http://$INGRESS_IP/api/events"
    echo "  Test Page: http://$INGRESS_IP/test.html"
    echo "  Management: http://$INGRESS_IP/actuator/health"
    INGRESS_AVAILABLE=true
else
    echo -e "${YELLOW}⚠️  Ingress not accessible (requires 'sudo minikube tunnel')${NC}"
    echo -e "${YELLOW}Using port-forward for local development access...${NC}"

    # Setup port forwards
    kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-$ENVIRONMENT > /dev/null 2>&1 &
    PF_PID_80=$!
    kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-$ENVIRONMENT > /dev/null 2>&1 &
    PF_PID_81=$!

    sleep 2  # Wait for port forwards to establish

    echo -e "${GREEN}🚀 Application accessible via port-forward:${NC}"
    echo "  Application: http://localhost:8080/api/events"
    echo "  Test Page: http://localhost:8080/test.html"
    echo "  Management: http://localhost:8081/actuator/health"
    INGRESS_AVAILABLE=false
fi
echo ""

# Ask user if they want to open the service automatically
echo -e "${YELLOW}Would you like to open the application in your browser? (y/n)${NC}"
read -r -n 1 response
echo ""

if [[ "$response" =~ ^[Yy]$ ]]; then
    echo -e "${GREEN}🌐 Opening application in browser...${NC}"
    if [ "$INGRESS_AVAILABLE" = true ]; then
        open "http://$INGRESS_IP/test.html" 2>/dev/null || xdg-open "http://$INGRESS_IP/test.html" 2>/dev/null || echo "Please manually open: http://$INGRESS_IP/test.html"
    else
        open "http://localhost:8080/test.html" 2>/dev/null || xdg-open "http://localhost:8080/test.html" 2>/dev/null || echo "Please manually open: http://localhost:8080/test.html"
    fi
fi

echo -e "${YELLOW}Useful commands:${NC}"
echo "kubectl get pods -n spring-sse-$ENVIRONMENT"
echo "kubectl logs -f deployment/spring-sse-app -n spring-sse-$ENVIRONMENT"
echo "kubectl delete -k k8s/overlays/$ENVIRONMENT  # To clean up"