#!/bin/bash
# Deploy Spring Boot SSE application to EKS

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}
DOCKER_USERNAME=${DOCKER_USERNAME}
IMAGE_TAG=${IMAGE_TAG:-0.0.1-SNAPSHOT}
EKS_CLUSTER_NAME=${EKS_CLUSTER_NAME}

# Check required environment variables
if [ -z "$DOCKER_USERNAME" ]; then
    echo -e "${RED}Error: DOCKER_USERNAME environment variable is required${NC}"
    exit 1
fi

if [ -z "$DOCKER_TOKEN" ]; then
    echo -e "${RED}Error: DOCKER_TOKEN environment variable is required (Docker Hub Personal Access Token)${NC}"
    exit 1
fi

if [ -z "$EKS_CLUSTER_NAME" ]; then
    echo -e "${RED}Error: EKS_CLUSTER_NAME environment variable is required${NC}"
    exit 1
fi

echo -e "${YELLOW}Deploying Spring Boot SSE application to EKS...${NC}"
echo "Cluster: $EKS_CLUSTER_NAME"
echo "Registry: $DOCKER_REGISTRY"
echo "Username: $DOCKER_USERNAME"
echo "Image Tag: $IMAGE_TAG"

# Configure kubectl for EKS
echo -e "${YELLOW}Configuring kubectl for EKS cluster...${NC}"
aws eks update-kubeconfig --name $EKS_CLUSTER_NAME

# Verify connection to cluster
echo -e "${YELLOW}Verifying cluster connection...${NC}"
kubectl cluster-info

# Create namespace
echo -e "${YELLOW}Creating namespace...${NC}"
kubectl apply -f k8s/namespace.yaml

# Apply manifests
echo -e "${YELLOW}Applying Kubernetes manifests...${NC}"
envsubst < k8s/configmap.yaml | kubectl apply -f -
envsubst < k8s/deployment.yaml | kubectl apply -f -
kubectl apply -f k8s/service.yaml

# Wait for deployment to be ready
echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=600s deployment/spring-sse-app -n spring-sse-example

# Get LoadBalancer URL
echo -e "${YELLOW}Waiting for LoadBalancer to be ready...${NC}"
kubectl wait --for=jsonpath='{.status.loadBalancer.ingress}' --timeout=300s service/spring-sse-service -n spring-sse-example

# Get service details
EXTERNAL_IP=$(kubectl get service spring-sse-service -n spring-sse-example -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
if [ -z "$EXTERNAL_IP" ]; then
    EXTERNAL_IP=$(kubectl get service spring-sse-service -n spring-sse-example -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
fi

echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
echo -e "${GREEN}Service is available at: http://${EXTERNAL_IP}${NC}"

echo -e "${YELLOW}To test the application:${NC}"
echo "1. SSE endpoint: curl -N http://${EXTERNAL_IP}/api/events"
echo "2. Test page: http://${EXTERNAL_IP}/test.html"
echo "3. Trigger event: curl -X POST -H 'Content-Type: application/json' -d '\"Test message\"' http://${EXTERNAL_IP}/api/trigger-event"

echo -e "${YELLOW}To check pod status:${NC}"
echo "kubectl get pods -n spring-sse-example"

echo -e "${YELLOW}To view logs:${NC}"
echo "kubectl logs -f deployment/spring-sse-app -n spring-sse-example"

echo -e "${YELLOW}To clean up:${NC}"
echo "kubectl delete namespace spring-sse-example"