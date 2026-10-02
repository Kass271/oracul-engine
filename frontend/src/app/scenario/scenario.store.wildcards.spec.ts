import { TestBed } from '@angular/core/testing';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ScenarioStore } from './scenario.store';

interface WildcardSetting {
  wildcardId: string;
  intensity: number;
}

/** Wildcard surface of the store (slice 03); accessed loosely so the spec compiles before it exists. */
interface WildcardStoreApi {
  wildcards(): WildcardSetting[];
  isWildcardEnabled(id: string): boolean;
  wildcardIntensity(id: string): number | null;
  enableWildcard(id: string): void;
  disableWildcard(id: string): void;
  setWildcardIntensity(id: string, n: number): void;
}

const PANDEMIC = 'biology-new-pandemic';
const HUMANOID = 'robotics-humanoid-boom';
const STAGNATION = 'ai-stagnation';

describe('ScenarioStore wildcards', () => {
  let store: ScenarioStore;
  let wc: WildcardStoreApi;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(ScenarioStore);
    wc = store as unknown as WildcardStoreApi;
  });

  // @trace FR-4
  it('starts with no wildcards enabled', () => {
    expect(wc.wildcards()).toEqual([]);
    expect(wc.isWildcardEnabled(PANDEMIC)).toBe(false);
    expect(wc.wildcardIntensity(PANDEMIC)).toBeNull();
  });

  // @trace FR-4
  it('enableWildcard appends the wildcard with intensity 5', () => {
    wc.enableWildcard(PANDEMIC);
    expect(wc.isWildcardEnabled(PANDEMIC)).toBe(true);
    expect(wc.wildcardIntensity(PANDEMIC)).toBe(5);
    expect(wc.wildcards()).toEqual([{ wildcardId: PANDEMIC, intensity: 5 }]);
    expect(store.configuration().wildcards).toEqual(wc.wildcards());
  });

  // @trace FR-4
  it('enableWildcard is a no-op when already enabled and keeps the intensity', () => {
    wc.enableWildcard(PANDEMIC);
    wc.setWildcardIntensity(PANDEMIC, 8);
    wc.enableWildcard(PANDEMIC);
    expect(wc.wildcards()).toEqual([{ wildcardId: PANDEMIC, intensity: 8 }]);
  });

  // @trace FR-4
  it('disableWildcard removes the entry and forgets its intensity', () => {
    wc.enableWildcard(PANDEMIC);
    wc.setWildcardIntensity(PANDEMIC, 8);
    wc.disableWildcard(PANDEMIC);
    expect(wc.isWildcardEnabled(PANDEMIC)).toBe(false);
    expect(wc.wildcardIntensity(PANDEMIC)).toBeNull();
    expect(wc.wildcards()).toEqual([]);
    wc.enableWildcard(PANDEMIC);
    expect(wc.wildcardIntensity(PANDEMIC)).toBe(5);
  });

  // @trace FR-4
  it('disableWildcard is a no-op for a wildcard that is not enabled', () => {
    wc.enableWildcard(PANDEMIC);
    wc.disableWildcard(HUMANOID);
    expect(wc.wildcards()).toEqual([{ wildcardId: PANDEMIC, intensity: 5 }]);
  });

  // @trace FR-4
  it('setWildcardIntensity accepts integers 1-10 only', () => {
    wc.enableWildcard(PANDEMIC);
    wc.setWildcardIntensity(PANDEMIC, 8);
    for (const bad of [0, 11, 5.5, Number.NaN]) {
      wc.setWildcardIntensity(PANDEMIC, bad);
      expect(wc.wildcardIntensity(PANDEMIC)).toBe(8);
    }
    wc.setWildcardIntensity(PANDEMIC, 1);
    expect(wc.wildcardIntensity(PANDEMIC)).toBe(1);
    wc.setWildcardIntensity(PANDEMIC, 10);
    expect(wc.wildcardIntensity(PANDEMIC)).toBe(10);
  });

  // @trace FR-4
  it('setWildcardIntensity is a no-op for a wildcard that is not enabled', () => {
    wc.setWildcardIntensity(PANDEMIC, 8);
    expect(wc.wildcards()).toEqual([]);
    expect(wc.wildcardIntensity(PANDEMIC)).toBeNull();
  });

  // @trace FR-4
  it('keeps enable order and position when intensities change; disabled wildcards are dropped', () => {
    wc.enableWildcard(PANDEMIC);
    wc.setWildcardIntensity(PANDEMIC, 8);
    wc.enableWildcard(HUMANOID);
    wc.setWildcardIntensity(HUMANOID, 6);
    wc.enableWildcard(STAGNATION);
    wc.disableWildcard(STAGNATION);
    wc.setWildcardIntensity(PANDEMIC, 9);
    expect(store.configuration().wildcards).toEqual([
      { wildcardId: PANDEMIC, intensity: 9 },
      { wildcardId: HUMANOID, intensity: 6 },
    ]);
  });

  // @trace FR-4
  it('wildcard changes leave the other settings untouched, and vice versa', () => {
    store.setDarkness(9);
    wc.enableWildcard(PANDEMIC);
    wc.setWildcardIntensity(PANDEMIC, 3);
    const c = store.configuration();
    expect(c).toMatchObject({ realism: 8, darkness: 9, optimism: 5, horizon: '1y', customWildcards: [] });
    expect(c.output).toEqual({ story: true, illustration: false });
    store.setRealism(2);
    store.setHorizon('5y');
    expect(wc.wildcards()).toEqual([{ wildcardId: PANDEMIC, intensity: 3 }]);
  });

  // @trace FR-4
  it('load() brings in enabled wildcards with their intensities', () => {
    const config: ScenarioConfiguration = {
      realism: 8,
      darkness: 5,
      optimism: 5,
      horizon: '1y',
      wildcards: [
        { wildcardId: HUMANOID, intensity: 6 },
        { wildcardId: PANDEMIC, intensity: 8 },
      ],
      customWildcards: [],
      output: { story: true, illustration: false },
    };
    store.load(config);
    expect(wc.wildcards()).toEqual(config.wildcards);
    expect(wc.isWildcardEnabled(PANDEMIC)).toBe(true);
    expect(wc.wildcardIntensity(HUMANOID)).toBe(6);
    expect(wc.wildcardIntensity(STAGNATION)).toBeNull();
  });
});
