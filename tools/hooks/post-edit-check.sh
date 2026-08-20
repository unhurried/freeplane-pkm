#!/usr/bin/env bash
# PostToolUse hook: runs tools/check-conventions.sh right after Claude Code
# edits a file that the conventions cover, so a violation surfaces at edit
# time rather than at `gradle check` time.
#
# Wired up in .claude/settings.json. The hook payload arrives as JSON on
# stdin; only .tool_input.file_path is needed here. Anything outside
# scripts/, lib/, test/ and gradle/packageAddon.gradle is none of this
# check's business, so it exits 0 without running anything.
#
# Note this only covers edits made through the Edit/Write family of tools -
# a file changed by a shell command (sed -i, mv, git apply) never reaches a
# PostToolUse matcher. tools/hooks/stop-check.sh is the backstop for those.
set -uo pipefail

payload=$(cat)
file_path=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty')
[ -n "$file_path" ] || exit 0

case "$file_path" in
    */scripts/*.groovy|*/lib/*.groovy|*/test/*.groovy|*/gradle/packageAddon.gradle) ;;
    scripts/*.groovy|lib/*.groovy|test/*.groovy|gradle/packageAddon.gradle) ;;
    *) exit 0 ;;
esac

# The hook's working directory isn't guaranteed, and file_path may be
# relative; prefer the project dir Claude Code exports, and fall back to
# locating this script's own repository root.
root=${CLAUDE_PROJECT_DIR:-}
if [ -z "$root" ]; then
    root=$(cd "$(dirname "$0")/../.." && pwd) || exit 0
fi

[ -x "$root/tools/check-conventions.sh" ] || exit 0
bash "$root/tools/check-conventions.sh"
