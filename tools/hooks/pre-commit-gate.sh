#!/usr/bin/env bash
# PreToolUse hook (wired up in .claude/settings.json): refuses a `git commit`
# whose tree doesn't pass `gradle check`, this repo's definition of done.
# The whole .tool_input.command is inspected, not just its first line, so a
# multi-line command with `git commit` further down doesn't slip through.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty')
[ -n "$cmd" ] || exit 0

case "$cmd" in
    *"git commit"*) ;;
    *) exit 0 ;;
esac

root=${CLAUDE_PROJECT_DIR:-}
if [ -z "$root" ]; then
    root=$(cd "$(dirname "$0")/../.." && pwd) || exit 0
fi

if (cd "$root" && gradle check >&2); then
    exit 0
fi

printf '%s' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"gradle check failed; see stderr above for the gradle output and fix the violations before committing."}}'
