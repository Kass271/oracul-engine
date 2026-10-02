import { Routes } from '@angular/router';

import { RunView } from './runs/run-view';

export const routes: Routes = [
  { path: '', pathMatch: 'full', children: [] },
  { path: 'futures/:runId', component: RunView },
  { path: '**', redirectTo: '' },
];
