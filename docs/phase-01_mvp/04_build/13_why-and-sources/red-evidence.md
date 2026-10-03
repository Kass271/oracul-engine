# Red evidence — 13_why-and-sources

Date: 2026-10-03 · FRs: FR-26, FR-27

## Tagged tests

- FR-26 → `e2e/tests/why-and-sources.spec.ts` (e2e)
- FR-26 → `frontend/src/app/result/why-panel.spec.ts` (frontend)
- FR-27 → `e2e/tests/why-and-sources.spec.ts` (e2e)
- FR-27 → `frontend/src/app/result/sources-panel.spec.ts` (frontend)

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR2 a FACT step with 0 evidence ids renders that many chips in order
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR2 a FACT step with 1 evidence ids renders that many chips in order
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR2 a FACT step with 2 evidence ids renders that many chips in order
[31m[1mAssertionError[22m: missing open-why: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-panel.spec.ts:[2m106:43[22m[39m
    [90m104|[39m
    [90m105|[39m   async function click(id: string): Promise<void> {
    [90m106|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m107|[39m     (byId(id) as HTMLElement).click();
    [90m108|[39m     await settle();
[90m [2m❯[22m src/app/result/why-panel.spec.ts:[2m314:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[29/69]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR3 a chain of 2 steps renders one card each in order and n-1 arrows
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR3 a chain of 3 steps renders one card each in order and n-1 arrows
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR3 a chain of 5 steps renders one card each in order and n-1 arrows
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR3 a chain of 8 steps renders one card each in order and n-1 arrows
[31m[1mAssertionError[22m: missing open-why: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-panel.spec.ts:[2m106:43[22m[39m
    [90m104|[39m
    [90m105|[39m   async function click(id: string): Promise<void> {
    [90m106|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m107|[39m     (byId(id) as HTMLElement).click();
    [90m108|[39m     await settle();
[90m [2m❯[22m src/app/result/why-panel.spec.ts:[2m328:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[30/69]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E001 is enabled iff it is the id of a source (true)
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E002 is enabled iff it is the id of a source (true)
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E003 is enabled iff it is the id of a source (true)
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E004 is enabled iff it is the id of a source (true)
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E005 is enabled iff it is the id of a source (false)
[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-panel.spec.ts[2m > [22mslice 13_why-and-sources: WHY panel[2m > [22mR4 chip E999 is enabled iff it is the id of a source (false)
[31m[1mAssertionError[22m: missing open-why: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-panel.spec.ts:[2m106:43[22m[39m
    [90m104|[39m
    [90m105|[39m   async function click(id: string): Promise<void> {
    [90m106|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m107|[39m     (byId(id) as HTMLElement).click();
    [90m108|[39m     await settle();
[90m [2m❯[22m src/app/result/why-panel.spec.ts:[2m348:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[31/69]⎯[22m[39m


```

RESULT: RED
