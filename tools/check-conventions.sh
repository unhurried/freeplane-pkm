#!/usr/bin/env bash
# Machine-checkable half of CLAUDE.md's conventions - the ones prose alone
# can't enforce. Run standalone (this script), as part of `gradle check` (see
# the checkConventions task in build.gradle), and from the hooks in
# .claude/settings.json (whose bodies live in tools/hooks/) right after a
# source file is edited and again before a turn ends.
#
# Deliberately dependency-free (bash + grep/sed/awk + sha256sum, no JVM) so it
# stays fast enough to run on every edit. Prints one line per violation,
# prefixed with the file it concerns and what to do about it, and exits
# non-zero if any violation was found.
#
# Two generated artifacts have refresh flags rather than being fixed by hand:
#   --update-readme-shortcuts    regenerates README.md's shortcut table (#8)
#   --update-index-fingerprint   regenerates tools/index-schema.fingerprint (#10)
# There is deliberately no blanket --fix: a guardrail that can be silenced
# without reading it isn't a guardrail.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

addon_defs_file='gradle/packageAddon.gradle'
exempt_file='tools/spec-exempt.txt'
workflow_file='.github/workflows/package-addon.yml'
readme_file='README.md'
search_index_file='lib/SearchIndex.groovy'
fingerprint_file='tools/index-schema.fingerprint'

readme_begin_marker='<!-- shortcuts:begin (generated from addonScriptDefs in gradle/packageAddon.gradle) -->'
readme_end_marker='<!-- shortcuts:end -->'

violations=0

fail() {
    echo "check-conventions: $1" >&2
    violations=$((violations + 1))
}

# --- Shared parsing of gradle/packageAddon.gradle ---
# Both the script list and the menu metadata come from the same
# addonScriptDefs literal, so it is parsed once, here, into TSV
# (file<TAB>title<TAB>shortcut) in menu order. Quoted values are matched as
# '[^']*' rather than a character class of "expected" characters, so a name
# this script didn't anticipate produces a real comparison instead of a
# phantom "missing" report.

script_defs() {
    awk '/^def addonScriptDefs = \[/ { inblock = 1; next }
         inblock && /^\]/ { exit }
         inblock' "$addon_defs_file" |
        sed -nE "s/.*file: '([^']*)'.*title: '([^']*)'.*shortcut: '([^']*)'.*/\1\t\2\t\3/p"
}

zip_entry_sources() {
    awk '/^def addonZipEntries = \[/ { inblock = 1; next }
         inblock && /^\]/ { exit }
         inblock' "$addon_defs_file" |
        sed -nE "s/.*'[^']*'[[:space:]]*:[[:space:]]*'([^']*)'.*/\1/p"
}

check_spec_for() {
    local dir="$1" file="$2"
    local path="$dir/$file"
    if echo "$exempt_paths" | grep -qxF "$path"; then
        return
    fi
    local base="${file%.groovy}"
    if [[ ! -f "test/${base}Spec.groovy" ]]; then
        fail "$path has no test/${base}Spec.groovy - add one (see test/ScriptSpec.groovy for scripts, test/UtilsSpec.groovy for lib), or add '$path' with a reason comment to $exempt_file if it genuinely can't be spec'd."
    fi
}

render_shortcut_table() {
    echo '| Shortcut | Menu entry |'
    echo '| --- | --- |'
    # Ordered by function key rather than by menu position: the table answers
    # "which key does what", so F1..F12 is the order a reader scans it in.
    while IFS=$'\t' read -r _ title shortcut; do
        [[ -z "$shortcut" ]] && continue
        printf '%s\t| %s | %s |\n' "${shortcut#F}" "$shortcut" "$title"
    done <<< "$defs_tsv" | sort -n -k1,1 | cut -f2-
}

readme_shortcut_table() {
    awk -v begin="$readme_begin_marker" -v end="$readme_end_marker" '
        $0 == begin { inblock = 1; next }
        $0 == end { inblock = 0; next }
        inblock && NF' "$readme_file"
}

update_readme_shortcuts() {
    if ! grep -qF "$readme_begin_marker" "$readme_file" || ! grep -qF "$readme_end_marker" "$readme_file"; then
        echo "check-conventions: $readme_file has no shortcut table markers to write between - restore the '$readme_begin_marker' / '$readme_end_marker' pair first." >&2
        return 1
    fi
    local tmp
    tmp=$(mktemp) || return 1
    awk -v begin="$readme_begin_marker" -v end="$readme_end_marker" -v table="$(render_shortcut_table)" '
        $0 == begin { print; print table; inblock = 1; next }
        $0 == end { inblock = 0 }
        !inblock' "$readme_file" > "$tmp" || return 1
    mv "$tmp" "$readme_file"
    echo "check-conventions: regenerated the shortcut table in $readme_file."
}

