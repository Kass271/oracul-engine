import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-welcome-view',
  imports: [MatButtonModule],
  template: `
    <div class="center" data-testid="welcome-view">
      <h1 data-testid="welcome-question">What happens next?</h1>
      <button mat-flat-button disabled data-testid="generate-button">GENERATE THE FUTURE</button>
      <p class="hint" data-testid="generate-hint">Connect ChatGPT to generate</p>
    </div>
  `,
  styles: `
    .center { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; text-align: center; padding: 16px; }
    .hint { color: var(--mat-sys-on-surface-variant); margin: 0; }
  `,
})
export class WelcomeView {}
