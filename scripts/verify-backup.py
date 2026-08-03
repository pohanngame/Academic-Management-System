#!/usr/bin/env python3
"""Verify an academic-profile .apbackup without exposing its contents."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import sys
import zipfile

SUPPORTED_FORMAT_VERSIONS = {1, 2}
DEFAULT_MAX_SCHEMA = 15
REQUIRED_FILES = {"manifest.json", "database.sql", "secrets/ai-config.key"}


class VerificationError(Exception):
    pass


def _safe_name(raw_name: str) -> str:
    if not raw_name or "\\" in raw_name or "\x00" in raw_name:
        raise VerificationError("backup contains an unsafe path")
    path = PurePosixPath(raw_name)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise VerificationError("backup contains an unsafe path")
    return path.as_posix()


def _is_symlink(info: zipfile.ZipInfo) -> bool:
    mode = (info.external_attr >> 16) & 0xFFFF
    return stat.S_ISLNK(mode)


def _sha256_stream(source) -> tuple[int, str]:
    digest = hashlib.sha256()
    size = 0
    while True:
        block = source.read(1024 * 1024)
        if not block:
            break
        size += len(block)
        digest.update(block)
    return size, digest.hexdigest()


def verify_backup(backup: Path, max_schema: int = DEFAULT_MAX_SCHEMA) -> dict:
    if backup.suffix != ".apbackup" or not backup.is_file():
        raise VerificationError("backup file is missing or has the wrong extension")
    try:
        with zipfile.ZipFile(backup) as archive:
            infos: dict[str, zipfile.ZipInfo] = {}
            for info in archive.infolist():
                name = _safe_name(info.filename)
                if name in infos:
                    raise VerificationError("backup contains duplicate paths")
                if _is_symlink(info):
                    raise VerificationError("backup contains a symbolic link")
                infos[name] = info
            missing = REQUIRED_FILES.difference(infos)
            if missing:
                raise VerificationError("backup is missing required files")
            manifest_info = infos["manifest.json"]
            if manifest_info.file_size > 4 * 1024 * 1024:
                raise VerificationError("backup manifest is too large")
            try:
                manifest = json.loads(archive.read(manifest_info))
            except (json.JSONDecodeError, UnicodeDecodeError, ValueError):
                raise VerificationError("backup manifest is invalid") from None
            format_version = manifest.get("backupFormatVersion")
            if format_version not in SUPPORTED_FORMAT_VERSIONS:
                raise VerificationError("backup format version is not supported")
            try:
                schema = int(manifest["flywaySchemaVersion"])
            except (KeyError, TypeError, ValueError):
                raise VerificationError("backup schema version is invalid") from None
            if schema > max_schema:
                raise VerificationError("backup schema is newer than the target application")
            if manifest.get("includesAiEncryptionKey") is not True:
                raise VerificationError("backup does not include the AI encryption key")
            if format_version == 2:
                if manifest.get("includesFileEncryptionKey") is not True:
                    raise VerificationError("backup does not include the file encryption key")
                if "secrets/file-encryption.key" not in infos:
                    raise VerificationError("backup is missing the file encryption key")
            declared = manifest.get("files")
            if not isinstance(declared, dict):
                raise VerificationError("backup file manifest is invalid")
            actual_files = {name for name, info in infos.items() if not info.is_dir() and name != "manifest.json"}
            if set(declared) != actual_files:
                raise VerificationError("backup file list does not match the manifest")
            for name, expected in declared.items():
                _safe_name(name)
                if not isinstance(expected, dict):
                    raise VerificationError("backup checksum metadata is invalid")
                with archive.open(infos[name]) as source:
                    size, checksum = _sha256_stream(source)
                if expected.get("size") != size or expected.get("sha256") != checksum:
                    raise VerificationError("backup checksum validation failed")
            return manifest
    except zipfile.BadZipFile:
        raise VerificationError("backup format is invalid") from None


def extract_verified(backup: Path, target: Path, max_schema: int = DEFAULT_MAX_SCHEMA) -> dict:
    manifest = verify_backup(backup, max_schema)
    target = target.resolve()
    if target.exists() and any(target.iterdir()):
        raise VerificationError("extraction target must be empty")
    target.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(backup) as archive:
            for info in archive.infolist():
                name = _safe_name(info.filename)
                destination = (target / Path(*PurePosixPath(name).parts)).resolve()
                if os.path.commonpath((str(target), str(destination))) != str(target):
                    raise VerificationError("backup extraction path escaped the target")
                if info.is_dir():
                    destination.mkdir(parents=True, exist_ok=True)
                    continue
                destination.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, destination.open("xb") as output:
                    shutil.copyfileobj(source, output, 1024 * 1024)
        return manifest
    except Exception:
        shutil.rmtree(target, ignore_errors=True)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify an academic-profile migration backup")
    parser.add_argument("backup", type=Path)
    parser.add_argument("--max-schema", type=int, default=DEFAULT_MAX_SCHEMA)
    parser.add_argument("--extract", type=Path)
    args = parser.parse_args()
    try:
        if args.extract:
            manifest = extract_verified(args.backup, args.extract, args.max_schema)
        else:
            manifest = verify_backup(args.backup, args.max_schema)
        print(
            "Backup verified: format=%s schema=%s files=%s"
            % (
                manifest["backupFormatVersion"],
                manifest["flywaySchemaVersion"],
                manifest["fileCount"],
            )
        )
        return 0
    except (VerificationError, OSError) as error:
        print(f"Backup verification failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
