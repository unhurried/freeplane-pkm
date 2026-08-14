// Minimal CodeNarc ruleset for lib/ and test/ (wired up via the codenarc plugin
// in build.gradle - scripts/ is deliberately not covered, see there for why).
//
// Groovy's dynamic typing means the compiler itself won't catch a stray
// import, a dead variable, or an exception silently swallowed on a path
// tests don't happen to exercise. These rules catch exactly that kind of
// thing and little else, so the check stays low-noise enough to run on every
// `gradle check` without turning into a style debate.
//
// UnnecessaryGString was tried and dropped: test/*Spec.groovy's multi-line
// Markdown page fixtures are written as triple-double-quoted strings purely
// for readability (no interpolation), and the rule flagged all of them -
// pure noise, not a real finding.
ruleset {
    UnusedImport
    UnusedVariable
    UnusedPrivateMethod
    EmptyCatchBlock
    DuplicateNumberLiteral {
        // 0/1/-1 are common, self-explanatory sentinels (not-found index,
        // boolean-ish flags) repeated across unrelated call sites, and small
        // values like 3 recur as two unrelated named @Field constants in
        // Utils.groovy (MAX_NEXT_STEPS, MIN_LIST_ITEM_LENGTH) - flagging
        // either case is noise, not a duplication smell, since the rule
        // can't see that a literal is already behind a named constant.
        ignoreNumbers = '0,1,-1,3'
    }
}
