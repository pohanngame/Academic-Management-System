#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
OPERATIONS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
PROJECT_ROOT="$(cd -- "$OPERATIONS_DIR/.." && pwd -P)"
RUNTIME_PARENT="${RESTORE_TEST_RUNTIME_ROOT:-${TMPDIR:-/tmp}}"
[[ -d "$RUNTIME_PARENT" ]] || mkdir -p "$RUNTIME_PARENT"
RUNTIME="$(mktemp -d "$RUNTIME_PARENT/restore-docker-test.XXXXXX")"
SOURCE_DIR="$RUNTIME/source"
TARGET_DIR="$RUNTIME/target"
mkdir -p "$SOURCE_DIR" "$TARGET_DIR" "$RUNTIME/tmp"
cp "$SCRIPT_DIR/restore-test-compose.yml" "$SOURCE_DIR/docker-compose.yml"
cp "$SCRIPT_DIR/restore-test-compose.yml" "$TARGET_DIR/docker-compose.yml"

RUN_ID="$(date -u +%m%d%H%M)-$$"
SOURCE_PROJECT="restore-test-src-$RUN_ID"
TARGET_PROJECT="restore-test-target-$RUN_ID"
[[ "$SOURCE_PROJECT" =~ ^restore-test-[a-z0-9_-]+$ ]]
[[ "$TARGET_PROJECT" =~ ^restore-test-[a-z0-9_-]+$ ]]

MYSQL_DATABASE="academic_profile"
MYSQL_USER="rtapp_$(openssl rand -hex 5)"
MYSQL_PASSWORD="$(openssl rand -hex 24)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
SOURCE_JWT="$(openssl rand -hex 32)"
TARGET_JWT="$(openssl rand -hex 32)"
ADMIN_PASSWORD="$(openssl rand -hex 16)"
TEACHER_PASSWORD="$(openssl rand -hex 16)"
AI_TEST_KEY="$(openssl rand -hex 24)"
PORT_BASE=$((42000 + ($$ % 500) * 10))
SOURCE_MYSQL_PORT=$PORT_BASE
SOURCE_FRONTEND_PORT=$((PORT_BASE + 1))
TARGET_MYSQL_PORT=$((PORT_BASE + 2))
TARGET_FRONTEND_PORT=$((PORT_BASE + 3))

source_dc() {
  MYSQL_DATABASE="$MYSQL_DATABASE" MYSQL_USER="$MYSQL_USER" \
  MYSQL_PASSWORD="$MYSQL_PASSWORD" MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  APP_JWT_SECRET="$SOURCE_JWT" MYSQL_PORT="$SOURCE_MYSQL_PORT" \
  FRONTEND_PORT="$SOURCE_FRONTEND_PORT" \
  docker compose --project-name "$SOURCE_PROJECT" --project-directory "$SOURCE_DIR" \
    --profile full "$@"
}

target_dc() {
  MYSQL_DATABASE="$MYSQL_DATABASE" MYSQL_USER="$MYSQL_USER" \
  MYSQL_PASSWORD="$MYSQL_PASSWORD" MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  APP_JWT_SECRET="$TARGET_JWT" MYSQL_PORT="$TARGET_MYSQL_PORT" \
  FRONTEND_PORT="$TARGET_FRONTEND_PORT" \
  docker compose --project-name "$TARGET_PROJECT" --project-directory "$TARGET_DIR" \
    --profile full "$@"
}

remove_project() {
  local project="$1" id label volume network
  local -a ids volumes networks
  mapfile -t ids < <(docker ps -aq --filter "label=com.docker.compose.project=$project")
  for id in "${ids[@]}"; do
    id="${id//$'\r'/}"
    [[ -n "$id" ]] || continue
    label="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$id")"
    label="${label//$'\r'/}"
    [[ "$label" == "$project" ]] || {
      echo "Refusing to remove a container outside the test project" >&2
      return 1
    }
    docker rm -f "$id" </dev/null >/dev/null
  done

  mapfile -t volumes < <(docker volume ls -q --filter "label=com.docker.compose.project=$project")
  for volume in "${volumes[@]}"; do
    volume="${volume//$'\r'/}"
    [[ -n "$volume" ]] || continue
    label="$(docker volume inspect --format '{{ index .Labels "com.docker.compose.project" }}' "$volume")"
    label="${label//$'\r'/}"
    [[ "$label" == "$project" ]] || {
      echo "Refusing to remove a volume outside the test project" >&2
      return 1
    }
    docker volume rm "$volume" </dev/null >/dev/null
  done

  mapfile -t networks < <(docker network ls -q --filter "label=com.docker.compose.project=$project")
  for network in "${networks[@]}"; do
    network="${network//$'\r'/}"
    [[ -n "$network" ]] || continue
    label="$(docker network inspect --format '{{ index .Labels "com.docker.compose.project" }}' "$network")"
    label="${label//$'\r'/}"
    [[ "$label" == "$project" ]] || {
      echo "Refusing to remove a network outside the test project" >&2
      return 1
    }
    docker network rm "$network" </dev/null >/dev/null
  done
}