schema_region() {
    sed -n '/schema-region:begin/,/schema-region:end/p' "$search_index_file"
}

schema_region_hash() {
    schema_region | sha256sum | cut -d' ' -f1
}

update_index_fingerprint() {
    local new_hash recorded_hash recorded_version
    if [[ -z "$current_schema_version" || -z "$(schema_region)" ]]; then
        echo "check-conventions: cannot fingerprint $search_index_file - INDEX_SCHEMA_VERSION and/or the schema-region markers are missing." >&2
        return 1
    fi
    new_hash=$(schema_region_hash)
    recorded_hash=$(grep -oE '^sha256=.*' "$fingerprint_file" 2>/dev/null | cut -d= -f2)
    recorded_version=$(grep -oE '^version=[0-9]+' "$fingerprint_file" 2>/dev/null | cut -d= -f2)

    if [[ -n "$recorded_hash" && "$recorded_hash" != "$new_hash" && "$recorded_version" == "$current_schema_version" ]]; then
        echo "check-conventions: refusing to update $fingerprint_file - the schema region changed but INDEX_SCHEMA_VERSION is still $current_schema_version. Bump it in $search_index_file first, so an existing on-disk index gets rebuilt instead of silently mismatching the new tokens." >&2
        return 1
    fi

    cat > "$fingerprint_file" <<EOF
# Pins the schema-affecting region of $search_index_file (the part between the
# schema-region markers: analyzer construction and indexed field setup) to the
# INDEX_SCHEMA_VERSION it belongs to. Checked by #10 in
# tools/check-conventions.sh. Do not edit by hand: bump INDEX_SCHEMA_VERSION,
# then run 'bash tools/check-conventions.sh --update-index-fingerprint'.
version=$current_schema_version
sha256=$new_hash
EOF
    echo "check-conventions: recorded schema version $current_schema_version / $new_hash in $fingerprint_file."
}

# --- Inputs shared by the checks below ---

defs_tsv=$(script_defs)
declared_scripts=$(printf '%s\n' "$defs_tsv" | cut -f1 | grep -v '^$' | sort -u)
actual_scripts=$(find scripts -maxdepth 1 -name '*.groovy' | sed 's#.*/##' | sort -u)
actual_lib=$(find lib -maxdepth 1 -name '*.groovy' | sed 's#.*/##' | sort -u)
exempt_paths=$(grep -vE '^\s*#|^\s*$' "$exempt_file" 2>/dev/null || true)
current_schema_version=$(grep -oE 'INDEX_SCHEMA_VERSION = [0-9]+' "$search_index_file" | head -1 | grep -oE '[0-9]+')

# --- Refresh flags run instead of the checks ---

case "${1:-}" in
    --update-readme-shortcuts)
        update_readme_shortcuts
        exit $?
        ;;
    --update-index-fingerprint)
        update_index_fingerprint
        exit $?
        ;;
    '') ;;
    *)
        echo "check-conventions: unknown option '$1' (expected --update-readme-shortcuts or --update-index-fingerprint)." >&2
        exit 2
        ;;
esac

# --- 1. scripts/*.groovy <-> addonScriptDefs (gradle/packageAddon.gradle) ---
# Mirrors the check packageAddon itself does at build time (see
# gradle/packageAddon.gradle), surfaced earlier - at edit time / `gradle
# check` - instead of only when someone happens to run `gradle packageAddon`.

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

# --- 2. scripts/*.groovy and lib/*.groovy <-> test/<Name>Spec.groovy ---
# CLAUDE.md: "Everything in lib/ is tested, and so is every script that
# carries logic of its own", and "Adding a script means adding its spec."
# Files that genuinely can't be spec'd (see the testing policy) are listed,
# one repo-relative path per line with a leading '# reason' comment, in
# tools/spec-exempt.txt instead of being silently skipped.

while IFS= read -r script; do
    [[ -z "$script" ]] && continue
    check_spec_for scripts "$script"
done <<< "$actual_scripts"

while IFS= read -r libfile; do
    [[ -z "$libfile" ]] && continue
    check_spec_for lib "$libfile"
done <<< "$actual_lib"

# Exempt entries that no longer correspond to a real file are just as stale
# as a missing spec - catch typos/renames left behind in the exemption list.
while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    if [[ ! -f "$path" ]]; then
        fail "$exempt_file lists '$path', which does not exist - remove the stale entry."
    fi
done <<< "$exempt_paths"

