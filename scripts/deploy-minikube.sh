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

# Extract version from build.gradle if not provided
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG=$(grep "version = " build.gradle | cut -d "'" -f 2)
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="1.0.0"
        echo -e "${YELLOW}Warning: Could not extract version from build.gradle, using default: $IMAGE_TAG${NC}"
    fi
fi

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

# Wait for Ingress to get an IP address
echo -e "${YELLOW}Waiting for Ingress to be ready...${NC}"
kubectl wait --for=condition=ready ingress/spring-sse-ingress -n spring-sse-example --timeout=300s || echo "Ingress not ready, will use port-forwarding"

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
    kubectl port-forward service/spring-sse-service 8080:80 -n spring-sse-example > /dev/null 2>&1 &
    PF_PID_80=$!
    kubectl port-forward service/spring-sse-service 8081:8081 -n spring-sse-example > /dev/null 2>&1 &  
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
        echo -e "${GREEN}Browser opened! You can now:${NC}"
        echo "  • Visit the test page: http://$INGRESS_IP/test.html"
        echo "  • Test SSE stream: curl -N http://$INGRESS_IP/api/events"
        echo "  • Trigger events: curl -X POST -H 'Content-Type: application/json' -d '\"Hello\"' http://$INGRESS_IP/api/trigger-event"
    else
        open "http://localhost:8080/test.html" 2>/dev/null || xdg-open "http://localhost:8080/test.html" 2>/dev/null || echo "Please manually open: http://localhost:8080/test.html"
        echo -e "${GREEN}Browser opened! You can now:${NC}"
        echo "  • Visit the test page: http://localhost:8080/test.html"
        echo "  • Test SSE stream: curl -N http://localhost:8080/api/events"
        echo "  • Trigger events: curl -X POST -H 'Content-Type: application/json' -d '\"Hello\"' http://localhost:8080/api/trigger-event"
    fi
else
    echo -e "${YELLOW}Manual access instructions:${NC}"
    if [ "$INGRESS_AVAILABLE" = true ]; then
        echo "  • Open browser to: http://$INGRESS_IP/test.html"
        echo "  • Test SSE: curl -N http://$INGRESS_IP/api/events"
        echo "  • Check health: http://$INGRESS_IP/actuator/health"
        echo "  • For production: Enable 'sudo minikube tunnel' for Ingress access"
    else
        echo "  • Open browser to: http://localhost:8080/test.html"
        echo "  • Test SSE: curl -N http://localhost:8080/api/events"
        echo "  • Check health: http://localhost:8081/actuator/health"
        echo "  • For Ingress access: Run 'sudo minikube tunnel' in another terminal"
    fi
fi

echo -e "${YELLOW}To check pod status:${NC}"
echo "kubectl get pods -n spring-sse-example"

echo -e "${YELLOW}To view logs:${NC}"
echo "kubectl logs -f deployment/spring-sse-app -n spring-sse-example"