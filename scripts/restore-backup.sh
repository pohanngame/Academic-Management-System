#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
VERIFY_ONLY=false
FORCE=false
BACKUP=""
PROJECT_DIR=""
PROJECT=""
MAX_SCHEMA=11
TEST_FAIL_AT="${RESTORE_TEST_FAIL_AT:-}"
TEST_CONFIRMATION="${RESTORE_TEST_CONFIRMATION:-}"

usage() {
  echo "Usage: $0 --backup FILE [--verify-only] [--project-dir DIR --project-name NAME [--force]]" >&2
}

while (($#)); do
  case "$1" in
    --backup) BACKUP="${2:-}"; shift 2 ;;
    --project-dir) PROJECT_DIR="${2:-}"; shift 2 ;;
    --project-name) PROJECT="${2:-}"; shift 2 ;;
    --max-schema) MAX_SCHEMA="${2:-}"; shift 2 ;;
    --verify-only) VERIFY_ONLY=true; shift ;;
    --force) FORCE=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ -n "$BACKUP" ]] || { usage; exit 2; }
BACKUP="$(cd -- "$(dirname -- "$BACKUP")" && pwd -P)/$(basename -- "$BACKUP")"
python3 "$SCRIPT_DIR/verify-backup.py" "$BACKUP" --max-schema "$MAX_SCHEMA"

if "$VERIFY_ONLY"; then
  exit 0
fi

[[ -n "$PROJECT_DIR" && -n "$PROJECT" ]] || { usage; exit 2; }
PROJECT_DIR="$(cd -- "$PROJECT_DIR" && pwd -P)"
[[ -f "$PROJECT_DIR/docker-compose.yml" || -f "$PROJECT_DIR/compose.yml" || -f "$PROJECT_DIR/compose.yaml" ]] \
  || { echo "Restore failed: target Compose file not found" >&2; exit 2; }
[[ "$PROJECT" =~ ^[a-z0-9][a-z0-9_-]{1,40}$ ]] \
  || { echo "Restore failed: unsafe Compose project name" >&2; exit 2; }
[[ "$MAX_SCHEMA" =~ ^[0-9]+$ ]] \
  || { echo "Restore failed: invalid schema limit" >&2; exit 2; }

case "$TEST_FAIL_AT" in
  ""|before_file_switch|after_original_uploads_moved|after_original_secrets_moved|\
after_new_uploads_installed|after_new_secrets_installed|after_database_switch|before_health_check) ;;
  *) echo "Restore failed: invalid test fault injection point" >&2; exit 2 ;;
esac
if [[ -n "$TEST_FAIL_AT" && ! "$PROJECT" =~ ^restore-test-[a-z0-9_-]+$ ]]; then
  echo "Restore failed: test fault injection requires a restore-test-* project" >&2
  exit 2
fi
if [[ -n "$TEST_CONFIRMATION" && ! "$PROJECT" =~ ^restore-test-[a-z0-9_-]+$ ]]; then
  echo "Restore failed: test confirmation requires a restore-test-* project" >&2
  exit 2
fi

RESTORE_ID="$(date -u +%Y%m%d%H%M%S)-$$"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/academic-profile-restore.XXXXXX")"
EXTRACT="$WORK/package"
TEMP_DB=""
DB_NAME=""
DB_SWITCHED=false
SAFETY_DIR=""
VERIFY_USER=""
VERIFY_DEFAULTS=""
TARGET_USER=""
TARGET_DEFAULTS=""
RESTORE_PHASE="initialization"
DC=(docker compose --project-name "$PROJECT" --project-directory "$PROJECT_DIR" --profile full)

root_sql() {
  "${DC[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch'
}

create_database() {
  local database="$1"
  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
    || { echo "Restore failed: unsafe database name" >&2; return 1; }
  printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n' "$database" \
    | root_sql >/dev/null
}

recreate_database() {
  local database="$1"
  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] \
    || { echo "Restore failed: unsafe database name" >&2; return 1; }
  {
    printf 'DROP DATABASE IF EXISTS `%s`;\n' "$database"
    printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n' "$database"
  } | root_sql >/dev/null
}

drop_database() {
  local database="$1"
  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || return 1
  printf 'DROP DATABASE IF EXISTS `%s`;\n' "$database" | root_sql >/dev/null
}