# --- 3. No direct outside-world side effects in scripts/ ---
# CLAUDE.md: "Side effects on the outside world belong in lib/Utils.groovy
# (e.g. Utils.openInDesktop) rather than inline in a script, so specs can
# replace them: java.awt.Desktop is unusable in a headless test JVM."
#
# The rationale isn't specific to Desktop, so the same rule covers process
# spawning and JVM exit. Swing dialog classes (JOptionPane in Delete.groovy,
# JDialog in Search.groovy) are deliberately NOT listed: they are how a
# script asks the user something, not a side effect on the outside world.

side_effect_hits=$(grep -rnE 'Desktop\.getDesktop\(\)|import java\.awt\.Desktop|ProcessBuilder|Runtime\.getRuntime\(\)|System\.exit\(|\.execute\(\)' scripts/ 2>/dev/null || true)
if [[ -n "$side_effect_hits" ]]; then
    while IFS= read -r hit; do
        [[ -z "$hit" ]] && continue
        fail "$hit <- this side effect belongs in lib/Utils.groovy (as e.g. Utils.openInDesktop does), so a spec can replace it."
    done <<< "$side_effect_hits"
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

build_java_version=$(grep -oE 'VERSION_[0-9]+' build.gradle | head -1 | grep -oE '[0-9]+')
workflow_java_version=$(grep -oE "java-version: '[0-9]+'" "$workflow_file" | head -1 | grep -oE '[0-9]+')

if [[ -z "$build_java_version" || -z "$workflow_java_version" ]]; then
    fail "could not determine Java version from build.gradle and/or $workflow_file - check they still use JavaVersion.VERSION_NN / java-version: 'NN'."
elif [[ "$build_java_version" != "$workflow_java_version" ]]; then
    fail "build.gradle targets Java $build_java_version but $workflow_file pins java-version '$workflow_java_version' - keep them in sync (see CLAUDE.md)."
fi

# --- 6. Node fixtures use FakeNode, not Expando ---
# CLAUDE.md: "Extend FakeNode (not an Expando) when a script starts using
# more of Freeplane's node API. Node fakes form a parent/child cycle and
# Expando.toString() recurses into it, so any failure message mentioning one
# overflows the stack, kills Gradle's JUnit listener mid-report, and gets
# recorded as a *skipped* test in a green build."
#
# ScriptSpec uses Expando for the acyclic fakes (map, controller, ui) and
# UtilsSpec for its flat node fixtures; both are the documented exceptions.
# Anything else reaching for Expando is the mistake this rule exists to catch.