cleanup() {
  local exit_code=$?
  set +e
  remove_project "$SOURCE_PROJECT"
  remove_project "$TARGET_PROJECT"
  if [[ "$RUNTIME" == "$RUNTIME_PARENT"/restore-docker-test.* ]]; then
    rm -rf -- "$RUNTIME"
  fi
  exit "$exit_code"
}
trap cleanup EXIT

TEST_STAGE="initialization"
report_error() {
  local exit_code=$?
  local failed_line="${BASH_LINENO[0]:-unknown}"
  echo "linux_docker_restore_acceptance=failed stage=$TEST_STAGE line=$failed_line exit=$exit_code" >&2
  exit "$exit_code"
}
trap report_error ERR

wait_healthy() {
  local side="$1"
  shift
  local deadline=$((SECONDS + 1800)) service container state_health container_state health
  for service in "$@"; do
    while true; do
      if [[ "$side" == "source" ]]; then
        container="$(source_dc ps -q "$service")"
      else
        container="$(target_dc ps -q "$service")"
      fi
      container="${container//$'\r'/}"
      if [[ -n "$container" ]]; then
        state_health="$(docker inspect --format \
          '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
          "$container")"
        state_health="${state_health//$'\r'/}"
        container_state="${state_health%%|*}"
        health="${state_health#*|}"
        [[ "$health" == "healthy" ]] && break
        [[ "$container_state" == "running" || "$container_state" == "restarting" ]] || {
          echo "Test service stopped before becoming healthy" >&2
          return 1
        }
      fi
      [[ $SECONDS -lt $deadline ]] || {
        echo "Timed out waiting for an isolated test service" >&2
        return 1
      }
      sleep 5
    done
  done
}

source_api() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-fsS -X "$method" -H "Content-Type: application/json")
  [[ -z "$token" ]] || args+=(-H "Authorization: Bearer $token")
  printf '%s' "$body" \
    | source_dc exec -T backend curl "${args[@]}" --data-binary @- \
        "http://127.0.0.1:8080$path"
}

target_api() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-fsS -X "$method" -H "Content-Type: application/json")
  [[ -z "$token" ]] || args+=(-H "Authorization: Bearer $token")
  printf '%s' "$body" \
    | target_dc exec -T backend curl "${args[@]}" --data-binary @- \
        "http://127.0.0.1:8080$path"
}

source_app_sql() {
  source_dc exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
      --batch --skip-column-names' | tr -d '\r'
}

target_app_sql() {
  target_dc exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
      --batch --skip-column-names' | tr -d '\r'
}

target_root_sql() {
  target_dc exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names' \
    | tr -d '\r'
}

json_token() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])'
}

tree_hash() {
  local side="$1"
  if [[ "$side" == "source" ]]; then
    source_dc run --rm --no-deps --entrypoint sh backend -c '
      cd /app/data
      find uploads -type f -print0 | sort -z \
        | xargs -0 sha256sum | sha256sum | cut -d" " -f1
    ' | tr -d '\r'
  else
    target_dc run --rm --no-deps --entrypoint sh backend -c '
      cd /app/data
      find uploads -type f -print0 | sort -z \
        | xargs -0 sha256sum | sha256sum | cut -d" " -f1
    ' | tr -d '\r'
  fi
}

temporary_account_count() {
  printf "SELECT COUNT(*) FROM mysql.user WHERE User REGEXP '^(rsv|rst)_[0-9a-f]+$';\n" \
    | target_root_sql
}

credential_file_count() {
  target_dc exec -T mysql sh -c \
    'find /tmp -maxdepth 1 -name "academic-profile-restore-client.*" -type f | wc -l' \
    | tr -d '\r'
}

restore_residue_count() {
  target_dc run --rm --no-deps --entrypoint sh backend -c \
    'find /app/data -maxdepth 1 \
      \( -name ".restore-stage-*" -o -name ".restore-rollback-*" \
         -o -name ".restore-package-*" \) | wc -l' \
    | tr -d '\r'
}

export MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD

TEST_STAGE="source_mysql"
source_dc up -d mysql >/dev/null
wait_healthy source mysql
TEST_STAGE="source_bootstrap"
source_dc run --rm --no-deps \
  -e APP_ADMIN_BOOTSTRAP_ENABLED=true \
  -e APP_ADMIN_BOOTSTRAP_ACTION=create \
  -e APP_ADMIN_BOOTSTRAP_USERNAME=restore-admin \
  -e APP_ADMIN_BOOTSTRAP_EMAIL=restore-admin@example.test \
  -e APP_ADMIN_BOOTSTRAP_PASSWORD="$ADMIN_PASSWORD" \
  -e APP_ADMIN_BOOTSTRAP_DISPLAY_NAME="Restore Administrator" \
  backend >/dev/null
source_dc up -d --no-deps backend >/dev/null
wait_healthy source backend

TEST_STAGE="source_test_data"
REGISTER_A="$(printf \
  '{"username":"restore-teacher-a","email":"restore-a@example.test","password":"%s","confirmPassword":"%s","displayName":"Restore Teacher A","department":"Test","title":"Teacher"}' \
  "$TEACHER_PASSWORD" "$TEACHER_PASSWORD")"
REGISTER_B="$(printf \
  '{"username":"restore-teacher-b","email":"restore-b@example.test","password":"%s","confirmPassword":"%s","displayName":"Restore Teacher B","department":"Test","title":"Teacher"}' \
  "$TEACHER_PASSWORD" "$TEACHER_PASSWORD")"
TEST_STAGE="source_register_a"
source_api POST /api/auth/register "" "$REGISTER_A" >/dev/null
TEST_STAGE="source_register_b"
source_api POST /api/auth/register "" "$REGISTER_B" >/dev/null

TEST_STAGE="source_login_admin"
ADMIN_TOKEN="$(
  source_api POST /api/auth/login "" \
    "$(printf '{"usernameOrEmail":"restore-admin","password":"%s"}' "$ADMIN_PASSWORD")" \
    | json_token
)"
TEST_STAGE="source_login_teacher_a"
TEACHER_A_TOKEN="$(
  source_api POST /api/auth/login "" \
    "$(printf '{"usernameOrEmail":"restore-teacher-a","password":"%s"}' "$TEACHER_PASSWORD")" \
    | json_token
)"

TEST_STAGE="source_ai_settings"
source_api PUT /api/ai/settings "$TEACHER_A_TOKEN" \
  "$(printf \
    '{"providerName":"restore-test","baseUrl":"https://api.deepseek.com","modelName":"restore-test-model","apiKey":"%s"}' \
    "$AI_TEST_KEY")" >/dev/null

printf '%s' \
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' \
  | source_dc exec -T backend sh -c 'base64 -d > /tmp/restore-test-avatar.png'
TEST_STAGE="source_upload"
UPLOAD_RESPONSE="$(
  source_dc exec -T backend curl -fsS \
    -H "Authorization: Bearer $TEACHER_A_TOKEN" \
    -F file=@/tmp/restore-test-avatar.png \
    http://127.0.0.1:8080/api/teacher/profile/avatar
)"
UPLOAD_ID="$(printf '%s' "$UPLOAD_RESPONSE" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')"
[[ "$UPLOAD_ID" =~ ^[0-9]+$ ]]

TEACHER_A_ID="$(
  printf "SELECT tp.id FROM teacher_profile tp JOIN user u ON u.id=tp.user_id WHERE u.username='restore-teacher-a';\n" \
    | source_app_sql
)"
[[ "$TEACHER_A_ID" =~ ^[0-9]+$ ]]
{
  printf "INSERT INTO project(teacher_id,project_name) VALUES (%s,'restore project');\n" \
    "$TEACHER_A_ID"
  printf "INSERT INTO ai_generation_task"
  printf "(teacher_id,template_requirement,selected_modules,provider,model_name,status)"
  printf " VALUES (%s,'restore history',JSON_ARRAY('PROFILE'),'restore-test','restore-test-model','FAILED');\n" \
    "$TEACHER_A_ID"
} | source_app_sql >/dev/null

BACKUP="$RUNTIME/source.apbackup"
TEST_STAGE="source_backup"
printf '{"currentPassword":"%s"}' "$ADMIN_PASSWORD" \
  | source_dc exec -T backend curl -fsS \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      -o /tmp/source.apbackup \
      http://127.0.0.1:8080/api/admin/backups/export
