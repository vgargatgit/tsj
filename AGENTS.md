# TSJ Agent Instructions

## Engineering Strategy
This project follows a comprehensive test-driven development (TDD) strategy.

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
