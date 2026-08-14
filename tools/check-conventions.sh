#!/usr/bin/env bash
# Machine-checkable half of CLAUDE.md's conventions - the ones prose alone
# can't enforce. Run standalone (this script), as part of `gradle check` (see
# the checkConventions task in build.gradle), and from the PostToolUse hook in
# .claude/settings.json right after a script/lib/test file is edited.
#
# Deliberately dependency-free (bash + grep/sed only, no JVM) so it stays fast
# enough to run on every edit. Prints one line per violation, prefixed with
# the file it concerns and what to do about it, and exits non-zero if any
# violation was found.
set -uo pipefail

cd "$(dirname "$0")/.."

violations=0

fail() {
    echo "check-conventions: $1" >&2
    violations=$((violations + 1))
}

# --- 1. scripts/*.groovy <-> addonScriptDefs (gradle/packageAddon.gradle) ---
# Mirrors the check packageAddon itself does at build time (see
# gradle/packageAddon.gradle), surfaced earlier - at edit time / `gradle
# check` - instead of only when someone happens to run `gradle packageAddon`.

addon_defs_file='gradle/packageAddon.gradle'
declared_scripts=$(grep -oE "file: '[A-Za-z0-9_]+\.groovy'" "$addon_defs_file" | sed -E "s/file: '(.*)'/\1/" | sort -u)
actual_scripts=$(cd scripts && ls -1 *.groovy 2>/dev/null | sort -u)

missing_in_defs=$(comm -23 <(echo "$actual_scripts") <(echo "$declared_scripts"))
missing_in_scripts=$(comm -13 <(echo "$actual_scripts") <(echo "$declared_scripts"))

if [[ -n "$missing_in_defs" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "scripts/$f has no entry in addonScriptDefs ($addon_defs_file) - add a [file: ..., title: ..., mode: ..., shortcut: ..., exec: ...] entry."
    done <<< "$missing_in_defs"
fi
if [[ -n "$missing_in_scripts" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "addonScriptDefs ($addon_defs_file) declares '$f' but scripts/$f does not exist - remove the stale entry or restore the file."
    done <<< "$missing_in_scripts"
fi

# --- 2. scripts/*.groovy <-> test/<Name>Spec.groovy ---
# CLAUDE.md: "Adding a script means adding its spec." Scripts that genuinely
# can't be spec'd (see the testing policy) are listed, one per line with a
# leading '# reason' comment, in tools/spec-exempt.txt instead of silently
# skipped.

exempt_file='tools/spec-exempt.txt'
exempt_scripts=$(grep -vE '^\s*#|^\s*$' "$exempt_file" 2>/dev/null || true)

while IFS= read -r script; do
    [[ -z "$script" ]] && continue
    if echo "$exempt_scripts" | grep -qxF "$script"; then
        continue
    fi
    base="${script%.groovy}"
    if [[ ! -f "test/${base}Spec.groovy" ]]; then
        fail "scripts/$script has no test/${base}Spec.groovy - add one (see test/ScriptSpec.groovy), or add '$script' with a reason comment to $exempt_file if it genuinely can't be spec'd."
    fi
done <<< "$actual_scripts"

# Exempt entries that no longer correspond to a real script are just as stale
# as a missing spec - catch typos/renames left behind in the exemption list.
while IFS= read -r script; do
    [[ -z "$script" ]] && continue
    if ! echo "$actual_scripts" | grep -qxF "$script"; then
        fail "$exempt_file lists '$script', which does not exist under scripts/ - remove the stale entry."
    fi
done <<< "$exempt_scripts"

# --- 3. No direct java.awt.Desktop use in scripts/ ---
# CLAUDE.md: "Side effects on the outside world belong in lib/Utils.groovy
# (e.g. Utils.openInDesktop) rather than inline in a script, so specs can
# replace them: java.awt.Desktop is unusable in a headless test JVM."

desktop_hits=$(grep -rlE 'Desktop\.getDesktop\(\)|import java\.awt\.Desktop' scripts/ 2>/dev/null || true)
if [[ -n "$desktop_hits" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "$f calls java.awt.Desktop directly - route it through Utils.openInDesktop() (lib/Utils.groovy) instead, so a spec can replace it."
    done <<< "$desktop_hits"
fi

# --- 4. No silently skipped tests under test/ ---
# The skippedTests listener in build.gradle already turns a SKIPPED JUnit
# result into a build failure; this catches the more common way a test gets
# silently dropped - a Spock annotation that excludes it before it's even
# run, which never produces a SKIPPED result for that listener to see.

skip_hits=$(grep -rlE '@(Ignore|IgnoreRest|PendingFeature)\b' test/ 2>/dev/null || true)
if [[ -n "$skip_hits" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "$f disables a test via @Ignore/@IgnoreRest/@PendingFeature - fix or remove the test instead of skipping it."
    done <<< "$skip_hits"
fi

# --- 5. Java version stays in sync between build.gradle and CI ---
# CLAUDE.md: "keep source/targetCompatibility in build.gradle in sync with
# java-version in .github/workflows/package-addon.yml."

workflow_file='.github/workflows/package-addon.yml'
build_java_version=$(grep -oE 'VERSION_[0-9]+' build.gradle | head -1 | grep -oE '[0-9]+')
workflow_java_version=$(grep -oE "java-version: '[0-9]+'" "$workflow_file" | head -1 | grep -oE '[0-9]+')

if [[ -z "$build_java_version" || -z "$workflow_java_version" ]]; then
    fail "could not determine Java version from build.gradle and/or $workflow_file - check they still use JavaVersion.VERSION_NN / java-version: 'NN'."
elif [[ "$build_java_version" != "$workflow_java_version" ]]; then
    fail "build.gradle targets Java $build_java_version but $workflow_file pins java-version '$workflow_java_version' - keep them in sync (see CLAUDE.md)."
fi

if [[ "$violations" -gt 0 ]]; then
    echo "check-conventions: $violations violation(s) found." >&2
    exit 1
fi
exit 0
