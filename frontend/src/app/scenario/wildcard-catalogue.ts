import { Component, inject, input } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';

import { ScenarioStore } from './scenario.store';

interface WildcardItem {
  id: string;
  label: string;
}
interface WildcardCategory {
  id: string;
  label: string;
  wildcards: readonly WildcardItem[];
}

@Component({
  selector: 'app-wildcard-catalogue',
  imports: [MatExpansionModule, MatSlideToggleModule, MatSliderModule],
  template: `
    <div data-testid="wildcard-section">
      @for (c of categories(); track c.id) {
        <mat-expansion-panel [attr.data-testid]="'wildcard-category-' + c.id">
          <mat-expansion-panel-header [attr.data-testid]="'wildcard-category-header-' + c.id">
            <mat-panel-title>{{ c.label }}</mat-panel-title>
          </mat-expansion-panel-header>
          @for (w of c.wildcards; track w.id) {
            <div class="wc">
              <mat-slide-toggle
                [attr.data-testid]="'wildcard-toggle-' + w.id"
                [aria-label]="w.label"
                [checked]="store.isWildcardEnabled(w.id)"
                (change)="toggle(w.id, $event.checked)"
              ></mat-slide-toggle>
              <span class="label" [attr.data-testid]="'wildcard-label-' + w.id">{{ labelOf(w) }}</span>
              @if (store.isWildcardEnabled(w.id)) {
                <mat-slider
                  class="slider"
                  [attr.data-testid]="'wildcard-intensity-' + w.id"
                  [min]="1"
                  [max]="10"
                  [step]="1"
                  [discrete]="true"
                >
                  <input
                    matSliderThumb
                    [attr.data-testid]="'wildcard-intensity-' + w.id + '-input'"
                    [attr.aria-label]="w.label + ' intensity'"
                    [value]="store.wildcardIntensity(w.id) ?? 5"
                    (input)="onInput(w.id, $event)"
                    (valueChange)="store.setWildcardIntensity(w.id, $event)"
                  />
                </mat-slider>
              }
            </div>
          }
        </mat-expansion-panel>
      }
    </div>
  `,
  styles: `
    .wc { display: flex; flex-direction: column; }
    .label { margin-left: 8px; }
    .slider { width: 100%; margin: 0; }
  `,
})
export class WildcardCatalogue {
  protected readonly store = inject(ScenarioStore);
  readonly categories = input.required<readonly WildcardCategory[]>();

  protected labelOf(w: WildcardItem): string {
    const n = this.store.wildcardIntensity(w.id);
    return n === null ? w.label : `${w.label} ${n}/10`;
  }

  protected toggle(id: string, on: boolean): void {
    if (on) this.store.enableWildcard(id);
    else this.store.disableWildcard(id);
  }

  protected onInput(id: string, e: Event): void {
    this.store.setWildcardIntensity(id, Number((e.target as HTMLInputElement).value));
  }
}