source_dc exec -T backend cat /tmp/source.apbackup > "$BACKUP"
python3 "$OPERATIONS_DIR/verify-backup.py" "$BACKUP" --max-schema 15 >/dev/null

SOURCE_COUNTS="$(
  printf "SELECT (SELECT COUNT(*) FROM user),(SELECT COUNT(*) FROM teacher_profile),(SELECT COUNT(*) FROM project),(SELECT COUNT(*) FROM file_metadata),(SELECT COUNT(*) FROM ai_provider_config),(SELECT COUNT(*) FROM ai_generation_task);\n" \
    | source_app_sql
)"
SOURCE_UPLOAD_HASH="$(tree_hash source)"
SOURCE_KEY_HASH="$(
  source_dc run --rm --no-deps --entrypoint sh backend -c \
    'sha256sum /app/data/secrets/ai-config.key | cut -d" " -f1'
)"
SOURCE_CIPHER_HASH="$(
  printf "SELECT SHA2(encrypted_api_key,256) FROM ai_provider_config LIMIT 1;\n" \
    | source_app_sql
)"

export APP_JWT_SECRET="$TARGET_JWT"
export MYSQL_PORT="$TARGET_MYSQL_PORT"
export FRONTEND_PORT="$TARGET_FRONTEND_PORT"
export RESTORE_TEST_RUNTIME_ROOT="$RUNTIME/tmp"
export TMPDIR="$RUNTIME/tmp"

TEST_STAGE="empty_restore"
"$OPERATIONS_DIR/restore-backup.sh" \
  --backup "$BACKUP" --project-dir "$TARGET_DIR" --project-name "$TARGET_PROJECT" \
  >/dev/null
wait_healthy target mysql backend frontend paddleocr libreoffice

TARGET_COUNTS="$(
  printf "SELECT (SELECT COUNT(*) FROM user),(SELECT COUNT(*) FROM teacher_profile),(SELECT COUNT(*) FROM project),(SELECT COUNT(*) FROM file_metadata),(SELECT COUNT(*) FROM ai_provider_config),(SELECT COUNT(*) FROM ai_generation_task);\n" \
    | target_app_sql
)"
[[ "$TARGET_COUNTS" == "$SOURCE_COUNTS" ]]
[[ "$(tree_hash target)" == "$SOURCE_UPLOAD_HASH" ]]
TARGET_KEY_HASH="$(
  target_dc run --rm --no-deps --entrypoint sh backend -c \
    'sha256sum /app/data/secrets/ai-config.key | cut -d" " -f1'
)"
[[ "$TARGET_KEY_HASH" == "$SOURCE_KEY_HASH" ]]
TARGET_CIPHER_HASH="$(
  printf "SELECT SHA2(encrypted_api_key,256) FROM ai_provider_config LIMIT 1;\n" \
    | target_app_sql
)"
[[ "$TARGET_CIPHER_HASH" == "$SOURCE_CIPHER_HASH" ]]

TARGET_ADMIN_TOKEN="$(
  target_api POST /api/auth/login "" \
    "$(printf '{"usernameOrEmail":"restore-admin","password":"%s"}' "$ADMIN_PASSWORD")" \
    | json_token
)"
TARGET_TEACHER_B_TOKEN="$(
  target_api POST /api/auth/login "" \
    "$(printf '{"usernameOrEmail":"restore-teacher-b","password":"%s"}' "$TEACHER_PASSWORD")" \
    | json_token
)"
TARGET_ROLE="$(
  target_api GET /api/auth/me "$TARGET_ADMIN_TOKEN" '{}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["role"])'
)"
[[ "$TARGET_ROLE" == "SUPER_ADMIN" ]]
SOURCE_TOKEN_STATUS="$(
  target_dc exec -T backend curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    http://127.0.0.1:8080/api/auth/me
)"
[[ "$SOURCE_TOKEN_STATUS" == "401" ]]
ISOLATION_STATUS="$(
  target_dc exec -T backend curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $TARGET_TEACHER_B_TOKEN" \
    "http://127.0.0.1:8080/api/files/$UPLOAD_ID/download"
)"
[[ "$ISOLATION_STATUS" == "404" ]] || {
  echo "Unexpected cross-teacher download status: $ISOLATION_STATUS" >&2
  false
}
echo "empty_restore=passed counts=$TARGET_COUNTS services_healthy=5"

