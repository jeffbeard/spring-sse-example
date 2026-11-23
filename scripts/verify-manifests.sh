#!/bin/bash
# Renders deploy/ (the manifests Argo CD syncs) and fails on:
#   - a kustomization that does not build
#   - images with no tag or pinned to :latest (non-reproducible deploys, issue #16)
#   - image tags that do not match the project version in build.gradle
#   - actuator exposed more widely than intended, or routed through an Ingress (issue #13)
#   - dashboard files that are not valid JSON or have no uid
#
# Runs entirely offline - `kubectl kustomize` needs no cluster.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

for tool in kubectl jq; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo -e "${RED}${tool} is required${NC}"
        exit 1
    fi
done

VERSION=$(grep "^version = " build.gradle | cut -d "'" -f 2)
if [ -z "$VERSION" ]; then
    echo -e "${RED}Could not read version from build.gradle${NC}"
    exit 1
fi
echo -e "${YELLOW}Project version: ${VERSION}${NC}"

FAILURES=0

if ! rendered=$(kubectl kustomize deploy); then
    echo -e "${RED}deploy/ does not build with kustomize${NC}"
    exit 1
fi

# In a rendered list item the key may follow the dash ("- image: ...").
images=$(echo "$rendered" | grep -E "^[[:space:]-]*image:[[:space:]]" | awk '{print $NF}' | sort -u)
if [ -z "$images" ]; then
    echo -e "${RED}no image found in rendered output${NC}"
    FAILURES=$((FAILURES + 1))
else
    while IFS= read -r image; do
        tag="${image##*:}"
        if [ "$tag" = "latest" ]; then
            echo -e "${RED}image pinned to :latest -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        elif [ "$image" = "$tag" ]; then
            echo -e "${RED}image has no tag -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        elif [ "$tag" != "$VERSION" ]; then
            echo -e "${RED}image tag ${tag} does not match project version ${VERSION} -> ${image}${NC}"
            FAILURES=$((FAILURES + 1))
        else
            echo -e "${GREEN}image ${image}${NC}"
        fi
    done <<< "$images"
fi

# Actuator has no authentication, so it must never be reachable through an Ingress
# (issue #13). deploy/ has no Ingress today; this guards the day one is added.
if echo "$rendered" | awk '
    /^---$/ { in_ingress = 0 }
    /^kind: Ingress$/ { in_ingress = 1 }
    in_ingress && /^[[:space:]]*path:[[:space:]]*\/actuator/ { found = 1 }
    END { exit !found }
'; then
    echo -e "${RED}Ingress routes /actuator externally${NC}"
    FAILURES=$((FAILURES + 1))
fi

# Keep the actuator surface small: health for the probes, prometheus for the scrape.
# The value is the line after the env var name in the rendered Deployment.
exposure=$(echo "$rendered" | awk '
    found { sub(/^[[:space:]]*value:[[:space:]]*/, ""); gsub(/"/, ""); print; exit }
    /name:[[:space:]]*MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE/ { found = 1 }
')
for endpoint in $(echo "$exposure" | tr ',' ' '); do
    case "$endpoint" in
        health|prometheus) ;;
        *)
            echo -e "${RED}actuator exposure too broad: ${exposure}${NC}"
            FAILURES=$((FAILURES + 1))
            break
            ;;
    esac
done

for dashboard in deploy/dashboards/*.json; do
    [ -e "$dashboard" ] || continue
    if ! uid=$(jq -er '.uid | select(type == "string" and length > 0)' "$dashboard" 2>/dev/null); then
        echo -e "${RED}${dashboard} is not valid JSON or has no top-level uid${NC}"
        FAILURES=$((FAILURES + 1))
    else
        echo -e "${GREEN}dashboard ${dashboard} (uid ${uid})${NC}"
    fi
done

if [ "$FAILURES" -gt 0 ]; then
    echo -e "${RED}Manifest verification failed with ${FAILURES} problem(s).${NC}"
    exit 1
fi

echo -e "${GREEN}deploy/ builds, pins a version-matched image tag, and its dashboards parse.${NC}"
