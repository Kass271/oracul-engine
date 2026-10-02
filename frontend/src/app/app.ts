import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

import { ChatGptConnectionComponent } from './chatgpt/chatgpt-connection';
import { ConnectionStore } from './chatgpt/connection.store';
import { WelcomeView } from './center/welcome-view';
import { ScenarioPanel } from './scenario/scenario-panel';
import { ScenarioLoader } from './scenario/scenario.loader';

@Component({
  selector: 'app-root',
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    ScenarioPanel,
    WelcomeView,
    ChatGptConnectionComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly loader = inject(ScenarioLoader);

  private readonly connection = inject(ConnectionStore);
  private readonly router = inject(Router);

  constructor() {
    this.loader.load();
  }

  ngOnInit(): void {
    // The router has not navigated yet at bootstrap, so read the real browser URL until it has.
    const outcome = this.router.navigated
      ? this.router.parseUrl(this.router.url).queryParams['chatgpt']
      : new URLSearchParams(window.location.search).get('chatgpt');
    this.connection.handleReturn(typeof outcome === 'string' ? outcome : null);
  }
}