TEST_STAGE="nonempty_rejection"
BEFORE_REJECT_COUNTS="$TARGET_COUNTS"
if "$OPERATIONS_DIR/restore-backup.sh" \
    --backup "$BACKUP" --project-dir "$TARGET_DIR" --project-name "$TARGET_PROJECT" \
    >/dev/null 2>&1; then
  REJECT_EXIT=0
else
  REJECT_EXIT=$?
fi
[[ $REJECT_EXIT -eq 4 ]]
AFTER_REJECT_COUNTS="$(
  printf "SELECT (SELECT COUNT(*) FROM user),(SELECT COUNT(*) FROM teacher_profile),(SELECT COUNT(*) FROM project),(SELECT COUNT(*) FROM file_metadata),(SELECT COUNT(*) FROM ai_provider_config),(SELECT COUNT(*) FROM ai_generation_task);\n" \
    | target_app_sql
)"
[[ "$AFTER_REJECT_COUNTS" == "$BEFORE_REJECT_COUNTS" ]]
echo "nonempty_without_force=passed exit=$REJECT_EXIT"

TEST_STAGE="force_restore"
RESTORE_TEST_CONFIRMATION="RESTORE $TARGET_PROJECT" \
  "$OPERATIONS_DIR/restore-backup.sh" \
    --backup "$BACKUP" --project-dir "$TARGET_DIR" \
    --project-name "$TARGET_PROJECT" --force >/dev/null
wait_healthy target mysql backend frontend paddleocr libreoffice
echo "force_restore=passed services_healthy=5"

TEST_STAGE="malicious_sql"
printf '%s\n' \
  "CREATE DATABASE decoy;" \
  "CREATE TABLE decoy.sentinel(id INT PRIMARY KEY, marker VARCHAR(32));" \
  "INSERT INTO decoy.sentinel VALUES (1,'preserve-me');" \
  "CREATE USER 'restore_control_user'@'localhost' IDENTIFIED BY 'runtime-control-only';" \
  | target_root_sql >/dev/null

MALICIOUS="$RUNTIME/malicious.apbackup"
python3 - "$BACKUP" "$MALICIOUS" <<'PY'
import hashlib
import json
import sys
import zipfile

source, target = sys.argv[1:]
payload = (
    b"\nUSE decoy;\n"
    b"DROP TABLE decoy.sentinel;\n"
    b"DROP DATABASE decoy;\n"
    b"CREATE USER 'restore_evil_user'@'%' IDENTIFIED BY 'runtime-only';\n"
    b"GRANT ALL PRIVILEGES ON *.* TO 'restore_evil_user'@'%' WITH GRANT OPTION;\n"
)
with zipfile.ZipFile(source) as current:
    database = current.read("database.sql") + payload
    manifest = json.loads(current.read("manifest.json"))
    manifest["files"]["database.sql"] = {
        "size": len(database),
        "sha256": hashlib.sha256(database).hexdigest(),
    }
    with zipfile.ZipFile(target, "w") as output:
        output.writestr("manifest.json", json.dumps(manifest))
        for info in current.infolist():
            if info.filename == "manifest.json":
                continue
            output.writestr(
                info,
                database if info.filename == "database.sql" else current.read(info),
            )
PY
python3 "$OPERATIONS_DIR/verify-backup.py" "$MALICIOUS" --max-schema 15 >/dev/null

if RESTORE_TEST_CONFIRMATION="RESTORE $TARGET_PROJECT" \
    "$OPERATIONS_DIR/restore-backup.sh" \
      --backup "$MALICIOUS" --project-dir "$TARGET_DIR" \
      --project-name "$TARGET_PROJECT" --force >/dev/null 2>&1; then
  MALICIOUS_EXIT=0
else
  MALICIOUS_EXIT=$?
fi
[[ $MALICIOUS_EXIT -ne 0 ]]
[[ "$(printf 'SELECT COUNT(*) FROM decoy.sentinel WHERE marker='\''preserve-me'\'';\n' \
  | target_root_sql)" == "1" ]]
[[ "$(printf "SELECT COUNT(*) FROM mysql.user WHERE User='restore_evil_user';\n" \
  | target_root_sql)" == "0" ]]
[[ "$(printf "SELECT COUNT(*) FROM mysql.user WHERE User='restore_control_user';\n" \
  | target_root_sql)" == "1" ]]
[[ "$(temporary_account_count)" == "0" ]]
[[ "$(credential_file_count)" == "0" ]]
echo "malicious_sql=blocked exit=$MALICIOUS_EXIT decoy_sentinel=1 evil_user=0 temp_users=0"

