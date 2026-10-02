import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

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
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly loader = inject(ScenarioLoader);

  constructor() {
    this.loader.load();
  }
}
