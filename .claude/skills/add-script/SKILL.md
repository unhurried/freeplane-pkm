---
name: add-script
description: Add, remove, or rename a Freeplane PKM menu script (scripts/*.groovy). Use whenever a scripts/*.groovy file is created, deleted, or renamed, since each of those changes several files together, not one.
---

# Adding/removing/renaming a menu script

A menu script is never a standalone file change. Move these together:

1. **`scripts/<Name>.groovy`** — top-level Groovy statements against the bindings
   Freeplane injects (`node`, `c`, `ui`). Outside-world side effects (opening a
   file, spawning a process) go through `lib/Utils.groovy` (e.g.
   `Utils.openInDesktop`) so the spec can replace them.
2. **`test/<Name>Spec.groovy`** — extends `test/ScriptSpec.groovy` (see its class
   doc for the fakes and recorded results). Use `FakeNode`, never an `Expando`,
   for node fixtures. A script that can't be driven from a `GroovyShell` (Swing
   `JDialog`, `org.freeplane.*` imports) is instead listed in
   `tools/spec-exempt.txt` with a reason.
3. **`gradle/packageAddon.gradle`**'s `addonScriptDefs` — menu title, execution
   mode, shortcut, permissions. Renaming means updating the existing entry.
4. **`README.md`**'s shortcut table, if the title or shortcut changed.

Then run `gradle check`: `tools/check-conventions.sh` fails on a script without
a spec or an `addonScriptDefs` entry, and `ScriptSyntaxSpec` parses every script.
