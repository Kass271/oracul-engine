
## Round 1
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
PreToolUse:Bash hook error: [node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/hooks/guard-edits.mjs"]: [oracul guard] `node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/bin/stack.mjs" e2e` is a stack operation: E2E and the Docker stack run only in the workflow's E2E step (subStep e2e). In a test-fix round the tester may verify a repair with: node <engine>/bin/stack.mjs e2e --scratch --grep <spec file>.

This hook comes from the oracul@inline plugin.
```

## Round 2
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
PreToolUse:Bash hook error: [node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/hooks/guard-edits.mjs"]: [oracul guard] `node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/bin/stack.mjs" e2e` is a stack operation: E2E and the Docker stack run only in the workflow's E2E step (subStep e2e). In a test-fix round the tester may verify a repair with: node <engine>/bin/stack.mjs e2e --scratch --grep <spec file>.
```

## Round 2
- Trigger: E2E RED
- To builders: yes · to tester: no
- Output:

```
PreToolUse:Bash hook error: [node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/hooks/guard-edits.mjs"]: [oracul guard] `node "/Users/frederiks/courses/agent_crash/oracul/factory-engine/bin/stack.mjs" e2e` is a stack operation: E2E and the Docker stack run only in the workflow's E2E step (subStep e2e). In a test-fix round the tester may verify a repair with: node <engine>/bin/stack.mjs e2e --scratch --grep <spec file>.
```

## Recovery (orchestrator, 2026-10-03)
- Rounds 1–2 never ran E2E: the guard hook refused the workflow's own `state.mjs set subStep e2e && stack.mjs e2e` command (factory bug, fixed in factory-engine 1cdc08f). The "Round 2" heading above is duplicated; it is the round-3 entry.
- Round 3 E2E was killed by the runner's 600 s Bash limit (exit 124, "Channel closed" in an unrelated FR-19 test) — not a test failure.
- E2E rerun alone by the orchestrator: 75 passed (9.5 m), incl. why-and-sources.spec.ts.
- Review round 3: R1 (medium, empty why-evidence-list container) + lows R2–R5. Tester added the container assertion (RED confirmed: 1/241 failing), frontend builder guarded the container in why-sources.ts (241/241 green); R4, R5 fixed by the tester.
- Full verify GREEN, E2E rerun GREEN (75 passed), review round 4 clean (open lows: R2, R3).
