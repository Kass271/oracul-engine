import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IntensitySlider } from './intensity-slider';

// @trace FR-2
describe('IntensitySlider', () => {
  let fixture: ComponentFixture<IntensitySlider>;

  const byId = (id: string): HTMLElement | null =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [IntensitySlider] }).compileComponents();
    fixture = TestBed.createComponent(IntensitySlider);
    fixture.componentRef.setInput('name', 'realism');
    fixture.componentRef.setInput('label', 'Realism');
    fixture.componentRef.setInput('value', 8);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  // @trace FR-2
  it('shows the label and the current value', () => {
    expect(byId('label-realism')?.textContent).toContain('Realism');
    expect(byId('value-realism')?.textContent?.trim()).toBe('8');
  });

  // @trace FR-2
  it('exposes a 1-10 integer range with the value on the input', () => {
    const input = byId('slider-realism-input') as HTMLInputElement;
    expect(input.min).toBe('1');
    expect(input.max).toBe('10');
    expect(input.step).toBe('1');
    expect(input.value).toBe('8');
    expect(input.getAttribute('aria-label')).toBe('Realism');
  });

  // @trace FR-2
  it('emits valueChange when the user moves the thumb', () => {
    const emitted: number[] = [];
    fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
    const input = byId('slider-realism-input') as HTMLInputElement;
    input.value = '3';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(emitted).toContain(3);
  });

  // @trace FR-2
  it('reflects an updated value input', async () => {
    fixture.componentRef.setInput('value', 2);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(byId('value-realism')?.textContent?.trim()).toBe('2');
  });
});
