import { Injectable, computed, signal } from '@angular/core';

import type { HorizonCode } from '../api/models/horizon-code';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';

export const HORIZON_CODES: readonly HorizonCode[] = ['1d', '1w', '1m', '1y', '5y', '10y', '20y'];

export const DEFAULT_CONFIGURATION: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

function validIntensity(n: unknown): n is number {
  return typeof n === 'number' && Number.isInteger(n) && n >= 1 && n <= 10;
}

@Injectable({ providedIn: 'root' })
export class ScenarioStore {
  private readonly state = signal<ScenarioConfiguration>({ ...DEFAULT_CONFIGURATION });

  readonly realism = computed(() => this.state().realism);
  readonly darkness = computed(() => this.state().darkness);
  readonly optimism = computed(() => this.state().optimism);
  readonly horizon = computed<HorizonCode>(() => this.state().horizon);
  readonly configuration = computed<ScenarioConfiguration>(() => this.state());

  load(config: ScenarioConfiguration): void {
    this.state.set({ ...config });
  }

  setRealism(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, realism: n }));
  }

  setDarkness(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, darkness: n }));
  }

  setOptimism(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, optimism: n }));
  }

  setHorizon(code: HorizonCode): void {
    if (HORIZON_CODES.includes(code)) this.state.update((s) => ({ ...s, horizon: code }));
  }

  readonly wildcards = computed(() => this.state().wildcards);

  isWildcardEnabled(id: string): boolean {
    return this.state().wildcards.some((w) => w.wildcardId === id);
  }

  wildcardIntensity(id: string): number | null {
    return this.state().wildcards.find((w) => w.wildcardId === id)?.intensity ?? null;
  }

  enableWildcard(id: string): void {
    if (this.isWildcardEnabled(id)) return;
    this.state.update((s) => ({ ...s, wildcards: [...s.wildcards, { wildcardId: id, intensity: 5 }] }));
  }

  disableWildcard(id: string): void {
    if (!this.isWildcardEnabled(id)) return;
    this.state.update((s) => ({ ...s, wildcards: s.wildcards.filter((w) => w.wildcardId !== id) }));
  }

  setWildcardIntensity(id: string, n: number): void {
    if (!validIntensity(n) || !this.isWildcardEnabled(id)) return;
    this.state.update((s) => ({
      ...s,
      wildcards: s.wildcards.map((w) => (w.wildcardId === id ? { ...w, intensity: n } : w)),
    }));
  }

  readonly customWildcards = computed(() => this.state().customWildcards);

  /** Returns an error message, or null when added. Check order: count, length, duplicate. */
  addCustomWildcard(raw: string): string | null {
    const list = this.state().customWildcards;
    if (list.length >= 3) return 'At most 3 custom wildcards';
    const label = raw.trim();
    if (label.length < 1 || label.length > 40) return 'Wildcard name must be 1–40 characters';
    if (list.some((c) => c.label.trim().toLowerCase() === label.toLowerCase())) {
      return 'This wildcard already exists';
    }
    this.state.update((s) => ({ ...s, customWildcards: [...s.customWildcards, { label, intensity: 5 }] }));
    return null;
  }

  removeCustomWildcard(index: number): void {
    if (!Number.isInteger(index) || index < 0 || index >= this.state().customWildcards.length) return;
    this.state.update((s) => ({ ...s, customWildcards: s.customWildcards.filter((_, i) => i !== index) }));
  }

  setCustomWildcardIntensity(index: number, n: number): void {
    if (!validIntensity(n) || !Number.isInteger(index) || index < 0 || index >= this.state().customWildcards.length) return;
    this.state.update((s) => ({
      ...s,
      customWildcards: s.customWildcards.map((c, i) => (i === index ? { ...c, intensity: n } : c)),
    }));
  }
}