printf 'CREATE TABLE IF NOT EXISTS restore_sentinel(id INT PRIMARY KEY, marker VARCHAR(32));\n' \
  | target_app_sql >/dev/null
printf "REPLACE INTO restore_sentinel VALUES (1,'original-target');\n" \
  | target_app_sql >/dev/null
target_dc run --rm --no-deps --user root --entrypoint sh backend -c '
  set -eu
  rm -rf /app/data/uploads /app/data/secrets
  mkdir -p /app/data/uploads/original-a /app/data/uploads/original-b /app/data/secrets
  printf "original attachment a\n" > /app/data/uploads/original-a/a.txt
  printf "original attachment b\n" > /app/data/uploads/original-b/b.txt
  head -c 32 /dev/urandom > /app/data/secrets/ai-config.key
  chmod 600 /app/data/secrets/ai-config.key
  chown -R app:app /app/data/uploads /app/data/secrets
'
ORIGINAL_UPLOAD_HASH="$(tree_hash target)"
ORIGINAL_KEY_HASH="$(
  target_dc run --rm --no-deps --entrypoint sh backend -c \
    'sha256sum /app/data/secrets/ai-config.key | cut -d" " -f1'
)"
ORIGINAL_KEY_MODE="$(
  target_dc run --rm --no-deps --entrypoint sh backend -c \
    'stat -c "%a" /app/data/secrets/ai-config.key'
)"
[[ "$ORIGINAL_KEY_MODE" == "600" ]]

FAULT_POINTS=(
  before_file_switch
  after_original_uploads_moved
  after_original_secrets_moved
  after_new_uploads_installed
  after_new_secrets_installed
  after_database_switch
  before_health_check
)
for point in "${FAULT_POINTS[@]}"; do
  TEST_STAGE="fault_$point"
  if RESTORE_TEST_CONFIRMATION="RESTORE $TARGET_PROJECT" \
      RESTORE_TEST_FAIL_AT="$point" "$OPERATIONS_DIR/restore-backup.sh" \
        --backup "$BACKUP" --project-dir "$TARGET_DIR" \
        --project-name "$TARGET_PROJECT" --force >/dev/null 2>&1; then
    FAULT_EXIT=0
  else
    FAULT_EXIT=$?
  fi
  [[ $FAULT_EXIT -ne 0 ]]

  target_dc up -d >/dev/null
  wait_healthy target mysql backend frontend paddleocr libreoffice
  [[ "$(printf "SELECT COUNT(*) FROM restore_sentinel WHERE marker='original-target';\n" \
    | target_app_sql)" == "1" ]]
  [[ "$(tree_hash target)" == "$ORIGINAL_UPLOAD_HASH" ]]
  [[ "$(
    target_dc run --rm --no-deps --entrypoint sh backend -c \
      'sha256sum /app/data/secrets/ai-config.key | cut -d" " -f1'
  )" == "$ORIGINAL_KEY_HASH" ]]
  [[ "$(
    target_dc run --rm --no-deps --entrypoint sh backend -c \
      'stat -c "%a" /app/data/secrets/ai-config.key'
  )" == "$ORIGINAL_KEY_MODE" ]]
  [[ "$(temporary_account_count)" == "0" ]]
  [[ "$(credential_file_count)" == "0" ]]
  [[ "$(restore_residue_count)" == "0" ]]
  echo "fault_$point=passed exit=$FAULT_EXIT sentinel=1 temp_users=0 residue=0 services_healthy=5"
done

FINAL_SOURCE_COUNTS="$(
  printf "SELECT (SELECT COUNT(*) FROM user),(SELECT COUNT(*) FROM teacher_profile),(SELECT COUNT(*) FROM project),(SELECT COUNT(*) FROM file_metadata),(SELECT COUNT(*) FROM ai_provider_config),(SELECT COUNT(*) FROM ai_generation_task);\n" \
    | source_app_sql
)"
[[ "$FINAL_SOURCE_COUNTS" == "$SOURCE_COUNTS" ]]
[[ "$(tree_hash source)" == "$SOURCE_UPLOAD_HASH" ]]
[[ "$(
  source_dc run --rm --no-deps --entrypoint sh backend -c \
    'sha256sum /app/data/secrets/ai-config.key | cut -d" " -f1'
)" == "$SOURCE_KEY_HASH" ]]
echo "source_unchanged=passed counts=$FINAL_SOURCE_COUNTS"
echo "linux_docker_restore_acceptance=passed"
