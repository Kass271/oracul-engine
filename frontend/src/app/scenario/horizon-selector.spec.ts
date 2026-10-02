import { ComponentFixture, TestBed } from '@angular/core/testing';

import type { HorizonCode } from '../api/models/horizon-code';
import { HorizonSelector, type HorizonOption } from './horizon-selector';

const OPTIONS: HorizonOption[] = [
  { code: '1d', label: 'Tomorrow' },
  { code: '1w', label: '1 week' },
  { code: '1m', label: '1 month' },
  { code: '1y', label: '1 year' },
  { code: '5y', label: '5 years' },
  { code: '10y', label: '10 years' },
  { code: '20y', label: '20 years' },
];

// @trace FR-3
describe('HorizonSelector', () => {
  let fixture: ComponentFixture<HorizonSelector>;

  const el = (): HTMLElement => fixture.nativeElement;
  const option = (code: string): HTMLElement | null =>
    el().querySelector(`[data-testid="horizon-option-${code}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HorizonSelector] }).compileComponents();
    fixture = TestBed.createComponent(HorizonSelector);
    fixture.componentRef.setInput('options', OPTIONS);
    fixture.componentRef.setInput('value', '1y');
    fixture.detectChanges();
    await fixture.whenStable();
  });

  // @trace FR-3
  it('renders the 7 options in order with their labels', () => {
    const items = Array.from(el().querySelectorAll('mat-button-toggle'));
    expect(items.map((i) => i.textContent?.trim())).toEqual(OPTIONS.map((o) => o.label));
  });

  // @trace FR-3
  it('marks the current value as selected', () => {
    expect(option('1y')?.classList.contains('mat-button-toggle-checked')).toBe(true);
    expect(option('5y')?.classList.contains('mat-button-toggle-checked')).toBe(false);
  });

  // @trace FR-3
  it('emits valueChange with the code when another option is clicked', () => {
    const emitted: HorizonCode[] = [];
    fixture.componentInstance.valueChange.subscribe((c) => emitted.push(c));
    (option('5y')?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(emitted).toEqual(['5y']);
  });
});
