import { Component, computed, inject } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';

import { MOBILE_QUERY } from '../layout';
import { ScenarioStore } from './scenario.store';

@Component({
  selector: 'app-writing-style-section',
  imports: [MatRadioModule, MatSelectModule, MatFormFieldModule],
  template: `
    <div data-testid="writing-style-section" class="group">
      <h3 class="sub">Writing Style</h3>
      <mat-radio-group
        data-testid="writing-style-group"
        aria-label="Writing Style"
        class="radios"
        [value]="store.writingStyle().mode"
        (change)="store.setWritingMode($event.value)"
      >
        @for (m of modes(); track m.id) {
          <mat-radio-button [attr.data-testid]="'writing-style-option-' + m.id" [value]="m.id">{{
            m.label
          }}</mat-radio-button>
        }
      </mat-radio-group>
    </div>

    @if (store.writingStyle().mode === 'NARRATIVE') {
      <div data-testid="narrative-style-section" class="group">
        <h3 class="sub">Narrative Style</h3>
        @if (isMobile()) {
          <mat-form-field>
            <mat-select
              data-testid="narrative-style-select"
              aria-label="Narrative Style"
              [value]="store.writingStyle().profileId"
              (selectionChange)="store.setNarrativeProfile($event.value)"
            >
              @for (p of narrativeProfiles(); track p.id) {
                <mat-option
                  [attr.data-testid]="'narrative-style-select-option-' + p.id"
                  [value]="p.id"
                  >{{ p.label }}</mat-option
                >
              }
            </mat-select>
          </mat-form-field>
        } @else {
          <mat-radio-group
            data-testid="narrative-style-group"
            aria-label="Narrative Style"
            class="radios"
            [value]="store.writingStyle().profileId"
            (change)="store.setNarrativeProfile($event.value)"
          >
            @for (p of narrativeProfiles(); track p.id) {
              <mat-radio-button
                [attr.data-testid]="'narrative-style-option-' + p.id"
                [value]="p.id"
                >{{ p.label }}</mat-radio-button
              >
            }
          </mat-radio-group>
        }
      </div>
    }

    <mat-form-field class="lang">
      <mat-label>Story language</mat-label>
      <mat-select
        data-testid="story-language-select"
        [value]="store.language()"
        (selectionChange)="store.setLanguage($event.value)"
      >
        @for (l of languages(); track l.code) {
          <mat-option [attr.data-testid]="'story-language-option-' + l.code" [value]="l.code">{{
            l.label
          }}</mat-option>
        }
      </mat-select>
    </mat-form-field>
  `,
  styles: `
    .group,
    .lang {
      display: block;
    }
    .sub {
      font-size: 14px;
      margin: 12px 0 4px;
    }
    .radios {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }
    .lang {
      margin-top: 8px;
    }
  `,
})
export class WritingStyleSection {
  protected readonly store = inject(ScenarioStore);
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly isMobile = toSignal(
    this.breakpoints.observe(MOBILE_QUERY).pipe(map((b) => b.matches)),
    { initialValue: false },
  );
  protected readonly modes = computed(() => this.store.writingStyles()?.modes ?? []);
  protected readonly languages = computed(() => this.store.writingStyles()?.languages ?? []);
  protected readonly narrativeProfiles = computed(
    () => this.store.writingStyles()?.profiles.filter((p) => p.mode === 'NARRATIVE') ?? [],
  );
}
