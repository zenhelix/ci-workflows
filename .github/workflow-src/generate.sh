#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKFLOWS_DIR="$SCRIPT_DIR/../workflows"

cd "$SCRIPT_DIR"

echo "Generating workflows from Kotlin sources..."

for script in *.main.kts; do
    # Skip shared files (prefixed with _)
    [[ "$script" == _* ]] && continue

    echo "  Processing $script..."
    kotlin "$script"
done

# Move generated YAML files to workflows directory
for yaml in *.yaml; do
    [ -f "$yaml" ] || continue
    target_name="${yaml%.yaml}.yml"
    mv "$yaml" "$WORKFLOWS_DIR/$target_name"
    echo "  Moved $yaml -> workflows/$target_name"
done

echo "Done. Generated workflows are in .github/workflows/"
