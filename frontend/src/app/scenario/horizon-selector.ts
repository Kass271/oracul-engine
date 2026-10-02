import { Component, input, output } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

import type { HorizonCode } from '../api/models/horizon-code';

export interface HorizonOption {
  code: HorizonCode;
  label: string;
}

@Component({
  selector: 'app-horizon-selector',
  imports: [MatButtonToggleModule],
  template: `
    <mat-button-toggle-group
      class="horizon"
      data-testid="horizon-group"
      aria-label="Time Horizon"
      [value]="value()"
      (change)="select($event.value)"
    >
      @for (h of options(); track h.code) {
        <mat-button-toggle [value]="h.code" [attr.data-testid]="'horizon-option-' + h.code">{{
          h.label
        }}</mat-button-toggle>
      }
    </mat-button-toggle-group>
  `,
  styles: `.horizon { flex-wrap: wrap; }`,
})
export class HorizonSelector {
  readonly options = input.required<readonly HorizonOption[]>();
  readonly value = input.required<HorizonCode>();
  readonly valueChange = output<HorizonCode>();

  protected select(code: HorizonCode | null): void {
    if (code) this.valueChange.emit(code);
  }
}
