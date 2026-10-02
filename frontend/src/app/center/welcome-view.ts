import { Component } from '@angular/core';
import { GenerateButton } from '../runs/generate-button';

@Component({
  selector: 'app-welcome-view',
  imports: [GenerateButton],
  template: `
    <div class="center" data-testid="welcome-view">
      <h1 data-testid="welcome-question">What happens next?</h1>
      <app-generate-button />
    </div>
  `,
  styles: `
    .center { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; text-align: center; padding: 16px; }
  `,
})
export class WelcomeView {}
