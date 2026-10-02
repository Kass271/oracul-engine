import { Component, inject } from '@angular/core';
import { MAT_SNACK_BAR_DATA } from '@angular/material/snack-bar';

@Component({
  selector: 'app-chatgpt-message',
  template: `<span data-testid="chatgpt-message">{{ text }}</span>`,
})
export class ChatGptMessage {
  protected readonly text = inject<string>(MAT_SNACK_BAR_DATA);
}
