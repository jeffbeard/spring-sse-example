#!/bin/bash
# Deploy Spring Boot SSE application to EKS using Kustomize
# Supports both dev and prod environments

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Set defaults
DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}
DOCKER_USERNAME=${DOCKER_USERNAME}
EKS_CLUSTER_NAME=${EKS_CLUSTER_NAME}
ENVIRONMENT=${ENVIRONMENT:-dev}  # Default to dev if not specified

# Validate environment
if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prod" ]]; then
    echo -e "${RED}Error: ENVIRONMENT must be 'dev' or 'prod'. Got: $ENVIRONMENT${NC}"
    exit 1
fi

# Check required environment variables
if [ -z "$DOCKER_USERNAME" ]; then
    echo -e "${RED}Error: DOCKER_USERNAME environment variable is required${NC}"
    exit 1
fi

if [ -z "$EKS_CLUSTER_NAME" ]; then
    echo -e "${RED}Error: EKS_CLUSTER_NAME environment variable is required${NC}"
    exit 1
fi

# Extract version from build.gradle
VERSION=$(grep "version = " build.gradle | cut -d "'" -f 2)
if [ -z "$VERSION" ]; then
    VERSION="1.0.0"
    echo -e "${YELLOW}Warning: Could not extract version from build.gradle, using default: $VERSION${NC}"
fi

# Set image tag based on environment
if [ "$ENVIRONMENT" = "prod" ]; then
    # Production uses clean semantic version
    IMAGE_TAG=${IMAGE_TAG:-$VERSION}
else
    # Dev includes timestamp for uniqueness when deploying multiple versions
    if [ -z "$IMAGE_TAG" ]; then
        TIMESTAMP=$(date +%Y%m%d-%H%M%S)
        IMAGE_TAG="${VERSION}-dev-${TIMESTAMP}"
    fi
fi

echo -e "${YELLOW}Deploying Spring Boot SSE application to EKS...${NC}"
echo "Cluster: $EKS_CLUSTER_NAME"
echo "Environment: $ENVIRONMENT"
echo "Registry: $DOCKER_REGISTRY"
echo "Username: $DOCKER_USERNAME"
echo "Image Tag: $IMAGE_TAG"

# Configure kubectl for EKS
echo -e "${YELLOW}Configuring kubectl for EKS cluster...${NC}"
aws eks update-kubeconfig --name $EKS_CLUSTER_NAME

# Verify connection to cluster
echo -e "${YELLOW}Verifying cluster connection...${NC}"
kubectl cluster-info

# Build kustomize manifests and apply.
# The old pipeline piped through envsubst, which only replaced a ${IMAGE_TAG} label -
# the image itself stayed at whatever the overlay resolved to, so the requested tag was
# never actually deployed (issue #16). render-manifests.sh overrides the image for real.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="$DOCKER_USERNAME/spring-sse-example"
if [ "$DOCKER_REGISTRY" != "docker.io" ]; then
    IMAGE_NAME="$DOCKER_REGISTRY/$IMAGE_NAME"
fi

echo -e "${YELLOW}Building and applying Kustomize manifests for $ENVIRONMENT...${NC}"
echo "Deploying image: ${IMAGE_NAME}:${IMAGE_TAG}"
"$SCRIPT_DIR/render-manifests.sh" "$ENVIRONMENT" "$IMAGE_TAG" "$IMAGE_NAME" | kubectl apply -f -

# Wait for deployment to be ready
echo -e "${YELLOW}Waiting for deployment to be ready...${NC}"
kubectl wait --for=condition=available --timeout=600s deployment/spring-sse-app -n spring-sse-$ENVIRONMENT

# Get ALB URL
echo -e "${YELLOW}Waiting for ALB to be ready...${NC}"
ALB_URL=""
for i in {1..60}; do
    ALB_URL=$(kubectl get ingress spring-sse-ingress -n spring-sse-$ENVIRONMENT -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    if [ ! -z "$ALB_URL" ]; then
        break
    fi
    echo "Waiting for ALB... ($i/60)"
    sleep 5
done

if [ -z "$ALB_URL" ]; then
    echo -e "${RED}Warning: Could not get ALB URL. The Ingress might still be provisioning.${NC}"
    echo "Check status with: kubectl get ingress -n spring-sse-$ENVIRONMENT"
else
    echo -e "${GREEN}✅ Deployment completed successfully!${NC}"
    echo -e "${GREEN}ALB URL: http://${ALB_URL}${NC}"

    # Run health check
    echo -e "${YELLOW}Running health check...${NC}"
    for i in {1..30}; do
        if curl -s -o /dev/null -w "%{http_code}" "http://${ALB_URL}/actuator/health" | grep -q "200"; then
            echo -e "${GREEN}✅ Health check passed!${NC}"
            break
        fi
        echo "Waiting for service to be healthy... ($i/30)"
        sleep 10
    done

    echo -e "${YELLOW}Application URLs:${NC}"
    echo "  API Events: http://${ALB_URL}/api/events"
    echo "  Test Page: http://${ALB_URL}/test.html"
    echo "  Health: http://${ALB_URL}/actuator/health"

    if [ "$ENVIRONMENT" = "dev" ]; then
        echo -e "${YELLOW}To deploy another version to dev:${NC}"
        echo "  1. Update version in build.gradle"
        echo "  2. Build and push new image: ./scripts/build-and-push.sh"
        echo "  3. Deploy with new tag: IMAGE_TAG=<new-tag> ./scripts/deploy-eks-kustomize.sh"
    fi
fi

echo -e "${YELLOW}Useful commands:${NC}"
echo "kubectl get pods -n spring-sse-$ENVIRONMENT"
echo "kubectl logs -f deployment/spring-sse-app -n spring-sse-$ENVIRONMENT"
echo "kubectl get ingress -n spring-sse-$ENVIRONMENT"

if [ "$ENVIRONMENT" = "prod" ]; then
    echo -e "${YELLOW}Production rollback:${NC}"
    echo "kubectl rollout undo deployment/spring-sse-app -n spring-sse-prod"
    echo "kubectl rollout status deployment/spring-sse-app -n spring-sse-prod"
fi

echo -e "${YELLOW}To clean up:${NC}"
echo "kubectl delete -k k8s/overlays/$ENVIRONMENT"