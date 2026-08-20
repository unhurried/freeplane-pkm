#!/usr/bin/env bash
# Runs shellcheck over this repo's shell scripts - tools/*.sh and the Claude
# Code hook bodies in tools/hooks/ (see .claude/settings.json).
#
# Those scripts are guardrails themselves (tools/check-conventions.sh gates
# `gradle check`; the hooks gate edits and commits), and a bug in one fails
# open - it stops catching things instead of complaining - so they get the
# same static analysis lib/ and test/ get from CodeNarc.
#
# Note: shellcheck isn't a build dependency; when it isn't installed the check
# reports that and passes, so a local `gradle check` still runs everywhere.
# CI's ubuntu-latest image ships it, so it is effectively always enforced
# there.
#
# Severity is capped at "warning" for the same reason config/codenarc/rules.groovy
# is a short list: the point is to catch things that actually break a script
# (unquoted expansions, misread exit codes), not to hold shell style opinions
# that would turn a guardrail into a chore.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

if ! command -v shellcheck >/dev/null 2>&1; then
    echo "check-shell: shellcheck not installed - skipping (it runs in CI)."
    exit 0
fi

shellcheck --severity=warning tools/*.sh tools/hooks/*.sh
