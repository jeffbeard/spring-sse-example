#!/bin/bash
# Renders a Kustomize overlay to stdout, optionally overriding the image tag/name.
#
# Replaces the previous `kubectl kustomize ... | envsubst` pipeline, which only ever
# substituted a ${IMAGE_TAG} *label* - the image itself came from the kustomize images
# transformer and stayed at :latest, so deploys ignored the requested tag (issue #16).
#
# The override is applied to a temporary copy of k8s/, so the repository is never
# mutated and a failed deploy leaves nothing behind.
#
# Usage: render-manifests.sh <environment> [image-tag] [image-name]

set -euo pipefail

ENVIRONMENT="${1:?usage: render-manifests.sh <environment> [image-tag] [image-name]}"
IMAGE_TAG="${2:-}"
IMAGE_NAME="${3:-}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OVERLAY_DIR="$REPO_ROOT/k8s/overlays/$ENVIRONMENT"

if [ ! -d "$OVERLAY_DIR" ]; then
    echo "Unknown environment '$ENVIRONMENT' (no such overlay: $OVERLAY_DIR)" >&2
    exit 1
fi

# No override requested: render the committed, pinned manifests as-is.
if [ -z "$IMAGE_TAG" ] && [ -z "$IMAGE_NAME" ]; then
    kubectl kustomize "$OVERLAY_DIR"
    exit 0
fi

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

cp -R "$REPO_ROOT/k8s" "$WORK_DIR/k8s"
KUSTOMIZATION="$WORK_DIR/k8s/overlays/$ENVIRONMENT/kustomization.yaml"

if [ -n "$IMAGE_TAG" ]; then
    # Keep the version label in step with the tag it describes.
    sed -i.bak -E "s|^([[:space:]]*newTag:[[:space:]]*).*$|\1\"$IMAGE_TAG\"|" "$KUSTOMIZATION"
    sed -i.bak -E "s|^([[:space:]]*app\.kubernetes\.io/version:[[:space:]]*).*$|\1\"$IMAGE_TAG\"|" "$KUSTOMIZATION"
fi

if [ -n "$IMAGE_NAME" ]; then
    sed -i.bak -E "s|^([[:space:]]*newName:[[:space:]]*).*$|\1$IMAGE_NAME|" "$KUSTOMIZATION"
fi

rm -f "$KUSTOMIZATION.bak"
kubectl kustomize "$WORK_DIR/k8s/overlays/$ENVIRONMENT"
