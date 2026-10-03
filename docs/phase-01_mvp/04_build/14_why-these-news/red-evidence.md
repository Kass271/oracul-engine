# Red evidence — 14_why-these-news

Date: 2026-10-03 · FRs: FR-28

## Tagged tests

- FR-28 → `e2e/tests/why-these-news.spec.ts` (e2e)
- FR-28 → `frontend/src/app/result/why-news-panel.spec.ts` (frontend)

## frontend

- Command exit: 1
- Classification: FAIL (assertions / missing behaviour)

```
    [90m154|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m155|[39m     (byId(id) as HTMLElement).click();
    [90m156|[39m     await settle();
[90m [2m❯[22m openWith src/app/result/why-news-panel.spec.ts:[2m161:11[22m[39m
[90m [2m❯[22m src/app/result/why-news-panel.spec.ts:[2m299:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[12/34]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-news-panel.spec.ts[2m > [22mslice 14_why-these-news: WHY THESE NEWS? panel[2m > [22mN10 descriptions and drivers are rendered as text, never markup
[31m[1mAssertionError[22m: missing open-why-news: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-news-panel.spec.ts:[2m154:43[22m[39m
    [90m152|[39m
    [90m153|[39m   async function click(id: string): Promise<void> {
    [90m154|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m155|[39m     (byId(id) as HTMLElement).click();
    [90m156|[39m     await settle();
[90m [2m❯[22m openWith src/app/result/why-news-panel.spec.ts:[2m161:11[22m[39m
[90m [2m❯[22m src/app/result/why-news-panel.spec.ts:[2m307:5[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[13/34]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-news-panel.spec.ts[2m > [22mslice 14_why-these-news: WHY THESE NEWS? panel[2m > [22mN11 the button toggles only its own panel
[31m[1mAssertionError[22m: missing open-why-news: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-news-panel.spec.ts:[2m154:43[22m[39m
    [90m152|[39m
    [90m153|[39m   async function click(id: string): Promise<void> {
    [90m154|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m155|[39m     (byId(id) as HTMLElement).click();
    [90m156|[39m     await settle();
[90m [2m❯[22m src/app/result/why-news-panel.spec.ts:[2m322:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[14/34]⎯[22m[39m

[41m[1m FAIL [22m[49m [30m[43m frontend [49m[39m src/app/result/why-news-panel.spec.ts[2m > [22mslice 14_why-these-news: WHY THESE NEWS? panel[2m > [22mN12 makes no extra HTTP call and keeps panel order
[31m[1mAssertionError[22m: missing open-why-news: expected null not to be null[39m
[36m [2m❯[22m click src/app/result/why-news-panel.spec.ts:[2m154:43[22m[39m
    [90m152|[39m
    [90m153|[39m   async function click(id: string): Promise<void> {
    [90m154|[39m     expect(byId(id), `missing ${id}`).not.toBeNull();
    [90m   |[39m                                           [31m^[39m
    [90m155|[39m     (byId(id) as HTMLElement).click();
    [90m156|[39m     await settle();
[90m [2m❯[22m src/app/result/why-news-panel.spec.ts:[2m336:11[22m[39m

[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[15/34]⎯[22m[39m


```

RESULT: RED