create_restore_account() {
  local database="$1" prefix="$2" user_var="$3" defaults_var="$4"
  local username password defaults_file
  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || return 1
  username="${prefix}_$(openssl rand -hex 8)"
  password="$(openssl rand -hex 32)"
  [[ "$username" =~ ^[a-z0-9_]{1,32}$ ]] || return 1

  {
    printf "CREATE USER '%s'@'localhost' IDENTIFIED BY '%s';\n" "$username" "$password"
    printf "GRANT ALL PRIVILEGES ON \`%s\`.* TO '%s'@'localhost';\n" "$database" "$username"
  } | root_sql >/dev/null

  defaults_file="$(
    printf '[client]\nuser=%s\npassword=%s\n' "$username" "$password" \
      | "${DC[@]}" exec -T mysql sh -c '
          set -eu
          umask 077
          credentials="$(mktemp /tmp/academic-profile-restore-client.XXXXXX)"
          cat > "$credentials"
          chmod 600 "$credentials"
          printf "%s" "$credentials"
        '
  )"
  defaults_file="${defaults_file//$'\r'/}"
  [[ "$defaults_file" =~ ^/tmp/academic-profile-restore-client\.[A-Za-z0-9]+$ ]] || return 1
  printf -v "$user_var" '%s' "$username"
  printf -v "$defaults_var" '%s' "$defaults_file"
  password=""
}

drop_restore_account() {
  local username="${1:-}" defaults_file="${2:-}"
  if [[ "$defaults_file" =~ ^/tmp/academic-profile-restore-client\.[A-Za-z0-9]+$ ]]; then
    "${DC[@]}" exec -T mysql rm -f -- "$defaults_file" >/dev/null 2>&1 || true
  fi
  if [[ "$username" =~ ^[a-z0-9_]{1,32}$ ]]; then
    printf "DROP USER IF EXISTS '%s'@'localhost';\n" "$username" \
      | root_sql >/dev/null 2>&1 || true
  fi
}

restricted_import() {
  local defaults_file="$1" database="$2" sql_file="$3"
  [[ "$defaults_file" =~ ^/tmp/academic-profile-restore-client\.[A-Za-z0-9]+$ ]] || return 1
  [[ "$database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || return 1
  "${DC[@]}" exec -T mysql sh -c \
    'exec mysql --defaults-extra-file="$1" --database="$2" --batch' \
    -- "$defaults_file" "$database" < "$sql_file"
}

restricted_schema_version() {
  local defaults_file="$1" database="$2"
  printf 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;\n' \
    | "${DC[@]}" exec -T mysql sh -c \
        'exec mysql --defaults-extra-file="$1" --database="$2" --batch --skip-column-names' \
        -- "$defaults_file" "$database" | tr -d '\r'
}

remove_restore_paths_after_success() {
  "${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c '
    set -eu
    restore_id="$1"
    stage="/app/data/.restore-stage-$restore_id"
    rollback="/app/data/.restore-rollback-$restore_id"
    package="/app/data/.restore-package-$restore_id"
    rm -rf -- "$stage" "$rollback" "$package"
  ' -- "$RESTORE_ID" >/dev/null
}

rollback_files() {
  "${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c '
    set -u
    restore_id="$1"
    stage="/app/data/.restore-stage-$restore_id"
    rollback="/app/data/.restore-rollback-$restore_id"
    package="/app/data/.restore-package-$restore_id"
    rollback_ok=true

    if test -f "$rollback/.new-uploads-installed"; then
      rm -rf -- /app/data/uploads || rollback_ok=false
    fi
    if test -f "$rollback/.new-secrets-installed"; then
      rm -rf -- /app/data/secrets || rollback_ok=false
    fi
    if test -f "$rollback/.original-uploads-moved"; then
      if test -d "$rollback/uploads" && test ! -L "$rollback/uploads"; then
        mv "$rollback/uploads" /app/data/uploads || rollback_ok=false
      else
        rollback_ok=false
      fi
    fi
    if test -f "$rollback/.original-secrets-moved"; then
      if test -d "$rollback/secrets" && test ! -L "$rollback/secrets"; then
        mv "$rollback/secrets" /app/data/secrets || rollback_ok=false
      else
        rollback_ok=false
      fi
    fi

    if "$rollback_ok"; then
      rm -rf -- "$stage" "$rollback" "$package"
      exit 0
    fi
    exit 1
  ' -- "$RESTORE_ID" >/dev/null
}

cleanup() {
  local exit_code=$?
  set +e
  drop_restore_account "$VERIFY_USER" "$VERIFY_DEFAULTS"
  drop_restore_account "$TARGET_USER" "$TARGET_DEFAULTS"
  if [[ -n "$TEMP_DB" && "$TEMP_DB" =~ ^[A-Za-z0-9_]{1,64}$ ]]; then
    drop_database "$TEMP_DB" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$WORK"
  exit "$exit_code"
}
trap cleanup EXIT

rollback_restore() {
  local rollback_ok=true
  set +e
  rollback_files || rollback_ok=false
  if "$DB_SWITCHED"; then
    recreate_database "$DB_NAME" || rollback_ok=false
    if [[ -n "$SAFETY_DIR" && -s "$SAFETY_DIR/database.sql" ]]; then
      restricted_import "$TARGET_DEFAULTS" "$DB_NAME" "$SAFETY_DIR/database.sql" \
        >/dev/null 2>&1 || rollback_ok=false
    fi
  fi
  if ! "$rollback_ok"; then
    echo "Restore rollback requires manual recovery from the restricted safety directory." >&2
  fi
}

on_error() {
  local exit_code=$?
  trap - ERR
  echo "Restore failed during phase: $RESTORE_PHASE" >&2
  rollback_restore
  exit "$exit_code"
}
trap on_error ERR

python3 "$SCRIPT_DIR/verify-backup.py" "$BACKUP" --max-schema "$MAX_SCHEMA" --extract "$EXTRACT"

"${DC[@]}" config --quiet
"${DC[@]}" up -d mysql

MYSQL_ID="$("${DC[@]}" ps -q mysql | tr -d '\r')"
[[ -n "$MYSQL_ID" ]] \
  || { echo "Restore failed: target MySQL container is unavailable" >&2; exit 3; }
MYSQL_DEADLINE=$((SECONDS + 180))
while true; do
  MYSQL_HEALTH="$(docker inspect --format \
    '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$MYSQL_ID")"
  MYSQL_HEALTH="${MYSQL_HEALTH//$'\r'/}"
  [[ "$MYSQL_HEALTH" == "healthy" ]] && break
  [[ "$MYSQL_HEALTH" != "unhealthy" && $SECONDS -lt $MYSQL_DEADLINE ]] \
    || { echo "Restore failed: target MySQL did not become healthy" >&2; exit 3; }
  sleep 3
done
DB_NAME="$("${DC[@]}" exec -T mysql sh -c 'printf "%s" "$MYSQL_DATABASE"' | tr -d '\r')"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]{1,32}$ ]] \
  || { echo "Restore failed: unsafe target database name" >&2; exit 3; }
