import { Component } from '@angular/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';

@Component({
  selector: 'app-output-settings',
  imports: [MatCheckboxModule, MatChipsModule],
  template: `
    <div data-testid="output-section" class="out">
      <mat-checkbox data-testid="output-story" #story [checked]="true" (change)="story.checked = true">Story</mat-checkbox>
      <div class="ill">
        <mat-checkbox
          data-testid="output-illustration"
          [checked]="false"
          [disabled]="true"
          >Illustration</mat-checkbox
        >
        <mat-chip data-testid="output-illustration-badge">MVP+1</mat-chip>
      </div>
    </div>
  `,
  styles: `
    .out { display: flex; flex-direction: column; }
    .ill { display: flex; align-items: center; gap: 8px; }
  `,
})
export class OutputSettings {}
