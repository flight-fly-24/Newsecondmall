#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../../scripts/ci-ecs-ssh.sh
source "$repo_root/scripts/ci-ecs-ssh.sh"

test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT

ssh-keygen -t ed25519 -f "$test_root/id" -N "" -C "ci-test" -q
original="$(cat "$test_root/id")"
flat_spaces="$(tr '\n' ' ' < "$test_root/id")"
flat_none="$(tr -d '\n' < "$test_root/id")"

ecs_write_private_key "$test_root/from-original" "$original"
ecs_write_private_key "$test_root/from-spaces" "$flat_spaces"
ecs_write_private_key "$test_root/from-concat" "$flat_none"

ssh-keygen -y -f "$test_root/from-original" >/dev/null
ssh-keygen -y -f "$test_root/from-spaces" >/dev/null
ssh-keygen -y -f "$test_root/from-concat" >/dev/null

if ecs_write_private_key "$test_root/bad" "not-a-key" 2>/dev/null; then
  echo "Invalid key unexpectedly accepted" >&2
  exit 1
fi

echo "CI ECS SSH key normalize tests: OK"