TEMP_DB="${DB_NAME}_restore_${RESTORE_ID//-/_}"

MYSQL_VOLUME="${PROJECT}_mysql-data"
BACKEND_VOLUME="${PROJECT}_backend-data"
check_volume() {
  local volume="$1" expected="$2" labels
  labels="$(docker volume inspect "$volume" \
    --format '{{ index .Labels "com.docker.compose.project" }}|{{ index .Labels "com.docker.compose.volume" }}' \
    2>/dev/null | tr -d '\r')" \
    || { echo "Restore failed: expected target volume is missing" >&2; exit 3; }
  [[ "$labels" == "$PROJECT|$expected" ]] \
    || { echo "Restore failed: volume labels do not match the target project" >&2; exit 3; }
}
check_volume "$MYSQL_VOLUME" "mysql-data"

"${DC[@]}" create backend >/dev/null
check_volume "$BACKEND_VOLUME" "backend-data"

TABLE_COUNT="$(
  printf 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();\n' \
    | "${DC[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names' \
    | tr -d '\r'
)"
FILE_COUNT="$("${DC[@]}" run --rm --no-deps --entrypoint sh backend -c \
  'for path in /app/data/uploads /app/data/secrets; do
     test ! -d "$path" || find "$path" -type f -print -quit
   done | head -n 1 | wc -l' | tr -d '\r')"
TARGET_HAS_DATA=false
if [[ "${TABLE_COUNT:-0}" != "0" || "${FILE_COUNT:-0}" != "0" ]]; then
  TARGET_HAS_DATA=true
fi

if "$TARGET_HAS_DATA" && ! "$FORCE"; then
  echo "Restore refused: target contains data; use --force only after reviewing the target" >&2
  exit 4
