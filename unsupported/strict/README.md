# Unsupported Strict Fixtures

This folder tracks strict-mode unsupported fixtures that should fail with
stable diagnostics.

Each runnable fixture uses:

- `NNN_name.ts` naming.
- `// EXPECT_CODE: <diagnostic-code>`
- `// EXPECT_FEATURE_ID: <feature-id>`

Helper modules should use `_name.ts` and are excluded from progression counts.
