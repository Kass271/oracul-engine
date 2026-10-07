
## Round 1
- Trigger: E2E (related specs not green on the current inputs) RED
- To builders: yes · to tester: backend/src/test/java/com/oracul/app/result/StoryWritingIT.java
- Output:

```
NEEDED (3 of 26 specs): critic.spec.ts future-story.spec.ts starting-conditions.spec.ts
    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/critic.spec.ts:128:3 › FR-22 Critic validation › FR-22 a failing critic triggers one regeneration with the critique attached 
  3 did not run
  7 passed (2.7m)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 2m 41s
E2E FAIL
==== E2E FAILURES (1) ====
✘ critic.spec.ts › FR-22 a failing critic triggers one regeneration with the critique attached · chromium
    Error: expect(received).toHaveLength(expected)
    Expected length: 2
    Received length: 1
    Received array:  [{"input": [{"content": [{"text": "ORACUL REQUEST SCENARIO_GENERATION
    SETTINGS
    Attempt: 1 | Reason: INITIAL
    Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years
    Wildcards: New pandemic 8 | Humanoid robot boom 6
    Cutoff date: 2026-10-07
    Future event date window: after 2026-10-07 and no later than 2031-10-07
    TASK
    Construct one scenario from the starting conditions in evidence-pack under the settings above.
    Cite Evidence IDs exactly as written in the pack. Use claim ids F1, F2, … for facts, I1, I2, … for inferences and P1, P2, … for speculations.
    <<<ORACUL_UNTRUSTED_DATA name="evidence-pack">>>
    ORACUL EVIDENCE PACK
    attachment: e2e/test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 2
- Trigger: E2E (related specs not green on the current inputs) RED
- To builders: yes · to tester: backend/src/test/java/com/oracul/app/runs/RunFailureHygieneIT.java
- Output:

```
NEEDED (3 of 26 specs): critic.spec.ts future-story.spec.ts starting-conditions.spec.ts
    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/critic.spec.ts:128:3 › FR-22 Critic validation › FR-22 a failing critic triggers one regeneration with the critique attached 
  3 did not run
  7 passed (2.7m)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 2m 42s
E2E FAIL
==== E2E FAILURES (1) ====
✘ critic.spec.ts › FR-22 a failing critic triggers one regeneration with the critique attached · chromium
    Error: expect(received).toHaveLength(expected)
    Expected length: 2
    Received length: 1
    Received array:  [{"input": [{"content": [{"text": "ORACUL REQUEST SCENARIO_GENERATION
    SETTINGS
    Attempt: 1 | Reason: INITIAL
    Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years
    Wildcards: New pandemic 8 | Humanoid robot boom 6
    Cutoff date: 2026-10-07
    Future event date window: after 2026-10-07 and no later than 2031-10-07
    TASK
    Construct one scenario from the starting conditions in evidence-pack under the settings above.
    Cite Evidence IDs exactly as written in the pack. Use claim ids F1, F2, … for facts, I1, I2, … for inferences and P1, P2, … for speculations.
    <<<ORACUL_UNTRUSTED_DATA name="evidence-pack">>>
    ORACUL EVIDENCE PACK
    attachment: e2e/test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
==== END E2E FAILURES ====

```

## Round 3
- Trigger: E2E (related specs not green on the current inputs) RED
- To builders: yes · to tester: no
- Output:

```
NEEDED (1 of 26 specs): critic.spec.ts
    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/critic.spec.ts:128:3 › FR-22 Critic validation › FR-22 a failing critic triggers one regeneration with the critique attached 
  3 did not run
  1 passed (45.0s)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 46s
E2E FAIL
==== E2E FAILURES (1) ====
✘ critic.spec.ts › FR-22 a failing critic triggers one regeneration with the critique attached · chromium
    Error: expect(received).toHaveLength(expected)
    Expected length: 2
    Received length: 1
    Received array:  [{"input": [{"content": [{"text": "ORACUL REQUEST SCENARIO_GENERATION
    SETTINGS
    Attempt: 1 | Reason: INITIAL
    Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years
    Wildcards: New pandemic 8 | Humanoid robot boom 6
    Cutoff date: 2026-10-07
    Future event date window: after 2026-10-07 and no later than 2031-10-07
    TASK
    Construct one scenario from the starting conditions in evidence-pack under the settings above.
    Cite Evidence IDs exactly as written in the pack. Use claim ids F1, F2, … for facts, I1, I2, … for inferences and P1, P2, … for speculations.
    <<<ORACUL_UNTRUSTED_DATA name="evidence-pack">>>
    ORACUL EVIDENCE PACK
    attachment: e2e/test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
==== END E2E FAILURES ====

```

## Takeover
- Not clean after 3 round(s): e2e.
- Code kept, not parked. The orchestrator takes over (small targeted fix, independent delta review, bin/close-slice.mjs) or asks the user.
- Last output:

```
E2E (related specs not green on the current inputs) is RED:
NEEDED (1 of 26 specs): critic.spec.ts
    attachment #2: trace (application/zip) ─────────────────────────────────────────────────────────
    test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
    Usage:

        npx playwright show-trace test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip

    ────────────────────────────────────────────────────────────────────────────────────────────────

  1 failed
    [chromium] › tests/critic.spec.ts:128:3 › FR-22 Critic validation › FR-22 a failing critic triggers one regeneration with the critique attached 
  3 did not run
  1 passed (45.0s)
 Container oracul-engine-backend-1  Restarting
 Container oracul-engine-backend-1  Started

Playwright took 46s
E2E FAIL
==== E2E FAILURES (1) ====
✘ critic.spec.ts › FR-22 a failing critic triggers one regeneration with the critique attached · chromium
    Error: expect(received).toHaveLength(expected)
    Expected length: 2
    Received length: 1
    Received array:  [{"input": [{"content": [{"text": "ORACUL REQUEST SCENARIO_GENERATION
    SETTINGS
    Attempt: 1 | Reason: INITIAL
    Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years
    Wildcards: New pandemic 8 | Humanoid robot boom 6
    Cutoff date: 2026-10-07
    Future event date window: after 2026-10-07 and no later than 2031-10-07
    TASK
    Construct one scenario from the starting conditions in evidence-pack under the settings above.
    Cite Evidence IDs exactly as written in the pack. Use claim ids F1, F2, … for facts, I1, I2, … for inferences and P1, P2, … for speculations.
    <<<ORACUL_UNTRUSTED_DATA name="evidence-pack">>>
    ORACUL EVIDENCE PACK
    attachment: e2e/test-results/critic-FR-22-Critic-valida-22567--with-the-critique-attached-chromium/trace.zip
==== END E2E FAILURES ====

```
