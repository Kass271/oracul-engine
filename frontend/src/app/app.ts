import { Component, OnInit, inject, signal } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

import { ChatGptConnectionComponent } from './chatgpt/chatgpt-connection';
import { ConnectionStore } from './chatgpt/connection.store';
import { WelcomeView } from './center/welcome-view';
import { RecentFuturesComponent } from './history/recent-futures';
import { MOBILE_QUERY } from './layout';
import { ScenarioPanel } from './scenario/scenario-panel';
import { ScenarioLoader } from './scenario/scenario.loader';

@Component({
  selector: 'app-root',
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    MatIconModule,
    ScenarioPanel,
    RouterOutlet,
    WelcomeView,
    ChatGptConnectionComponent,
    RecentFuturesComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class App implements OnInit {
  protected readonly loader = inject(ScenarioLoader);

  private readonly connection = inject(ConnectionStore);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly isMobile = signal(false);
  protected readonly drawerOpen = signal(false);

  /** Until the first navigation ends, the real browser path decides whether the welcome view shows. */
  protected readonly path = signal<'/' | 'other'>(window.location.pathname === '/' ? '/' : 'other');

  constructor() {
    this.loader.load();
    this.breakpoints
      .observe(MOBILE_QUERY)
      .pipe(takeUntilDestroyed())
      .subscribe((b) => {
        this.isMobile.set(b.matches);
        this.drawerOpen.set(false);
      });
    this.router.events.pipe(takeUntilDestroyed()).subscribe((e) => {
      if (e instanceof NavigationEnd)
        this.path.set(
          this.router.parseUrl(e.urlAfterRedirects).root.children['primary']?.segments.length
            ? 'other'
            : '/',
        );
    });
  }

  protected onEscape(): void {
    if (this.isMobile() && this.drawerOpen()) this.drawerOpen.set(false);
  }

  ngOnInit(): void {
    // The router has not navigated yet at bootstrap, so read the real browser URL until it has.
    const outcome = this.router.navigated
      ? this.router.parseUrl(this.router.url).queryParams['chatgpt']
      : new URLSearchParams(window.location.search).get('chatgpt');
    this.connection.handleReturn(typeof outcome === 'string' ? outcome : null);
  }
}
