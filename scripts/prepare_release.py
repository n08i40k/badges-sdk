#!/usr/bin/env python3
"""Проставляет релизную версию в исходник лоадера и pyproject.toml."""

import argparse
import re
from pathlib import Path


VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")


def replace_one(text: str, pattern: str, replacement: str, description: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"Failed to update {description}")
    return updated


def update_loader_file(loader_path: Path, version: str) -> None:
    text = loader_path.read_text()
    text = replace_one(
        text,
        r'^BADGES_SDK_VERSION = ".*"$',
        f'BADGES_SDK_VERSION = "{version}"',
        "BADGES_SDK_VERSION",
    )
    loader_path.write_text(text)


def update_pyproject_file(pyproject_path: Path, version: str) -> None:
    text = pyproject_path.read_text()
    text = replace_one(
        text,
        r'^version = ".*"$',
        f'version = "{version}"',
        "pyproject version",
    )
    pyproject_path.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--loader-file", required=True, type=Path)
    parser.add_argument("--pyproject-file", required=True, type=Path)
    args = parser.parse_args()

    if VERSION_RE.fullmatch(args.version) is None:
        raise SystemExit("Version must match x.x.x")

    if not args.loader_file.is_file():
        raise SystemExit(f"Loader file not found: {args.loader_file}")
    if not args.pyproject_file.is_file():
        raise SystemExit(f"pyproject file not found: {args.pyproject_file}")

    update_loader_file(args.loader_file, args.version)
    update_pyproject_file(args.pyproject_file, args.version)

    print(f"version={args.version}")


if __name__ == "__main__":
    main()
