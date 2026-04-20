#!/usr/bin/env python3
"""
Fails the job if any `uses:` line in .github/workflows or .github/actions
references a non-SHA third-party action.

Exemptions (do NOT need SHA):
  - local refs: `uses: ./path`
  - reusable-workflow refs to zenhelix/ci-workflows reusable workflows
    (GitHub's sha_pinning_required policy exempts reusable workflows)
"""
from __future__ import annotations
import pathlib
import re
import sys

SHA_REF = re.compile(r"@[0-9a-f]{40}(?:\s|$|[\"'])")
LOCAL_REF = re.compile(r"""uses:\s*['"]?\./""")
ZENHELIX_WORKFLOW = re.compile(
    r"""uses:\s*['"]?zenhelix/ci-workflows/\.github/workflows/[^@]+@[^\s'"]+"""
)
USES_LINE = re.compile(r"^\s*(?:-\s*)?uses:\s*")


def main() -> int:
    roots = [pathlib.Path(".github/workflows"), pathlib.Path(".github/actions")]
    bad: list[str] = []
    scanned = 0
    for root in roots:
        if not root.exists():
            continue
        for path in sorted(root.rglob("*.y*ml")):
            scanned += 1
            for i, line in enumerate(path.read_text().splitlines(), 1):
                if not USES_LINE.search(line):
                    continue
                if SHA_REF.search(line) or LOCAL_REF.search(line) or ZENHELIX_WORKFLOW.search(line):
                    continue
                bad.append(f"{path}:{i}: {line.strip()}")
    if bad:
        print("::error::non-SHA third-party action refs found:")
        print("\n".join(bad))
        return 1
    print(f"SHA pinning OK ({scanned} files scanned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
