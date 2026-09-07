# SSH helpers for CodeArts -> ECS. Source this file; do not execute it.

# CodeArts string parameters often collapse PEM newlines into spaces or nothing.
# Rebuild a canonical OpenSSH private key file before ssh(1) reads it.
ecs_write_private_key() {
  local dest="$1"
  local raw="$2"
  local body decoded i len

  raw="${raw#"${raw%%[![:space:]]*}"}"
  raw="${raw%"${raw##*[![:space:]]}"}"
  raw="${raw//$'\r'/}"
  raw="${raw//\\n/$'\n'}"
  if [[ "${raw:0:1}" == '"' && "${raw: -1}" == '"' ]]; then
    raw="${raw:1:-1}"
  fi

  if [[ "$raw" == *"BEGIN OPENSSH PRIVATE KEY"* ]]; then
    body="${raw#*-----BEGIN OPENSSH PRIVATE KEY-----}"
    body="${body%-----END OPENSSH PRIVATE KEY-----*}"
  else
    body="$raw"
  fi
  body="$(printf '%s' "$body" | tr -d '[:space:]')"

  if [[ "$body" == LS0tLS1CRUdJTi* ]] && command -v base64 >/dev/null 2>&1; then
    decoded="$(printf '%s' "$body" | base64 -d 2>/dev/null || true)"
    if [[ "$decoded" == *"BEGIN OPENSSH PRIVATE KEY"* ]]; then
      ecs_write_private_key "$dest" "$decoded"
      return
    fi
  fi

  if [[ "$body" != b3BlbnNzaC1rZXktdjE* ]]; then
    echo "ECS_SSH_PRIVATE_KEY is not an OpenSSH private key" >&2
    return 2
  fi

  {
    printf '%s\n' '-----BEGIN OPENSSH PRIVATE KEY-----'
    i=0
    len=${#body}
    while (( i < len )); do
      printf '%s\n' "${body:i:70}"
      i=$((i + 70))
    done
    printf '%s\n' '-----END OPENSSH PRIVATE KEY-----'
  } > "$dest"
}

ecs_ssh_setup() {
  ecs_host="${ECS_HOST:?ECS_HOST is required}"
  ecs_user="${ECS_USER:-root}"
  ecs_host_key="${ECS_HOST_KEY:?ECS_HOST_KEY is required}"

  [[ "$ecs_host" =~ ^[a-zA-Z0-9.:-]+$ ]] || { echo "Invalid ECS_HOST" >&2; return 2; }
  [[ "$ecs_user" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo "Invalid ECS_USER" >&2; return 2; }

  SSH_DIR="$(mktemp -d)"
  key_file="${ECS_SSH_KEY_FILE:-$SSH_DIR/id_deploy}"

  cleanup_ecs_ssh() {
    if [[ -z "${ECS_SSH_KEY_FILE:-}" ]]; then
      rm -f -- "$key_file"
    fi
    rm -rf -- "$SSH_DIR"
  }
  trap cleanup_ecs_ssh EXIT

  mkdir -p "$SSH_DIR"
  chmod 700 "$SSH_DIR"

  if [[ -z "${ECS_SSH_KEY_FILE:-}" ]]; then
    [[ -n "${ECS_SSH_PRIVATE_KEY:-}" ]] || { echo "ECS_SSH_PRIVATE_KEY or ECS_SSH_KEY_FILE is required" >&2; return 2; }
    ecs_write_private_key "$key_file" "$ECS_SSH_PRIVATE_KEY"
  fi
  chmod 600 "$key_file"
  printf '%s\n' "$ecs_host_key" > "$SSH_DIR/known_hosts"
  chmod 600 "$SSH_DIR/known_hosts"

  SSH_OPTS=(-i "$key_file" -o BatchMode=yes -o IdentitiesOnly=yes -o UserKnownHostsFile="$SSH_DIR/known_hosts" -o StrictHostKeyChecking=yes)
  REMOTE="${ecs_user}@${ecs_host}"
}

ecs_ssh() {
  ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"
}

ecs_scp() {
  scp "${SSH_OPTS[@]}" "$@"
}
