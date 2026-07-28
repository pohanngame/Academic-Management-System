#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ID=""
PROJECT_DIR="$PWD"
VERIFY_ONLY=false
FORCE=false
TEMP_DIR=""

usage() {
  echo "Usage: $0 --id UUID [--project-dir DIR] [--verify-only] [--force]" >&2
}

cleanup() {
  if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then
    rm -rf -- "$TEMP_DIR"
  fi
}
trap cleanup EXIT

while (($#)); do
  case "$1" in
    --id) ID="${2:-}"; shift 2 ;;
    --project-dir) PROJECT_DIR="${2:-}"; shift 2 ;;
    --verify-only) VERIFY_ONLY=true; shift ;;
    --force) FORCE=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] \
  || { echo "Staged restore failed: invalid task ID" >&2; exit 2; }
PROJECT_DIR="$(cd -- "$PROJECT_DIR" && pwd -P)"
[[ -f "$PROJECT_DIR/docker-compose.yml" || -f "$PROJECT_DIR/compose.yml" || -f "$PROJECT_DIR/compose.yaml" ]] \
  || { echo "Staged restore failed: target Compose file not found" >&2; exit 2; }

DC=(docker compose --project-directory "$PROJECT_DIR" --profile full)
CONFIG_JSON="$("${DC[@]}" config --format json)"
readarray -t COMPOSE_VALUES < <(
  python3 -c '
import json, sys
config = json.load(sys.stdin)
project = config.get("name", "")
volume = config.get("volumes", {}).get("backend-data", {}).get("name", "")
print(project)
print(volume)
' <<<"$CONFIG_JSON"
)
CONFIG_JSON=""
PROJECT_NAME="${COMPOSE_VALUES[0]:-}"
BACKEND_VOLUME="${COMPOSE_VALUES[1]:-}"
[[ "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]{1,62}$ ]] \
  || { echo "Staged restore failed: unsafe Compose project name" >&2; exit 2; }
[[ "$BACKEND_VOLUME" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$ ]] \
  || { echo "Staged restore failed: unsafe backend-data volume name" >&2; exit 2; }
docker volume inspect "$BACKEND_VOLUME" >/dev/null 2>&1 \
  || { echo "Staged restore failed: backend-data volume does not exist" >&2; exit 3; }

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/restore-staged.XXXXXX")"
chmod 700 "$TEMP_DIR"
LOCAL_PACKAGE="$TEMP_DIR/$ID.apbackup"

"${DC[@]}" --project-name "$PROJECT_NAME" run --rm --no-deps --entrypoint sh backend -c '
  set -eu
  package="/app/data/restore-inbox/$1.apbackup"
  [ -f "$package" ] || exit 44
  cat -- "$package"
' -- "$ID" >"$LOCAL_PACKAGE" \
  || { echo "Staged restore failed: package was not found or could not be copied" >&2; exit 3; }
chmod 600 "$LOCAL_PACKAGE"
[[ -s "$LOCAL_PACKAGE" ]] \
  || { echo "Staged restore failed: copied package is empty" >&2; exit 3; }

python3 "$SCRIPT_DIR/verify-backup.py" "$LOCAL_PACKAGE" --max-schema 11
if "$VERIFY_ONLY"; then
  echo "Staged restore package verification succeeded."
  exit 0
fi

RESTORE_ARGS=(
  "$SCRIPT_DIR/restore-backup.sh"
  --backup "$LOCAL_PACKAGE"
  --project-dir "$PROJECT_DIR"
  --project-name "$PROJECT_NAME"
)
if "$FORCE"; then
  RESTORE_ARGS+=(--force)
fi

set +e
bash "${RESTORE_ARGS[@]}"
RESTORE_STATUS=$?
set -e
if ((RESTORE_STATUS != 0)); then
  echo "Staged restore failed; the staged package was retained for investigation or retry." >&2
  exit "$RESTORE_STATUS"
fi

"${DC[@]}" --project-name "$PROJECT_NAME" run --rm --no-deps --entrypoint sh backend -c '
  set -eu
  inbox="/app/data/restore-inbox"
  rm -f -- "$inbox/$1.apbackup" "$inbox/$1.json"
' -- "$ID" >/dev/null
echo "Restore completed; the staged package was removed."
