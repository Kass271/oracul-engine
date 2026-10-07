import { Injectable, computed, signal } from '@angular/core';

import type { HorizonCode } from '../api/models/horizon-code';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import type { StoryLanguage } from '../api/models/story-language';
import type { WritingMode } from '../api/models/writing-mode';
import type { WritingStyleCatalogue } from '../api/models/writing-style-catalogue';
import type { WritingStyleSelection } from '../api/models/writing-style-selection';

const OFFICIAL_ID = 'official-briefing';
const OFFICIAL_SELECTION: WritingStyleSelection = { mode: 'OFFICIAL', profileId: OFFICIAL_ID };

export const HORIZON_CODES: readonly HorizonCode[] = ['1d', '1w', '1m', '1y', '5y', '10y', '20y'];

export const DEFAULT_CONFIGURATION: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: {
    story: true,
    illustration: false,
    writingStyle: { ...OFFICIAL_SELECTION },
    language: 'en',
  },
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

  readonly writingStyle = computed<WritingStyleSelection>(
    () => this.state().output.writingStyle ?? { ...OFFICIAL_SELECTION },
  );
  readonly language = computed<StoryLanguage>(() => this.state().output.language ?? 'en');

  private readonly styleCatalogue = signal<WritingStyleCatalogue | null>(null);
  readonly writingStyles = this.styleCatalogue.asReadonly();

  setWritingStyles(catalogue: WritingStyleCatalogue): void {
    this.styleCatalogue.set(catalogue);
  }

  private normaliseStyle(raw: unknown): WritingStyleSelection {
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return { ...OFFICIAL_SELECTION };
    const { mode, profileId } = raw as { mode?: unknown; profileId?: unknown };
    if (mode === 'OFFICIAL' && profileId === OFFICIAL_ID) return { mode, profileId };
    if (mode === 'NARRATIVE' && typeof profileId === 'string' && profileId !== '') {
      const cat = this.styleCatalogue();
      const ok = cat
        ? cat.profiles.some((p) => p.id === profileId && p.mode === 'NARRATIVE')
        : profileId !== OFFICIAL_ID;
      if (ok) return { mode, profileId };
    }
    return { ...OFFICIAL_SELECTION };
  }

  load(config: ScenarioConfiguration): void {
    const out = (config.output ?? {}) as Partial<ScenarioConfiguration['output']>;
    const lang = out.language as unknown;
    this.state.set({
      ...config,
      output: {
        ...config.output,
        writingStyle: this.normaliseStyle(out.writingStyle),
        language: lang === 'en' || lang === 'uk' ? lang : 'en',
      },
    });
  }

  setWritingMode(mode: WritingMode): void {
    if (mode !== 'OFFICIAL' && mode !== 'NARRATIVE') return;
    if (this.writingStyle().mode === mode) return;
    const profileId =
      mode === 'OFFICIAL'
        ? OFFICIAL_ID
        : (this.styleCatalogue()?.modes.find((m) => m.id === 'NARRATIVE')?.defaultProfileId ?? 'neutral-narrative');
    this.setOutput({ writingStyle: { mode, profileId } });
  }

  setNarrativeProfile(id: string): void {
    if (this.writingStyle().mode !== 'NARRATIVE') return;
    if (!this.styleCatalogue()?.profiles.some((p) => p.id === id && p.mode === 'NARRATIVE')) return;
    this.setOutput({ writingStyle: { mode: 'NARRATIVE', profileId: id } });
  }

  setLanguage(code: StoryLanguage): void {
    if (code === 'en' || code === 'uk') this.setOutput({ language: code });
  }

  private setOutput(patch: Partial<ScenarioConfiguration['output']>): void {
    this.state.update((s) => ({ ...s, output: { ...s.output, ...patch } }));
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