fi
if "$TARGET_HAS_DATA"; then
  RESTORE_PHASE="safety_backup"
  EXPECTED="RESTORE $PROJECT"
  if [[ -n "$TEST_CONFIRMATION" ]]; then
    CONFIRM="$TEST_CONFIRMATION"
  else
    read -r -p "Type '$EXPECTED' to overwrite the target: " CONFIRM </dev/tty
  fi
  [[ "$CONFIRM" == "$EXPECTED" ]] \
    || { echo "Restore cancelled: confirmation did not match" >&2; exit 4; }
  SAFETY_DIR="$PROJECT_DIR/restore-safety/$RESTORE_ID"
  mkdir -p -- "$SAFETY_DIR"
  chmod 700 "$PROJECT_DIR/restore-safety" "$SAFETY_DIR" 2>/dev/null || true
  RESTORE_PHASE="safety_database_dump"
  "${DC[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" \
      --single-transaction --quick --hex-blob --no-tablespaces "$MYSQL_DATABASE"' \
    > "$SAFETY_DIR/database.sql"
  RESTORE_PHASE="safety_file_snapshot"
  "${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c \
    'set --; test ! -e /app/data/uploads || set -- "$@" uploads
     test ! -e /app/data/secrets || set -- "$@" secrets
     tar -C /app/data -cf - --files-from /dev/null "$@"' \
    > "$SAFETY_DIR/backend-data.tar"
  echo "Safety backup created in a restricted target-server directory."
fi

RESTORE_PHASE="temporary_database_validation"
create_database "$TEMP_DB"
create_restore_account "$TEMP_DB" "rsv" VERIFY_USER VERIFY_DEFAULTS
restricted_import "$VERIFY_DEFAULTS" "$TEMP_DB" "$EXTRACT/database.sql"
VERIFIED_SCHEMA="$(restricted_schema_version "$VERIFY_DEFAULTS" "$TEMP_DB")"
[[ "$VERIFIED_SCHEMA" =~ ^[0-9]+$ && "$VERIFIED_SCHEMA" -le "$MAX_SCHEMA" ]] \
  || { echo "Restore failed: temporary database schema validation failed" >&2; false; }
drop_restore_account "$VERIFY_USER" "$VERIFY_DEFAULTS"
VERIFY_USER=""
VERIFY_DEFAULTS=""
drop_database "$TEMP_DB"
TEMP_DB=""

RESTORE_PHASE="file_staging"
"${DC[@]}" stop backend frontend >/dev/null 2>&1 || true

BACKEND_CONTAINER="$("${DC[@]}" ps -q --all backend | tr -d '\r')"
[[ -n "$BACKEND_CONTAINER" ]] \
  || { echo "Restore failed: backend staging container is unavailable" >&2; false; }
"${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c '
  set -eu
  package="/app/data/.restore-package-$1"
  test ! -e "$package"
' -- "$RESTORE_ID"
docker cp "$EXTRACT/." "$BACKEND_CONTAINER:/app/data/.restore-package-$RESTORE_ID"

"${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c '
    set -eu
    restore_id="$1"
    stage="/app/data/.restore-stage-$restore_id"
    rollback="/app/data/.restore-rollback-$restore_id"
    package="/app/data/.restore-package-$restore_id"
    test -d "$package" || {
      echo "Restore failed: verified package staging is unavailable" >&2
      exit 1
    }
    test ! -e "$stage" && test ! -e "$rollback" || {
      echo "Restore failed: staging directory already exists" >&2
      exit 1
    }
    mkdir -p "$stage/uploads" "$stage/secrets" "$rollback"
    if test -d "$package/uploads" && test ! -L "$package/uploads"; then
      cp -a "$package/uploads/." "$stage/uploads/"
    fi
    test -f "$package/secrets/ai-config.key" \
      && test ! -L "$package/secrets/ai-config.key" || {
      echo "Restore failed: staged encryption key is invalid" >&2
      exit 1
    }
    cp "$package/secrets/ai-config.key" "$stage/secrets/ai-config.key"
    chmod 600 "$stage/secrets/ai-config.key"
    chown -R app:app "$stage"
    rm -rf -- "$package"
  ' -- "$RESTORE_ID"

RESTORE_PHASE="target_database_import"
create_restore_account "$DB_NAME" "rst" TARGET_USER TARGET_DEFAULTS
DB_SWITCHED=true
recreate_database "$DB_NAME"
restricted_import "$TARGET_DEFAULTS" "$DB_NAME" "$EXTRACT/database.sql"
if [[ "$TEST_FAIL_AT" == "after_database_switch" ]]; then
  false
fi

