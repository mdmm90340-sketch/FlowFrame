#!/usr/bin/env python3
"""Build a deterministic yt-dlp zipapp without make or an external zip tool."""

from __future__ import annotations

import argparse
import shutil
import tempfile
import zipfile
from pathlib import Path


ZIP_TIMESTAMP = (2000, 1, 1, 1, 1, 0)
FILE_MODE = 0o100644 << 16


def _entry(name: str, payload: bytes) -> tuple[zipfile.ZipInfo, bytes]:
    info = zipfile.ZipInfo(name, ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = FILE_MODE
    return info, payload


def build(source: Path, output: Path, manifest: Path) -> None:
    package = source / "yt_dlp"
    lazy_extractors = package / "extractor" / "lazy_extractors.py"
    if not lazy_extractors.is_file():
        raise SystemExit("lazy_extractors.py is missing; generate it before building")

    members: list[tuple[str, bytes]] = []
    for path in package.rglob("*"):
        if not path.is_file() or path.suffix not in {".py", ".js"}:
            continue
        relative = path.relative_to(source).as_posix()
        if relative == "yt_dlp/__main__.py" or "__pycache__" in path.parts:
            continue
        members.append((relative, path.read_bytes()))

    members.sort(key=lambda item: item[0])
    members.append(("FLOWFRAME_KERNEL.json", manifest.read_bytes()))
    members.append(("__main__.py", (package / "__main__.py").read_bytes()))

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="flowframe-ytdlp-zip-") as temp_dir:
        archive = Path(temp_dir) / "yt-dlp.zip"
        with zipfile.ZipFile(archive, "w", allowZip64=True, compresslevel=9) as bundle:
            for name, payload in members:
                info, data = _entry(name, payload)
                bundle.writestr(info, data, compresslevel=9)

        temporary_output = output.with_name(f".{output.name}.tmp")
        with temporary_output.open("wb") as result, archive.open("rb") as payload:
            result.write(b"#!/usr/bin/env python3\n")
            shutil.copyfileobj(payload, result)
        temporary_output.replace(output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    args = parser.parse_args()
    build(args.source.resolve(), args.output.resolve(), args.manifest.resolve())


if __name__ == "__main__":
    main()
