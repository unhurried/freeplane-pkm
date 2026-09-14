#!/usr/bin/env bash
# The machine-checkable conventions from CLAUDE.md. Runs as part of `gradle
# check` (the checkConventions task) and from the pre-commit hook in
# tools/hooks/. Dependency-free (bash + grep/sed/awk) so it stays fast.
# Prints one line per violation and exits non-zero if any was found.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

addon_defs_file='gradle/packageAddon.gradle'
exempt_file='tools/spec-exempt.txt'
workflow_file='.github/workflows/package-addon.yml'

violations=0

fail() {
    echo "check-conventions: $1" >&2
    violations=$((violations + 1))
}

declared_scripts=$(awk '/^def addonScriptDefs = \[/ { inblock = 1; next }
                        inblock && /^\]/ { exit }
                        inblock' "$addon_defs_file" |
    sed -nE "s/.*file: '([^']*)'.*/\1/p" | sort -u)
actual_scripts=$(find scripts -maxdepth 1 -name '*.groovy' | sed 's#.*/##' | sort -u)
actual_lib=$(find lib -maxdepth 1 -name '*.groovy' | sed 's#.*/##' | sort -u)
exempt_paths=$(grep -vE '^\s*#|^\s*$' "$exempt_file" 2>/dev/null || true)

# --- 1. scripts/*.groovy <-> addonScriptDefs (gradle/packageAddon.gradle) ---
# packageAddon checks this too, but only when someone runs it.

while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    fail "scripts/$f has no entry in addonScriptDefs ($addon_defs_file)."
done <<< "$(comm -23 <(echo "$actual_scripts") <(echo "$declared_scripts"))"

while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    fail "addonScriptDefs ($addon_defs_file) declares '$f' but scripts/$f does not exist."
done <<< "$(comm -13 <(echo "$actual_scripts") <(echo "$declared_scripts"))"

# --- 2. scripts/*.groovy and lib/*.groovy <-> test/<Name>Spec.groovy ---
# Files that genuinely can't be spec'd are listed in tools/spec-exempt.txt
# with a reason, instead of being silently skipped.

check_spec_for() {
    local path="$1/$2"
    echo "$exempt_paths" | grep -qxF "$path" && return
    local base="${2%.groovy}"
    if [[ ! -f "test/${base}Spec.groovy" ]]; then
        fail "$path has no test/${base}Spec.groovy - add one, or list '$path' with a reason in $exempt_file."
    fi
}

while IFS= read -r f; do
    [[ -n "$f" ]] && check_spec_for scripts "$f"
done <<< "$actual_scripts"

while IFS= read -r f; do
    [[ -n "$f" ]] && check_spec_for lib "$f"
done <<< "$actual_lib"

while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    [[ -f "$path" ]] || fail "$exempt_file lists '$path', which does not exist."
done <<< "$exempt_paths"

# --- 3. Java version stays in sync between build.gradle and CI ---

build_java_version=$(grep -oE 'VERSION_[0-9]+' build.gradle | head -1 | grep -oE '[0-9]+')
workflow_java_version=$(grep -oE "java-version: '[0-9]+'" "$workflow_file" | head -1 | grep -oE '[0-9]+')

if [[ -z "$build_java_version" || -z "$workflow_java_version" ]]; then
    fail "could not determine the Java version from build.gradle and/or $workflow_file."
elif [[ "$build_java_version" != "$workflow_java_version" ]]; then
    fail "build.gradle targets Java $build_java_version but $workflow_file pins '$workflow_java_version'."
fi

# --- 4. Node fixtures use FakeNode, not Expando ---
# A cyclic Expando's toString() overflows the stack inside Gradle's JUnit
# listener, which records the failing test as skipped. ScriptSpec alone uses
# Expando, for its acyclic controller/UI/map fakes.

while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    fail "$f builds fixtures with 'new Expando(' - use test/FakeNode.groovy (see its class doc)."
done <<< "$(grep -rl 'new Expando(' test/ 2>/dev/null | grep -vx 'test/ScriptSpec.groovy' || true)"

if [[ "$violations" -gt 0 ]]; then
    echo "check-conventions: $violations violation(s) found." >&2
    exit 1
fi
