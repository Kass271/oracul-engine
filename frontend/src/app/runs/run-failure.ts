import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';

@Component({
  selector: 'app-run-failure',
  imports: [MatButtonModule],
  template: `
    <div class="failure" data-testid="failure-view">
      <p data-testid="failure-message">Future not found</p>
      <button mat-flat-button data-testid="try-again" (click)="back()">Try again</button>
    </div>
  `,
  styles: `.failure { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; }`,
})
export class RunFailure {
  private readonly router = inject(Router);

  protected back(): void {
    void this.router.navigateByUrl('/');
  }
}
