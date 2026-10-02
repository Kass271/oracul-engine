import { Component, inject } from '@angular/core';

import { WildcardCatalogue } from './wildcard-catalogue';
import { HorizonSelector } from './horizon-selector';
import { IntensitySlider } from './intensity-slider';
import { ScenarioLoader } from './scenario.loader';
import { ScenarioStore } from './scenario.store';

@Component({
  selector: 'app-scenario-panel',
  imports: [IntensitySlider, HorizonSelector, WildcardCatalogue],
  templateUrl: './scenario-panel.html',
  styleUrl: './scenario-panel.scss',
})
export class ScenarioPanel {
  protected readonly store = inject(ScenarioStore);
  protected readonly loader = inject(ScenarioLoader);
}
