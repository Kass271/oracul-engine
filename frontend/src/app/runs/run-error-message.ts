import { Component, inject } from '@angular/core';
import { MAT_SNACK_BAR_DATA } from '@angular/material/snack-bar';

@Component({
  selector: 'app-run-error-message',
  template: `<span data-testid="run-error-message">{{ text }}</span>`,
})
export class RunErrorMessage {
  protected readonly text = inject<string>(MAT_SNACK_BAR_DATA);
}
