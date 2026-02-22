# TSJ Agent Instructions

## Engineering Strategy
- This project follows a comprehensive test-driven development (TDD) strategy.
- In plan mode, if something goes sideways, STOP and re-plan immediately - don't keep pushing

### Self-Improvement loop
- After ANY correction from the user: update `docs/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "would a Phd level compiler designer approve this?"

### Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user

## Task Management
- **Plan First**: Write plan to `docs/plans.md` with checkable items
- **Verify Plans**: Check in before starting implementation 
- **Track Progress**: Mark items complete as you go
- **Explain Changes**: High-level summary at each step
- **Document Results**: Add review section to `docs/todo.md`
- **Capture Lessons**: Update `docs/lesson.md` after corrections

## Core Principles
- **Simplicity**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's neccessary. Avoid introducing bugs.

### Required Workflow
1. `Red`: add or update tests to express intended behavior before implementation changes.
2. `Green`: implement the smallest code change needed to make tests pass.
3. `Refactor`: improve design while keeping tests green.

### Coverage Expectations
1. Unit tests for all new module-level logic.
2. Integration tests for cross-module behavior as stories progress.
3. Differential/conformance tests for language semantics when compiler features are added.

### Definition of Done
1. Relevant tests exist and pass locally/CI.
2. Lint checks pass.
3. Behavior changes are documented in story or architecture docs when needed.

## Story Execution Directive
1. Work on all planned stories in sequence.
2. Only change the sequence when the user explicitly reprioritizes or skips stories.
3. Continue execution until all stories are completed.
