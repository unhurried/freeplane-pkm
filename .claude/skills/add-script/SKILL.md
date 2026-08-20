---
name: add-script
description: Add, remove, or rename a Freeplane PKM menu script (scripts/*.groovy). Use whenever a scripts/*.groovy file is created, deleted, or renamed, since each of those changes three files together, not one.
---

# Adding/removing/renaming a menu script

A menu script under `scripts/*.groovy` is never a standalone file change. It is
always three files moved together, because `gradle check` (via
`tools/check-conventions.sh` and `test/ScriptSyntaxSpec.groovy`) fails the
build the moment any one of them is missing:

1. **`scripts/<Name>.groovy`** — the script itself. Top-level Groovy
   statements (not a class), written against the bindings Freeplane injects:
   `node`, `c`, `ui`. Any side effect on the outside world (opening a file,
   spawning a process, etc.) must go through a `lib/Utils.groovy` method
   (e.g. `Utils.openInDesktop`) instead of being called inline - `Desktop`
   and similar are unusable in the headless test JVM, and `check-conventions.sh`
   rejects `java.awt.Desktop` used directly in `scripts/`.
2. **`test/<Name>Spec.groovy`** — extends `test/ScriptSpec.groovy` (see its
   class doc for the fake `node`/`c`/`ui` bindings, `inputAnswers`/
   `confirmAnswer` for dialogs, and `errorMessages`/`openedInDesktop`/
   `selectedNodes`/`filterCalls` for assertions). If the script needs richer
   node behavior than `test/FakeNode.groovy` currently fakes, extend
   `FakeNode` - never use a plain `Expando` for node fixtures here (see
   `FakeNode`'s class doc for why: cyclic parent/child references make
   `Expando.toString()` overflow the stack and turn a failing test into a
   silently-skipped one).

   A script only skips this if it genuinely can't be driven from a
   `GroovyShell` (e.g. it builds a Swing `JDialog`, which throws
   `HeadlessException` in the test JVM, or it imports `org.freeplane.*`
   internals that aren't obtainable as a dependency) - and even then, add it
   to `tools/spec-exempt.txt` with a reason comment rather than leaving it
   silently uncovered. Entries there are repo-relative paths
   (`scripts/Search.groovy`, `lib/SearchHit.groovy`), since the same rule
   covers `lib/*.groovy`.
3. **`gradle/packageAddon.gradle`**'s `addonScriptDefs` list - the menu
   title, execution mode, keyboard shortcut, and per-script permissions.
   Removing a script also means removing its shortcut/menu order entry here;
   renaming means updating `file:`/`title:` together, not adding a new entry.

If the change touches a menu title or shortcut, regenerate `README.md`'s
shortcut table with `bash tools/check-conventions.sh --update-readme-shortcuts`
(it is generated from `addonScriptDefs` and checked against it); update the
feature list by hand if the change affects what a user sees in the
Tools → Scripts menu.

## Verifying the change

Run `gradle check` (not just `gradle test`) - it wires together the tests,
`test/ScriptSyntaxSpec.groovy` (parses every `scripts/*.groovy` file to catch
syntax errors that a plain-text-embedding `packageAddon` build would
otherwise ship unnoticed), and `tools/check-conventions.sh` (the script↔spec
and script↔`addonScriptDefs` sync checks, and the no-direct-`Desktop` rule
above, plus the menu-shortcut, README-table and `addonZipEntries` rules).
All of it must pass; a failure from `check-conventions.sh` names the exact
file and what to do about it.