expando_hits=$(grep -rl 'new Expando(' test/ 2>/dev/null | grep -vE '^test/(ScriptSpec|UtilsSpec)\.groovy$' || true)
if [[ -n "$expando_hits" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "$f builds fixtures with 'new Expando(' - use test/FakeNode.groovy instead (see its class doc: a cyclic Expando turns a failing test into a silently skipped one)."
    done <<< "$expando_hits"
fi

# --- 7. addonScriptDefs is internally consistent ---
# Freeplane silently takes one binding when two menu entries claim the same
# shortcut, and a duplicated title makes two menu entries indistinguishable,
# so neither shows up as a build or test failure.

dup_shortcuts=$(printf '%s\n' "$defs_tsv" | cut -f3 | grep -v '^$' | sort | uniq -d)
if [[ -n "$dup_shortcuts" ]]; then
    while IFS= read -r sc; do
        [[ -z "$sc" ]] && continue
        fail "addonScriptDefs ($addon_defs_file) assigns shortcut '$sc' to more than one script - give each entry its own shortcut (or an empty one)."
    done <<< "$dup_shortcuts"
fi

dup_titles=$(printf '%s\n' "$defs_tsv" | cut -f2 | grep -v '^$' | sort | uniq -d)
if [[ -n "$dup_titles" ]]; then
    while IFS= read -r title; do
        [[ -z "$title" ]] && continue
        fail "addonScriptDefs ($addon_defs_file) uses the menu title '$title' more than once - menu entries must be distinguishable."
    done <<< "$dup_titles"
fi

while IFS=$'\t' read -r file title shortcut; do
    [[ -z "$file" ]] && continue
    if [[ ! "$shortcut" =~ ^(F[1-9]|F1[0-2])?$ ]]; then
        fail "addonScriptDefs ($addon_defs_file) gives '$file' the shortcut '$shortcut' - this add-on assigns F1-F12, or an empty shortcut for menu-only entries."
    fi
done <<< "$defs_tsv"

# --- 8. README.md's shortcut table matches addonScriptDefs ---
# The table is generated from addonScriptDefs; regenerate it with
# `bash tools/check-conventions.sh --update-readme-shortcuts` rather than
# editing it by hand. (Before this check existed, README claimed the
# shortcuts were "F1-F10, F12" while Delete.groovy had long since taken F11.)

if ! grep -qF "$readme_begin_marker" "$readme_file" || ! grep -qF "$readme_end_marker" "$readme_file"; then
    fail "$readme_file has no shortcut table markers - restore the '$readme_begin_marker' / '$readme_end_marker' pair around the table (see #8 in tools/check-conventions.sh)."
elif [[ "$(readme_shortcut_table)" != "$(render_shortcut_table)" ]]; then
    fail "$readme_file's shortcut table no longer matches addonScriptDefs ($addon_defs_file) - run 'bash tools/check-conventions.sh --update-readme-shortcuts'."
fi

# --- 9. addonZipEntries covers everything that has to reach the user dir ---
# scripts/*.groovy are embedded as script nodes; everything else under
# scripts/ (the page template, and the whole scripts/init/ tree) only reaches
# <userdir> through the zip node. packageAddon fails when a *declared* entry
# is missing, but an undeclared file is silently left out of the add-on - the
# scripts that read it then fail at runtime, on the user's machine.

declared_zip_sources=$(zip_entry_sources | sort -u)
actual_zip_sources=$( { find scripts -maxdepth 1 -type f ! -name '*.groovy'; find scripts/init -type f 2>/dev/null; } | sort -u)

undeclared_zip=$(comm -23 <(echo "$actual_zip_sources") <(echo "$declared_zip_sources"))
missing_zip=$(comm -13 <(echo "$actual_zip_sources") <(echo "$declared_zip_sources"))

if [[ -n "$undeclared_zip" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "$f is not declared in addonZipEntries ($addon_defs_file) - add it, or the packaged add-on will not install it into the user directory."
    done <<< "$undeclared_zip"
fi
if [[ -n "$missing_zip" ]]; then
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        fail "addonZipEntries ($addon_defs_file) declares '$f', which does not exist - remove the stale entry or restore the file."
    done <<< "$missing_zip"
fi

# --- 10. SearchIndex's schema fingerprint matches INDEX_SCHEMA_VERSION ---
# CLAUDE.md: "bump SearchIndex.INDEX_SCHEMA_VERSION whenever the analyzer or
# field configuration changes in a way that makes old and new tokens
# incompatible". Forgetting the bump has no visible symptom in the build: the
# tests build a fresh index, so they pass, and only a user with an existing
# index sees search silently return the wrong results (or nothing).
#
# So the schema-affecting part of SearchIndex.groovy is delimited by
# schema-region markers and its hash pinned in tools/index-schema.fingerprint
# next to the version it belongs to. Changing that region without bumping
# INDEX_SCHEMA_VERSION fails here, and --update-index-fingerprint refuses to
# paper over it.

if [[ -z "$current_schema_version" ]]; then
    fail "could not read INDEX_SCHEMA_VERSION from $search_index_file - check it is still declared as 'INDEX_SCHEMA_VERSION = <n>'."
elif [[ -z "$(schema_region)" ]]; then
    fail "$search_index_file has no schema-region markers - restore the '// schema-region:begin' / '// schema-region:end' comments around the analyzer and field configuration (see #10 in tools/check-conventions.sh)."
elif [[ ! -f "$fingerprint_file" ]]; then
    fail "$fingerprint_file is missing - run 'bash tools/check-conventions.sh --update-index-fingerprint' to record the current schema fingerprint."
else
    recorded_version=$(grep -oE '^version=[0-9]+' "$fingerprint_file" | cut -d= -f2)
    recorded_hash=$(grep -oE '^sha256=.*' "$fingerprint_file" | cut -d= -f2)
    if [[ "$recorded_hash" != "$(schema_region_hash)" ]]; then
        fail "$search_index_file's analyzer/field configuration changed but $fingerprint_file still pins the old fingerprint - bump INDEX_SCHEMA_VERSION (currently $current_schema_version) so existing indexes get rebuilt, then run 'bash tools/check-conventions.sh --update-index-fingerprint'."
    elif [[ "$recorded_version" != "$current_schema_version" ]]; then
        fail "$fingerprint_file records INDEX_SCHEMA_VERSION $recorded_version but $search_index_file declares $current_schema_version - run 'bash tools/check-conventions.sh --update-index-fingerprint'."
    fi
fi

# Deliberately NOT checked here: CLAUDE.md's "node mutations must happen on
# the Swing EDT". Whether a given mutation already sits inside an
# invokeLater block isn't something grep can decide, and a rule that guessed
# would either miss the real cases or cry wolf on the correct ones. It stays
# a prose convention.

if [[ "$violations" -gt 0 ]]; then
    echo "check-conventions: $violations violation(s) found." >&2
    exit 1
fi
exit 0
