#!/usr/bin/env bash
# PreToolUse hook: refuses a `git commit` whose tree doesn't pass `gradle
# check`, which is this repo's definition of done (see CLAUDE.md).
#
# Wired up in .claude/settings.json. The hook payload arrives as JSON on
# stdin; the whole .tool_input.command is read (NOT just its first line -
# a multi-line bash command whose `git commit` sits on a later line used to
# slip through this gate entirely).
#
# On failure it prints the deny decision Claude Code understands, with
# gradle's own output already on stderr for the reader to act on.
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
