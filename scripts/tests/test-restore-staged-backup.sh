#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

TEST_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$(cd -- "$TEST_DIR/../.." && pwd -P)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/restore-staged-script-test.XXXXXX")"
trap 'rm -rf -- "$WORK"' EXIT

mkdir -p "$WORK/project/scripts" "$WORK/bin" "$WORK/tmp"
touch "$WORK/project/docker-compose.yml"
cp "$PROJECT_ROOT/scripts/restore-staged-backup.sh" "$WORK/project/scripts/"

cat >"$WORK/project/scripts/verify-backup.py" <<'PY'
import os
import pathlib
import stat
import sys

path = pathlib.Path(sys.argv[1])
assert path.is_file() and path.read_bytes() == b"PK\x03\x04fixture"
assert stat.S_IMODE(path.stat().st_mode) == 0o600
assert stat.S_IMODE(path.parent.stat().st_mode) == 0o700
pathlib.Path(os.environ["VERIFY_MARKER"]).write_text("verified", encoding="utf-8")
raise SystemExit(int(os.environ.get("VERIFY_EXIT", "0")))
PY

cat >"$WORK/project/scripts/restore-backup.sh" <<'SH'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$@" >"$RESTORE_ARGS_MARKER"
exit "${RESTORE_EXIT:-0}"
SH
chmod +x "$WORK/project/scripts/restore-backup.sh"

cat >"$WORK/bin/docker" <<'SH'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >>"$DOCKER_CALLS"
if [[ "${1:-}" == "volume" && "${2:-}" == "inspect" ]]; then
  exit 0
fi
if [[ "$*" == *"config --format json"* ]]; then
  printf '%s\n' '{"name":"restore-test-wrapper","volumes":{"backend-data":{"name":"restore-test-wrapper_backend-data"}}}'
  exit 0
fi
if [[ "$*" == *"cat --"* ]]; then
  [[ "${MISSING_PACKAGE:-}" != "1" ]] || exit 44
  printf 'PK\003\004fixture'
  exit 0
fi
if [[ "$*" == *"rm -f --"* ]]; then
  : >"$DELETE_MARKER"
  exit 0
fi
exit 1
SH
chmod +x "$WORK/bin/docker"

export PATH="$WORK/bin:$PATH"
export TMPDIR="$WORK/tmp"
export VERIFY_MARKER="$WORK/verified"
export RESTORE_ARGS_MARKER="$WORK/restore-args"
export DOCKER_CALLS="$WORK/docker-calls"
export DELETE_MARKER="$WORK/deleted"
RUN_LOG="$WORK/run.log"
ID="11111111-1111-4111-8111-111111111111"
WRAPPER="$WORK/project/scripts/restore-staged-backup.sh"

if bash "$WRAPPER" --id ../unsafe --project-dir "$WORK/project" >/dev/null 2>&1; then
  echo "invalid UUID was accepted" >&2
  exit 1
fi
[[ ! -e "$DOCKER_CALLS" ]]

export MISSING_PACKAGE=1
if bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" >/dev/null 2>&1; then
  echo "missing staged package was accepted" >&2
  exit 1
fi
unset MISSING_PACKAGE
[[ ! -e "$RESTORE_ARGS_MARKER" && ! -e "$DELETE_MARKER" ]]

bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" --verify-only >/dev/null
[[ -f "$VERIFY_MARKER" && ! -e "$RESTORE_ARGS_MARKER" && ! -e "$DELETE_MARKER" ]]
[[ -z "$(find "$WORK/tmp" -mindepth 1 -print -quit)" ]]

rm -f "$VERIFY_MARKER"
export VERIFY_EXIT=7
if bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" >/dev/null 2>&1; then
  echo "verification failure did not stop restore" >&2
  exit 1
fi
unset VERIFY_EXIT
[[ ! -e "$RESTORE_ARGS_MARKER" && ! -e "$DELETE_MARKER" ]]

bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" >/dev/null
! grep -Fx -- "--force" "$RESTORE_ARGS_MARKER" >/dev/null
[[ -f "$DELETE_MARKER" ]]

rm -f "$RESTORE_ARGS_MARKER" "$DELETE_MARKER"
bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" --force >/dev/null
grep -Fx -- "--project-dir" "$RESTORE_ARGS_MARKER" >/dev/null
grep -Fx -- "--project-name" "$RESTORE_ARGS_MARKER" >/dev/null
grep -Fx -- "restore-test-wrapper" "$RESTORE_ARGS_MARKER" >/dev/null
grep -Fx -- "--force" "$RESTORE_ARGS_MARKER" >/dev/null
[[ -f "$DELETE_MARKER" ]]

rm -f "$DELETE_MARKER"
export RESTORE_EXIT=9
if bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" >/dev/null 2>&1; then
  echo "restore failure was reported as success" >&2
  exit 1
fi
unset RESTORE_EXIT
[[ ! -e "$DELETE_MARKER" ]]
[[ -z "$(find "$WORK/tmp" -mindepth 1 -print -quit)" ]]

! grep -Eq '\beval\b|docker\.sock' "$PROJECT_ROOT/scripts/restore-staged-backup.sh"
bash "$WRAPPER" --id "$ID" --project-dir "$WORK/project" --verify-only >"$RUN_LOG" 2>&1
! grep -F 'fixture' "$RUN_LOG" >/dev/null
! grep -F 'docker.sock' "$DOCKER_CALLS" >/dev/null
[[ -z "$(find "$WORK/tmp" -mindepth 1 -print -quit)" ]]
echo "restore-staged-backup wrapper tests passed"