RESTORE_PHASE="file_switch"
"${DC[@]}" run --rm --no-deps --user root --entrypoint sh backend -c '
  set -eu
  restore_id="$1"
  fail_point="$2"
  stage="/app/data/.restore-stage-$restore_id"
  rollback="/app/data/.restore-rollback-$restore_id"
  package="/app/data/.restore-package-$restore_id"
  original_uploads_moved=false
  original_secrets_moved=false
  new_uploads_installed=false
  new_secrets_installed=false

  fail_if_requested() {
    test "$fail_point" != "$1" || return 97
  }

  rollback_local() {
    exit_code=$?
    set +e
    rollback_ok=true
    if "$new_uploads_installed"; then
      rm -rf -- /app/data/uploads || rollback_ok=false
    fi
    if "$new_secrets_installed"; then
      rm -rf -- /app/data/secrets || rollback_ok=false
    fi
    if "$original_uploads_moved"; then
      if test -d "$rollback/uploads" && test ! -L "$rollback/uploads"; then
        mv "$rollback/uploads" /app/data/uploads || rollback_ok=false
      else
        rollback_ok=false
      fi
    fi
    if "$original_secrets_moved"; then
      if test -d "$rollback/secrets" && test ! -L "$rollback/secrets"; then
        mv "$rollback/secrets" /app/data/secrets || rollback_ok=false
      else
        rollback_ok=false
      fi
    fi
    if "$rollback_ok"; then
      rm -rf -- "$stage" "$rollback" "$package"
    fi
    exit "$exit_code"
  }
  trap rollback_local EXIT

  test -d "$stage/uploads" && test ! -L "$stage/uploads"
  test -d "$stage/secrets" && test ! -L "$stage/secrets"
  test -f "$stage/secrets/ai-config.key" && test ! -L "$stage/secrets/ai-config.key"
  test -d "$rollback" && test ! -L "$rollback"
  test ! -e "$rollback/uploads" && test ! -e "$rollback/secrets"
  test ! -L /app/data/uploads && test ! -L /app/data/secrets
  fail_if_requested before_file_switch

  if test -e /app/data/uploads; then
    mv /app/data/uploads "$rollback/uploads"
    original_uploads_moved=true
    : > "$rollback/.original-uploads-moved"
  fi
  fail_if_requested after_original_uploads_moved

  if test -e /app/data/secrets; then
    mv /app/data/secrets "$rollback/secrets"
    original_secrets_moved=true
    : > "$rollback/.original-secrets-moved"
  fi
  fail_if_requested after_original_secrets_moved

  mv "$stage/uploads" /app/data/uploads
  new_uploads_installed=true
  : > "$rollback/.new-uploads-installed"
  fail_if_requested after_new_uploads_installed

  mv "$stage/secrets" /app/data/secrets
  new_secrets_installed=true
  : > "$rollback/.new-secrets-installed"
  fail_if_requested after_new_secrets_installed

  chmod 600 /app/data/secrets/ai-config.key
  chown -R app:app /app/data/uploads /app/data/secrets
  rmdir "$stage"
  trap - EXIT
' -- "$RESTORE_ID" "$TEST_FAIL_AT"

if [[ "$TEST_FAIL_AT" == "before_health_check" ]]; then
  false
fi

RESTORE_PHASE="service_start"
"${DC[@]}" up -d

RESTORE_PHASE="health_validation"
SERVICES=(mysql backend frontend paddleocr libreoffice)
DEADLINE=$((SECONDS + 600))
for service in "${SERVICES[@]}"; do
  while true; do
    container="$("${DC[@]}" ps -q "$service")"
    container="${container//$'\r'/}"
    [[ -n "$container" ]] \
      || { echo "Restore failed: required service is not running: $service" >&2; false; }
    health="$(docker inspect --format \
      '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
    health="${health//$'\r'/}"
    [[ "$health" == "healthy" ]] && break
    [[ "$health" != "unhealthy" && $SECONDS -lt $DEADLINE ]] \
      || { echo "Restore failed: required service did not become healthy: $service" >&2; false; }
    sleep 5
  done
done

SCHEMA="$(
  printf 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;\n' \
    | "${DC[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
          --batch --skip-column-names' \
    | tr -d '\r'
)"
[[ "$SCHEMA" =~ ^[0-9]+$ && "$SCHEMA" -le "$MAX_SCHEMA" ]] \
  || { echo "Restore failed: restored schema version is invalid" >&2; false; }
"${DC[@]}" run --rm --no-deps --entrypoint sh backend -c \
  'test -f /app/data/secrets/ai-config.key \
    && test ! -L /app/data/secrets/ai-config.key \
    && test -d /app/data/uploads \
    && test ! -L /app/data/uploads'

remove_restore_paths_after_success
DB_SWITCHED=false
drop_restore_account "$TARGET_USER" "$TARGET_DEFAULTS"
TARGET_USER=""
TARGET_DEFAULTS=""
trap cleanup EXIT
echo "Restore completed and all five services are healthy."
