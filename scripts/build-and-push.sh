#!/bin/bash
# Build and push Docker image to Docker Hub

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check required environment variables
if [ -z "$DOCKER_USERNAME" ]; then
    echo -e "${RED}Error: DOCKER_USERNAME environment variable is required${NC}"
    exit 1
fi

if [ -z "$DOCKER_TOKEN" ]; then
    echo -e "${RED}Error: DOCKER_TOKEN environment variable is required (Docker Hub Personal Access Token)${NC}"
    exit 1
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

# Login to Docker registry using personal access token
echo -e "${YELLOW}Logging in to Docker registry...${NC}"
echo $DOCKER_TOKEN | docker login $DOCKER_REGISTRY -u $DOCKER_USERNAME --password-stdin

# Build and push using Jib
echo -e "${YELLOW}Building and pushing image with Jib...${NC}"
./gradlew jib

echo -e "${GREEN}✅ Successfully built and pushed image!${NC}"
echo -e "${GREEN}Image: ${DOCKER_REGISTRY}/${DOCKER_USERNAME}/spring-sse-example:${IMAGE_TAG}${NC}"