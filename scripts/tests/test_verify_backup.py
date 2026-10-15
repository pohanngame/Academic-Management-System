import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

MODULE_PATH = Path(__file__).parent.parent / "verify-backup.py"
SPEC = importlib.util.spec_from_file_location("verify_backup", MODULE_PATH)
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


class VerifyBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def package(self, *, schema=11, version=1, mutate_manifest=None, extra=None):
        files = {
            "database.sql": b"CREATE TABLE example(id BIGINT);",
            "secrets/ai-config.key": b"unit-test-key-material",
            "uploads/example.txt": b"attachment",
        }
        if version == 2:
            files["secrets/file-encryption.key"] = b"f" * 32
        manifest = {
            "backupFormatVersion": version,
            "flywaySchemaVersion": str(schema),
            "includesAiEncryptionKey": True,
            "includesFileEncryptionKey": version == 2,
            "fileCount": len(files),
            "files": {
                name: {"size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                for name, data in files.items()
            },
        }
        if mutate_manifest:
            mutate_manifest(manifest)
        path = self.root / "test.apbackup"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("manifest.json", json.dumps(manifest))
            for name, data in files.items():
                archive.writestr(name, data)
            if extra:
                extra(archive)
        return path

    def test_valid_package_and_extract(self):
        package = self.package()
        manifest = VERIFY.verify_backup(package)
        self.assertEqual("11", manifest["flywaySchemaVersion"])
        target = self.root / "extract"
        VERIFY.extract_verified(package, target)
        self.assertEqual(b"attachment", (target / "uploads/example.txt").read_bytes())

    def test_checksum_tampering_fails(self):
        package = self.package(
            mutate_manifest=lambda manifest: manifest["files"]["uploads/example.txt"].update(
                sha256="0" * 64
            )
        )
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(package)

    def test_v2_requires_file_encryption_key(self):
        package = self.package(version=2, schema=15)
        self.assertEqual(2, VERIFY.verify_backup(package)["backupFormatVersion"])
        rewritten = self.root / "missing-file-key.apbackup"
        with zipfile.ZipFile(package) as source, zipfile.ZipFile(rewritten, "w") as target:
            for info in source.infolist():
                if info.filename != "secrets/file-encryption.key":
                    target.writestr(info, source.read(info))
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(rewritten)

    def test_missing_manifest_fails(self):
        path = self.root / "missing.apbackup"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("database.sql", "data")
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(path)

    def test_missing_database_fails(self):
        package = self.package()
        rewritten = self.root / "missing-database.apbackup"
        with zipfile.ZipFile(package) as source, zipfile.ZipFile(rewritten, "w") as target:
            for info in source.infolist():
                if info.filename != "database.sql":
                    target.writestr(info, source.read(info))
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(rewritten)

    def test_missing_encryption_key_fails(self):
        package = self.package()
        rewritten = self.root / "missing-key.apbackup"
        with zipfile.ZipFile(package) as source, zipfile.ZipFile(rewritten, "w") as target:
            for info in source.infolist():
                if info.filename != "secrets/ai-config.key":
                    target.writestr(info, source.read(info))
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(rewritten)

    def test_invalid_archive_fails(self):
        path = self.root / "invalid.apbackup"
        path.write_text("not a zip file")
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(path)

    def test_path_traversal_fails(self):
        package = self.package(extra=lambda archive: archive.writestr("../escape", "bad"))
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(package)

    def test_symbolic_link_fails(self):
        def add_link(archive):
            info = zipfile.ZipInfo("uploads/link")
            info.create_system = 3
            info.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(info, "../../outside")
        package = self.package(extra=add_link)
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(package)

    def test_newer_schema_fails(self):
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(self.package(schema=12), max_schema=11)

    def test_default_schema_limit_matches_current_application(self):
        self.assertEqual("18", VERIFY.verify_backup(self.package(schema=18))["flywaySchemaVersion"])
        with self.assertRaises(VERIFY.VerificationError):
            VERIFY.verify_backup(self.package(schema=19))

    def test_modified_sql_with_recomputed_hash_still_verifies(self):
        package = self.package()
        rewritten = self.root / "malicious.apbackup"
        malicious = (
            b"USE decoy; DROP TABLE decoy.sentinel; "
            b"CREATE USER 'not_allowed'@'%' IDENTIFIED BY 'not-a-real-secret';"
        )
        with zipfile.ZipFile(package) as source:
            manifest = json.loads(source.read("manifest.json"))
            manifest["files"]["database.sql"] = {
                "size": len(malicious),
                "sha256": hashlib.sha256(malicious).hexdigest(),
            }
            with zipfile.ZipFile(rewritten, "w") as target:
                target.writestr("manifest.json", json.dumps(manifest))
                for info in source.infolist():
                    if info.filename == "manifest.json":
                        continue
                    target.writestr(
                        info,
                        malicious if info.filename == "database.sql" else source.read(info),
                    )

        self.assertEqual("11", VERIFY.verify_backup(rewritten)["flywaySchemaVersion"])


class RestoreScriptSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = (Path(__file__).parent.parent / "restore-backup.sh").read_text(
            encoding="utf-8"
        )
        cls.acceptance_script = (Path(__file__).parent / "test-restore-docker.sh").read_text(
            encoding="utf-8"
        )
        cls.production_compose = (Path(__file__).parents[2] / "docker-compose.yml").read_text(
            encoding="utf-8"
        )
        cls.acceptance_compose = (Path(__file__).parent / "restore-test-compose.yml").read_text(
            encoding="utf-8"
        )
        cls.ocr_dockerfile = (
            Path(__file__).parents[2] / "docker" / "paddleocr" / "Dockerfile"
        ).read_text(encoding="utf-8")

    def test_backup_sql_is_only_sent_to_restricted_import(self):
        self.assertNotRegex(self.script, r"mysql\s+-uroot[^\n]*database\.sql")
        self.assertIn(
            'restricted_import "$VERIFY_DEFAULTS" "$TEMP_DB" "$EXTRACT/database.sql"',
            self.script,
        )
        self.assertIn(
            'restricted_import "$TARGET_DEFAULTS" "$DB_NAME" "$EXTRACT/database.sql"',
            self.script,
        )

    def test_file_switch_uses_separate_post_success_states(self):
        self.assertNotIn("FILES_SWITCHED", self.script)
        for state in (
            "original_uploads_moved",
            "original_secrets_moved",
            "new_uploads_installed",
            "new_secrets_installed",
        ):
            self.assertIn(f"{state}=false", self.script)
            self.assertIn(f"{state}=true", self.script)

    def test_fault_injection_requires_dedicated_project_prefix(self):
        self.assertIn('^restore-test-[a-z0-9_-]+$', self.script)
        for point in (
            "before_file_switch",
            "after_original_uploads_moved",
            "after_original_secrets_moved",
            "after_new_uploads_installed",
            "after_new_secrets_installed",
            "after_database_switch",
            "before_health_check",
        ):
            self.assertIn(point, self.script)

    def test_acceptance_matrix_uses_current_schema_limit(self):
        self.assertNotIn("--max-schema 11", self.acceptance_script)
        self.assertNotIn("--max-schema", self.acceptance_script)

    def test_cold_ocr_startup_has_a_consistent_restore_window(self):
        self.assertIn("start_period: 900s", self.production_compose)
        self.assertIn("start_period: 900s", self.acceptance_compose)
        self.assertRegex(
            self.acceptance_compose,
            r"(?s)  paddleocr:.*?restart: unless-stopped.*?healthcheck:",
        )
        self.assertIn("DEADLINE=$((SECONDS + 1800))", self.script)
        self.assertIn("local deadline=$((SECONDS + 1800))", self.acceptance_script)

    def test_health_wait_allows_recovery_until_deadline_but_rejects_stopped_containers(self):
        self.assertNotIn('[[ "$health" != "unhealthy"', self.script)
        self.assertNotIn('[[ "$health" != "unhealthy"', self.acceptance_script)
        for script in (self.script, self.acceptance_script):
            self.assertIn("container_state", script)
            self.assertIn('"$container_state" == "running"', script)
            self.assertIn('"$container_state" == "restarting"', script)

    def test_ocr_image_preloads_runtime_models(self):
        self.assertIn('RUN python -c "import app"', self.ocr_dockerfile)


if __name__ == "__main__":
    unittest.main()
