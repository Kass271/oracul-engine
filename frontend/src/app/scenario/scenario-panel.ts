import { Component, inject } from '@angular/core';

import { CustomWildcards } from './custom-wildcards';
import { OutputSettings } from './output-settings';
import { WildcardCatalogue } from './wildcard-catalogue';
import { HorizonSelector } from './horizon-selector';
import { IntensitySlider } from './intensity-slider';
import { ScenarioLoader } from './scenario.loader';
import { ScenarioStore } from './scenario.store';

@Component({
  selector: 'app-scenario-panel',
  imports: [IntensitySlider, HorizonSelector, WildcardCatalogue, CustomWildcards, OutputSettings],
  templateUrl: './scenario-panel.html',
  styleUrl: './scenario-panel.scss',
})
export class ScenarioPanel {
  protected readonly store = inject(ScenarioStore);
  protected readonly loader = inject(ScenarioLoader);
}
