#!/usr/bin/env bash
# Stop hook: the backstop for tools/check-conventions.sh.
#
# The PostToolUse hook only sees edits made through the Edit/Write family of
# tools. A file changed by a shell command (sed -i, mv, git apply, a
# generator script) never reaches that matcher, so a convention violation
# introduced that way would sit unnoticed until someone ran `gradle check`.
# This hook runs the same check once more as the turn ends, and blocks the
# stop - handing the violations back - if any remain.
#
# stop_hook_active guards against looping: when Claude is already continuing
# because of this hook, blocking again would never terminate.
set -uo pipefail

payload=$(cat)

if [ "$(printf '%s' "$payload" | jq -r '.stop_hook_active // false')" = "true" ]; then
    exit 0
fi

root=${CLAUDE_PROJECT_DIR:-}
if [ -z "$root" ]; then
    root=$(cd "$(dirname "$0")/../.." && pwd) || exit 0
fi

[ -x "$root/tools/check-conventions.sh" ] || exit 0

if violations=$(bash "$root/tools/check-conventions.sh" 2>&1); then
    exit 0
fi

reason="tools/check-conventions.sh still reports violations; fix them before finishing:
${violations}"
jq -n --arg reason "$reason" '{decision: "block", reason: $reason}'
