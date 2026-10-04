import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { ConnectionStore } from './connection.store';

@Component({
  selector: 'app-reset-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Reset ChatGPT connection?</h2>
    <mat-dialog-content data-testid="chatgpt-reset-dialog">
      ORACUL forgets its ChatGPT registration and signs you out. The next Continue with ChatGPT asks you to connect ORACUL again.
    </mat-dialog-content>
    <mat-dialog-actions>
      <button mat-button data-testid="chatgpt-reset-cancel" mat-dialog-close>Cancel</button>
      <button mat-flat-button data-testid="chatgpt-reset-confirm" [disabled]="store.resetting()" (click)="confirm()">
        Reset
      </button>
    </mat-dialog-actions>
  `,
})
export class ResetDialog {
  protected readonly store = inject(ConnectionStore);
  private readonly ref = inject(MatDialogRef<ResetDialog>);

  protected confirm(): void {
    if (this.store.resetting()) return;
    this.store.reset().subscribe({ complete: () => this.ref.close() });
  }
}
