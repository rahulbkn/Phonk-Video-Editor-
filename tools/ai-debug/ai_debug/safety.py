"""Hard safety rules for the AI debug loop.

Validated BEFORE committing/pushing an AI fix. A violation means the change is
discarded and the attempt is recorded as failed — never committed.
"""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

FORBIDDEN_PATH_PATTERNS = [
    r"\.jks$",
    r"\.keystore$",
    r"keystore\.properties$",
    r"local\.properties$",
    r"google-services\.json$",
    r"service-account.*\.json$",
    r"\.env$",
    r"secrets?/",
    r"\.github/workflows/.*\.ya?ml$",  # AI must not rewrite CI itself
    r"gradle-wrapper\.properties$",
]

# Content-level red flags in the diff (added lines only).
FORBIDDEN_CONTENT_PATTERNS = [
    (r"^\+.*@Ignore", "Test annotated with @Ignore (disabling a test)"),
    (r"^\+.*abortOnError\s*=?\s*false", "Lint abortOnError disabled"),
    (r"^\+.*checkReleaseBuilds\s*=?\s*false", "Lint checkReleaseBuilds disabled"),
    (r"^\+.*//\s*@Test", "Test annotation commented out"),
    (r"^\+.*System\.exit", "Unexpected System.exit call introduced"),
    (r"^\+.*setMinSdk\s*\(|^\+.*minSdkVersion\s*=|^\+.*minSdk\s*=", "minSdk lowered"),
    (r"^\+.*\breturn\s+null\b.*//\s*TEMPORARY", "Suspicious temporary null return"),
]

FORBIDDEN_DELETIONS = [
    (r"^-.*@Test", "An existing @Test method appears to have been deleted"),
    (r"^-.*class\s+\w+Test", "An existing test class appears to have been deleted"),
    (r"^-\s*</?test", "Test XML element removed"),
]


def get_diff(repo_dir: Path | str) -> str:
    result = subprocess.run(
        ["git", "diff", "--unified=0"],
        cwd=str(repo_dir), capture_output=True, text=True, check=True,
    )
    return result.stdout


def get_changed_files(repo_dir: Path | str) -> list[str]:
    """All changed or untracked files (paths only). Untracked directories are
    expanded to their contained files so forbidden paths inside them are seen."""
    repo = Path(repo_dir)
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=str(repo_dir), capture_output=True, text=True, check=True,
    )
    files: list[str] = []
    for line in result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if path.endswith("/"):
            # untracked directory — expand
            base = repo / path
            if base.is_dir():
                for p in sorted(base.rglob("*")):
                    if p.is_file() and not p.is_symlink():
                        files.append(str(p.relative_to(repo)))
            continue
        files.append(path)
    return [f for f in files if f.strip()]


def _untracked_lines(repo_dir: Path | str, changed_files: list[str]) -> str:
    """Read untracked file contents, prefixing '+' so content patterns match."""
    chunks = []
    repo = Path(repo_dir)
    for f in changed_files:
        path = repo / f
        if path.is_file() and not path.is_symlink():
            try:
                content = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for line in content.splitlines():
                chunks.append(f"+{line}")
    return "\n".join(chunks)


def validate_diff(repo_dir: Path | str) -> tuple[bool, list[str]]:
    """Returns (is_safe, list_of_violations)."""
    violations: list[str] = []
    changed_files = get_changed_files(repo_dir)

    for f in changed_files:
        for pattern in FORBIDDEN_PATH_PATTERNS:
            if re.search(pattern, f):
                violations.append(f"Forbidden file touched: {f} (matches {pattern})")

    # Tracked-file diff (both staged and unstaged vs HEAD). Falls back to
    # working-tree vs index when HEAD does not exist yet (no commits).
    try:
        diff_text = subprocess.run(
            ["git", "diff", "HEAD", "--unified=0"],
            cwd=str(repo_dir), capture_output=True, text=True, check=True,
        ).stdout
    except subprocess.CalledProcessError:
        diff_text = subprocess.run(
            ["git", "diff", "--unified=0"],
            cwd=str(repo_dir), capture_output=True, text=True, check=True,
        ).stdout
        staged = subprocess.run(
            ["git", "diff", "--cached", "--unified=0"],
            cwd=str(repo_dir), capture_output=True, text=True, check=True,
        ).stdout
        diff_text += "\n" + staged
    # Untracked files have no diff yet — include their raw content.
    diff_text += "\n" + _untracked_lines(repo_dir, changed_files)

    for line in diff_text.splitlines():
        for pattern, reason in FORBIDDEN_CONTENT_PATTERNS:
            if re.match(pattern, line):
                violations.append(f"{reason}: {line.strip()}")
        for pattern, reason in FORBIDDEN_DELETIONS:
            if re.match(pattern, line):
                violations.append(f"{reason}: {line.strip()}")

    return (len(violations) == 0, violations)


def discard_changes(repo_dir: Path | str) -> None:
    subprocess.run(["git", "checkout", "--", "."], cwd=str(repo_dir), check=False)
    subprocess.run(["git", "clean", "-fd"], cwd=str(repo_dir), check=False)
