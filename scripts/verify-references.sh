#!/bin/bash
# Fails when a script or document points at a repository file that does not exist.
#
# Three deploy scripts applied flat manifests under k8s/ that were deleted in the move
# to Kustomize, so each aborted on its first kubectl apply, and the docs advertised a
# build script that had also been removed (issue #27). Nothing caught it because the
# only way to find out was to run the script.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

FAILURES=0

# Referenced paths worth checking: repo-relative manifests and scripts.
PATTERN='(\./)?(k8s/[A-Za-z0-9_./-]+\.ya?ml|scripts/[A-Za-z0-9_.-]+\.sh)'

targets=$(find scripts -name '*.sh' -type f; find . -maxdepth 1 -name '*.md' -type f)

for target in $targets; do
    # Skip this script: its own comments name the historical paths.
    if [ "$(basename "$target")" = "verify-references.sh" ]; then
        continue
    fi

    while IFS= read -r match; do
        line_no="${match%%:*}"
        ref="${match#*:}"
        ref="${ref#./}"
        if [ ! -e "$ref" ]; then
            echo -e "${RED}${target}:${line_no} references missing path: ${ref}${NC}"
            FAILURES=$((FAILURES + 1))
        fi
    done < <(grep -noE "$PATTERN" "$target" || true)
done

if [ "$FAILURES" -gt 0 ]; then
    echo -e "${RED}Reference verification failed with ${FAILURES} missing path(s).${NC}"
    exit 1
fi

echo -e "${GREEN}All script and document references point at files that exist.${NC}"
