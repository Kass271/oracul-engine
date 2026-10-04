# Red evidence — 19_mobile-layout

Date: 2026-10-04 · FRs: FR-34

## Tagged tests

- FR-34 → `e2e/tests/mobile-layout.spec.ts` (e2e)
- FR-34 → `frontend/src/app/app.mobile.spec.ts` (frontend)

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.mobile.spec.ts[2m > [22mApp mobile layout (slice 19_mobile-layout)[2m > [22mmobile: over drawer, closed on load, toggle "Scenario" first in header before wordmark
[31m[1mAssertionError[22m: expected [ '' ] to include 'mat-drawer-over'[39m
[36m [2m❯[22m src/app/app.mobile.spec.ts:[2m105:29[22m[39m
    [90m103|[39m     setup(true);
    [90m104|[39m     await startLoaded();
    [90m105|[39m     expect(drawerClasses()).toContain('mat-drawer-over');
    [90m   |[39m                             [31m^[39m
    [90m106|[39m     expect(drawerClasses()).not.toContain('mat-drawer-opened');
    [90m107|[39m     const toggle = byId('scenario-drawer-toggle');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[2/5]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.mobile.spec.ts[2m > [22mApp mobile layout (slice 19_mobile-layout)[2m > [22mmobile: toggle opens, close button closes, aria-expanded follows
[31m[1mTypeError[22m: Cannot read properties of null (reading 'click')[39m
[36m [2m❯[22m src/app/app.mobile.spec.ts:[2m141:34[22m[39m
    [90m139|[39m     setup(true);
    [90m140|[39m     await startLoaded();
    [90m141|[39m     byId('scenario-drawer-toggle')!.click();
    [90m   |[39m                                  [31m^[39m
    [90m142|[39m     await settle();
    [90m143|[39m     expect(drawerClasses()).toContain('mat-drawer-opened');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[3/5]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.mobile.spec.ts[2m > [22mApp mobile layout (slice 19_mobile-layout)[2m > [22mmobile: darkness set while open survives close and reopen
[31m[1mTypeError[22m: Cannot read properties of null (reading 'click')[39m
[36m [2m❯[22m src/app/app.mobile.spec.ts:[2m159:34[22m[39m
    [90m157|[39m     await startLoaded();
    [90m158|[39m     const store = TestBed.inject(ScenarioStore);
    [90m159|[39m     byId('scenario-drawer-toggle')!.click();
    [90m   |[39m                                  [31m^[39m
    [90m160|[39m     await settle();
    [90m161|[39m     setDarkness(9);

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[4/5]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/app.mobile.spec.ts[2m > [22mApp mobile layout (slice 19_mobile-layout)[2m > [22mcrossing the breakpoint switches mode; entering mobile starts closed
[31m[1mTypeError[22m: Cannot read properties of null (reading 'click')[39m
[36m [2m❯[22m src/app/app.mobile.spec.ts:[2m176:34[22m[39m
    [90m174|[39m     setup(true);
    [90m175|[39m     await startLoaded();
    [90m176|[39m     byId('scenario-drawer-toggle')!.click();
    [90m   |[39m                                  [31m^[39m
    [90m177|[39m     await settle();
    [90m178|[39m     expect(drawerClasses()).toContain('mat-drawer-opened');

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[5/5]⎯[22m[39m


```

RESULT: RED
