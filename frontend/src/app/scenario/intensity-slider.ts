import { Component, input, output } from '@angular/core';
import { MatSliderModule } from '@angular/material/slider';

@Component({
  selector: 'app-intensity-slider',
  imports: [MatSliderModule],
  template: `
    <div class="row">
      <div class="label" [attr.data-testid]="'label-' + name()">
        <span [id]="'label-text-' + name()">{{ label() }}</span>
        <span [attr.data-testid]="'value-' + name()">{{ value() }}</span>
      </div>
      <mat-slider
        class="slider"
        [attr.data-testid]="'slider-' + name()"
        [min]="1"
        [max]="10"
        [step]="1"
        [discrete]="true"
      >
        <input
          matSliderThumb
          [attr.data-testid]="'slider-' + name() + '-input'"
          [attr.aria-label]="label()"
          [value]="value()"
          (input)="onInput($event)"
          (valueChange)="valueChange.emit($event)"
        />
      </mat-slider>
    </div>
  `,
  styles: `
    .row { display: flex; flex-direction: column; }
    .label { display: flex; gap: 6px; }
    .slider { width: 100%; margin: 0; }
  `,
})
export class IntensitySlider {
  readonly name = input.required<string>();
  readonly label = input.required<string>();
  readonly value = input.required<number>();
  readonly valueChange = output<number>();

  protected onInput(event: Event): void {
    const n = Number((event.target as HTMLInputElement).value);
    this.valueChange.emit(n);
  }
}
