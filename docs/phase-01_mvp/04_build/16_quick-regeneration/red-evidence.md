# Red evidence — 16_quick-regeneration

Date: 2026-10-03 · FRs: FR-29

## Tagged tests

- FR-29 → `e2e/tests/quick-regeneration.spec.ts` (e2e)
- FR-29 → `frontend/src/app/runs/quick-actions.spec.ts` (frontend)
- FR-29 → `frontend/src/app/runs/run-view.spec.ts` (frontend)

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[90m [2m❯[22m src/app/runs/quick-actions.spec.ts:[2m285:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[12/51]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/quick-actions.spec.ts[2m > [22mslice 16_quick-regeneration: quick actions bar[2m > [22m500 without an ApiError body: generic message, panel keeps the value
[31m[1mAssertionError[22m: quick-more-extreme exists: expected null not to be null[39m
[36m [2m❯[22m btn src/app/runs/quick-actions.spec.ts:[2m118:35[22m[39m
    [90m116|[39m   const btn = (id: string): HTMLButtonElement => {
    [90m117|[39m     const b = byId(id);
    [90m118|[39m     expect(b, `${id} exists`).not.toBeNull();
    [90m   |[39m                                   [31m^[39m
    [90m119|[39m     return b as HTMLButtonElement;
    [90m120|[39m   };
[90m [2m❯[22m click src/app/runs/quick-actions.spec.ts:[2m144:5[22m[39m
[90m [2m❯[22m src/app/runs/quick-actions.spec.ts:[2m297:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[13/51]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 16_quick-regeneration[2m > [22ma COMPLETED run shows quick-actions inside result-view
[31m[1mAssertionError[22m: expected +0 to be 1 // Object.is equality[39m

- Expected
+ Received

- 1
+ 0

[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m677:103[22m[39m
    [90m675|[39m     http.expectOne(resultGet).flush(futureResult());
    [90m676|[39m     await render();
    [90m677|[39m     expect(el().querySelectorAll('[data-testid="result-view"] [data-te…
    [90m   |[39m                                                                                                       [31m^[39m
    [90m678|[39m   });
    [90m679|[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[14/51]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/runs/run-view.spec.ts[2m > [22mslice 16_quick-regeneration[2m > [22mclick quick-darker with darkness 5 sends darkness 7, shows the new run and keeps 7 in the panel
[31m[1mTypeError[22m: Cannot read properties of null (reading 'click')[39m
[36m [2m❯[22m src/app/runs/run-view.spec.ts:[2m698:55[22m[39m
    [90m696|[39m     expect(TestBed.inject(ScenarioStore).darkness()).toBe(5);
    [90m697|[39m     vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInte…
    [90m698|[39m     (el().querySelector('[data-testid="quick-darker"]') as HTMLButtonE…
    [90m   |[39m                                                       [31m^[39m
    [90m699|[39m     await vi.advanceTimersByTimeAsync(0);
    [90m700|[39m     const req = http.expectOne(runsPost);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[15/51]⎯[22m[39m


```

RESULT: RED
