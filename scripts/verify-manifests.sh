#!/bin/bash
# Renders every Kustomize overlay and fails on the defects behind issue #16:
#   - images resolving to :latest (non-reproducible deploys, broken rollback)
#   - image tags that do not match the project version in build.gradle
#   - unsubstituted ${...} placeholders, which are invalid Kubernetes field values
#
# Runs entirely offline - `kubectl kustomize` needs no cluster.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if ! command -v kubectl >/dev/null 2>&1; then
    echo -e "${RED}kubectl is required (it provides the embedded kustomize renderer)${NC}"
    exit 1
fi

VERSION=$(grep "^version = " build.gradle | cut -d "'" -f 2)
if [ -z "$VERSION" ]; then
    echo -e "${RED}Could not read version from build.gradle${NC}"
    exit 1
fi
echo -e "${YELLOW}Project version: ${VERSION}${NC}"

FAILURES=0

for overlay in k8s/overlays/*/; do
    env_name=$(basename "$overlay")
    rendered=$(kubectl kustomize "$overlay")

    # Unsubstituted placeholders reach the API server as invalid values.
    if placeholders=$(echo "$rendered" | grep -n '\${' || true); [ -n "$placeholders" ]; then
        echo -e "${RED}[$env_name] unsubstituted placeholder(s):${NC}"
        echo "$placeholders" | sed 's/^/    /'
        FAILURES=$((FAILURES + 1))
    fi

    images=$(echo "$rendered" | grep -E "^\s+image:" | awk '{print $2}' | sort -u)
    if [ -z "$images" ]; then
        echo -e "${RED}[$env_name] no image found in rendered output${NC}"
        FAILURES=$((FAILURES + 1))
        continue
    fi

    while IFS= read -r image; do
        tag="${image##*:}"
        if [ "$tag" = "latest" ]; then
            echo -e "${RED}[$env_name] image pinned to :latest -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        elif [ "$image" = "$tag" ]; then
            echo -e "${RED}[$env_name] image has no tag -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        elif [ "$tag" != "$VERSION" ]; then
            echo -e "${RED}[$env_name] image tag ${tag} does not match project version ${VERSION} -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        else
            echo -e "${GREEN}[$env_name] ${image}${NC}"
        fi
    done <<< "$images"

    # Version labels must track the release, not drift from it (issue #25).
    labels=$(echo "$rendered" | grep -E "app\.kubernetes\.io/version:" | awk '{print $2}' | tr -d '"' | sort -u)
    if [ -z "$labels" ]; then
        echo -e "${RED}[$env_name] no app.kubernetes.io/version label found${NC}"
        FAILURES=$((FAILURES + 1))
    else
        while IFS= read -r label; do
            if [ "$label" != "$VERSION" ]; then
                echo -e "${RED}[$env_name] version label ${label} does not match project version ${VERSION}${NC}"
                FAILURES=$((FAILURES + 1))
            fi
        done <<< "$labels"
    fi
done

if [ "$FAILURES" -gt 0 ]; then
    echo -e "${RED}Manifest verification failed with ${FAILURES} problem(s).${NC}"
    exit 1
fi

echo -e "${GREEN}All overlays pin a buildable, version-matched image tag.${NC}"
