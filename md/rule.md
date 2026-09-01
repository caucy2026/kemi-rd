# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. Preserve Proven Workflows

**A workflow that passed an end-to-end verification is project knowledge, not disposable conversation memory.**

- Before building, debugging, uploading, publishing, configuring a VM, injecting, signing, connecting a device, or integrating another tool, read the mandatory project docs and search for the last verified workflow.
- Every project must keep its verified workflow records and index in the durable project context. A new session, executor, model, resumed task, or compacted context must reload them before taking action.
- The context must state which workflow is currently stable, where its full record lives, and what evidence last verified it. Source code without its proven build, debug, deployment, and toolchain knowledge is incomplete context.
- Use the verified workflow as the default baseline, including its versions, paths, commands, ordering, artifacts, target environment, and acceptance criteria.
- Do not replace a mature method because time has passed, the current executor forgot it, or an unverified alternative looks easier.
- While the recorded workflow remains applicable, do not restart research or invent a fresh debugging path for every task. Reproduce the baseline first; explore alternatives only after repeatable failure evidence.
- Test a new method only as an isolated candidate. It may replace the baseline only after the old method has documented failure evidence and the candidate passes an equivalent or stronger end-to-end validation.
- When a method becomes stable or changes, update the project's mandatory documentation in the same task. Record prerequisites, exact steps, outputs, evidence, failure signatures, rollback, date, and commit/artifact identity.
- Chat history, shell history, and personal memory are not durable sources of truth. The project documentation is.

The detailed inheritance and recording contract is mandatory in `md/dev-iron-rules.md`, under **铁律四：成熟方法必须继承**.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
