#!/bin/bash
# Build and push Docker image to Docker Hub

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check Docker authentication
if [ -z "$DOCKER_USERNAME" ]; then
    # Try to get from current Docker login
    if docker info 2>/dev/null | grep -q "Username"; then
        DOCKER_USERNAME=$(docker info 2>/dev/null | grep "Username" | awk '{print $2}')
        echo -e "${GREEN}Using Docker Hub username from current login: $DOCKER_USERNAME${NC}"
    else
        echo -e "${RED}Error: Not logged into Docker Hub and DOCKER_USERNAME not set${NC}"
        echo -e "${YELLOW}Please either:${NC}"
        echo "  1. Run: docker login"
        echo "  2. Set: export DOCKER_USERNAME=<your-username>"
        exit 1
    fi
fi

# Check if we can push (either logged in or have token)
if [ -z "$DOCKER_TOKEN" ]; then
    # Check if already logged in
    if ! docker info 2>/dev/null | grep -q "Username"; then
        echo -e "${RED}Error: Not logged into Docker Hub${NC}"
        echo -e "${YELLOW}Please either:${NC}"
        echo "  1. Run: docker login"
        echo "  2. Set: export DOCKER_TOKEN=<your-personal-access-token>"
        exit 1
    fi
    echo -e "${GREEN}Using existing Docker Hub login${NC}"
fi

DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}

# Extract version from build.gradle if not provided
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG=$(grep "version = " build.gradle | cut -d "'" -f 2)
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="1.0.0"
        echo -e "${YELLOW}Warning: Could not extract version from build.gradle, using default: $IMAGE_TAG${NC}"
    fi
fi

echo -e "${YELLOW}Building and pushing Spring Boot SSE application...${NC}"
echo "Registry: $DOCKER_REGISTRY"
echo "Username: $DOCKER_USERNAME"
echo "Image Tag: $IMAGE_TAG"

# Login to Docker registry if token provided
if [ ! -z "$DOCKER_TOKEN" ]; then
    echo -e "${YELLOW}Logging in to Docker registry...${NC}"
    echo $DOCKER_TOKEN | docker login $DOCKER_REGISTRY -u $DOCKER_USERNAME --password-stdin
fi

# Build and push using Jib
echo -e "${YELLOW}Building and pushing image with Jib...${NC}"
./gradlew jib

echo -e "${GREEN}✅ Successfully built and pushed image!${NC}"
echo -e "${GREEN}Image: ${DOCKER_REGISTRY}/${DOCKER_USERNAME}/spring-sse-example:${IMAGE_TAG}${NC}"