import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';

import { ScenarioStore } from './scenario.store';

@Component({
  selector: 'app-custom-wildcards',
  imports: [MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatSliderModule],
  template: `
    <div data-testid="custom-wildcard-section">
      <div class="add">
        <mat-form-field class="field" subscriptSizing="dynamic">
          <input
            matInput
            data-testid="custom-wildcard-input"
            aria-label="Custom wildcard name"
            placeholder="e.g. Ocean desalination boom"
            [value]="text()"
            (input)="onType($event)"
            (keydown.enter)="add()"
          />
        </mat-form-field>
        <button mat-stroked-button type="button" data-testid="custom-wildcard-add" (click)="add()">
          Add
        </button>
      </div>
      @if (error()) {
        <div class="err" data-testid="custom-wildcard-error" role="alert">{{ error() }}</div>
      }
      @for (c of store.customWildcards(); track $index; let i = $index) {
        <div class="row" [attr.data-testid]="'custom-wildcard-' + i">
          <div class="head">
            <span [attr.data-testid]="'custom-wildcard-label-' + i"
              >{{ c.label }} {{ c.intensity }}/10</span
            >
            <button
              mat-icon-button
              type="button"
              [attr.data-testid]="'custom-wildcard-remove-' + i"
              [attr.aria-label]="'Remove ' + c.label"
              (click)="remove(i)"
            >
              <mat-icon>close</mat-icon>
            </button>
          </div>
          <mat-slider
            class="slider"
            [attr.data-testid]="'custom-wildcard-intensity-' + i"
            [min]="1"
            [max]="10"
            [step]="1"
            [discrete]="true"
          >
            <input
              matSliderThumb
              [attr.data-testid]="'custom-wildcard-intensity-' + i + '-input'"
              [attr.aria-label]="c.label + ' intensity'"
              [value]="c.intensity"
              (input)="onInput(i, $event)"
              (valueChange)="store.setCustomWildcardIntensity(i, $event)"
            />
          </mat-slider>
        </div>
      }
    </div>
  `,
  styles: `
    .add {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .field {
      flex: 1;
    }
    .err {
      color: var(--mat-sys-error, #b3261e);
      font-size: 12px;
      margin: 4px 0;
    }
    .row {
      display: flex;
      flex-direction: column;
    }
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .slider {
      width: 100%;
      margin: 0;
    }
  `,
})
export class CustomWildcards {
  protected readonly store = inject(ScenarioStore);
  protected readonly text = signal('');
  protected readonly error = signal<string | null>(null);

  protected onType(e: Event): void {
    this.text.set((e.target as HTMLInputElement).value);
    this.error.set(null);
  }

  protected add(): void {
    const err = this.store.addCustomWildcard(this.text());
    this.error.set(err);
    if (err === null) this.text.set('');
  }

  protected remove(i: number): void {
    this.store.removeCustomWildcard(i);
    this.error.set(null);
  }

  protected onInput(i: number, e: Event): void {
    this.store.setCustomWildcardIntensity(i, Number((e.target as HTMLInputElement).value));
  }
}
