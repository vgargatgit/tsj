# Lessons

## 2026-02-22
- Pattern: I started by inspecting TSJ implementation details for a task that was explicitly scoped to building a standalone TypeScript example app.
- Rule: For spec implementation under `examples/*`, default to app-local implementation and only touch TSJ internals if the user explicitly asks for integration.
- Guardrail: Before exploration, state dependency intent: `standalone app` vs `TSJ integration`; if standalone, restrict reads/edits to that example folder and shared docs.
- Pattern: I proposed creating a separate TSJ-compatible suite when the user asked for full TSJ success on existing TGTA `src/ok` files.
- Rule: When user goal is explicit (`TSJ-COMPILE-SUCCESS for all files`), do gap analysis against that exact target first; do not redirect scope unless asked.
- Guardrail: For validation requests, always report current pass/fail against the exact requested file set before suggesting alternatives.
- Pattern: I left an out-of-scope JSX/TSX parser gap item in the actionable TODO list after scope was narrowed.
- Rule: When scope exclusion is explicit (for example, `jsx/tsx is not in scope`), remove excluded work items immediately from active TODOs and plans.
- Guardrail: Before starting implementation, re-check active TODO bullets against the latest scope constraints from the user.
- Pattern: I attempted file edits via `exec_command` using `apply_patch` text instead of using the dedicated `apply_patch` tool.
- Rule: For source/doc edits, use `apply_patch` directly and reserve `exec_command` for shell execution only.
- Guardrail: Before any patch edit, choose the tool explicitly: `apply_patch` for text diffs, `exec_command` for running commands/tests.
