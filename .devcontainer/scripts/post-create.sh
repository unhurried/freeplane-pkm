#!/usr/bin/env bash
set -euo pipefail

workspace_dir="${1:-/workspaces/freeplane-pkm}"
template_dir="${workspace_dir}/.devcontainer"
claude_home="${HOME}/.claude"

install -d -m 0755 "${claude_home}"
install -m 0644 "${template_dir}/claude/settings.json" "${claude_home}/settings.json"
install -m 0644 "${template_dir}/.npmrc" "${HOME}/.npmrc"
