import { TestBed } from '@angular/core/testing';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ScenarioStore } from './scenario.store';

interface CustomWildcard {
  label: string;
  intensity: number;
}

/** Custom wildcard surface of the store (slice 18); accessed loosely so the spec compiles before it exists. */
interface CustomStoreApi {
  customWildcards(): CustomWildcard[];
  addCustomWildcard(label: string): string | null;
  removeCustomWildcard(index: number): void;
  setCustomWildcardIntensity(index: number, n: number): void;
}

const NAME = 'Wildcard name must be 1–40 characters';
const MAX = 'At most 3 custom wildcards';
const DUP = 'This wildcard already exists';

describe('ScenarioStore custom wildcards', () => {
  let store: ScenarioStore;
  let cw: CustomStoreApi;

  const labels = () => cw.customWildcards().map((c) => c.label);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(ScenarioStore);
    cw = store as unknown as CustomStoreApi;
  });

  // @trace FR-5
  it('starts empty', () => {
    expect(cw.customWildcards()).toEqual([]);
    expect(store.configuration().customWildcards).toEqual([]);
  });

  // @trace FR-5
  it('adds a trimmed label with intensity 5 and returns null', () => {
    expect(cw.addCustomWildcard('  Ocean desalination boom ')).toBeNull();
    expect(cw.customWildcards()).toEqual([{ label: 'Ocean desalination boom', intensity: 5 }]);
    expect(store.configuration().customWildcards).toEqual(cw.customWildcards());
  });

  // @trace FR-5
  it('spec example: add, add trimmed, set intensity, reject case-insensitive duplicate', () => {
    cw.addCustomWildcard('Ocean desalination boom');
    cw.addCustomWildcard(' Mars colony ');
    cw.setCustomWildcardIntensity(1, 8);
    expect(cw.addCustomWildcard('OCEAN DESALINATION BOOM')).toBe(DUP);
    expect(cw.customWildcards()).toEqual([
      { label: 'Ocean desalination boom', intensity: 5 },
      { label: 'Mars colony', intensity: 8 },
    ]);
  });

  // @trace FR-5
  it('removes by index, later items shift down, a slot is free again', () => {
    for (const l of ['A', 'B', 'C']) cw.addCustomWildcard(l);
    expect(cw.addCustomWildcard('D')).toBe(MAX);
    cw.removeCustomWildcard(0);
    expect(labels()).toEqual(['B', 'C']);
    expect(cw.addCustomWildcard('D')).toBeNull();
    expect(labels()).toEqual(['B', 'C', 'D']);
  });

  // @trace FR-5
  it('out-of-range remove / intensity is a no-op', () => {
    cw.addCustomWildcard('A');
    cw.removeCustomWildcard(1);
    cw.removeCustomWildcard(-1);
    cw.setCustomWildcardIntensity(3, 7);
    cw.setCustomWildcardIntensity(-1, 7);
    expect(cw.customWildcards()).toEqual([{ label: 'A', intensity: 5 }]);
  });

  // @trace FR-5
  it.each([0, 11, 5.5, NaN, -1])('intensity %s is ignored', (n) => {
    cw.addCustomWildcard('A');
    cw.setCustomWildcardIntensity(0, n);
    expect(cw.customWildcards()[0].intensity).toBe(5);
  });

  // @trace FR-5
  it.each([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])('intensity %i is accepted', (n) => {
    cw.addCustomWildcard('A');
    cw.setCustomWildcardIntensity(0, n);
    expect(cw.customWildcards()[0].intensity).toBe(n);
  });

  // Ranges & invariants: label length classes
  // @trace FR-5
  it.each([
    ['', NAME],
    ['   ', NAME],
    ['a', null],
    ['a'.repeat(40), null],
    ['a'.repeat(41), NAME],
    ['  ' + 'a'.repeat(40) + '  ', null],
    ['  ' + 'a'.repeat(41) + '  ', NAME],
  ])('label %j -> %s', (label, expected) => {
    expect(cw.addCustomWildcard(label)).toBe(expected);
    expect(cw.customWildcards().length).toBe(expected === null ? 1 : 0);
    if (expected === null) expect(cw.customWildcards()[0].label).toBe(label.trim());
  });

  // @trace FR-5
  it('every label length 0..45 is classified by its trimmed length', () => {
    for (let len = 0; len <= 45; len++) {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({});
      const c = TestBed.inject(ScenarioStore) as unknown as CustomStoreApi;
      const result = c.addCustomWildcard(' ' + 'x'.repeat(len) + ' ');
      expect(result, `len ${len}`).toBe(len >= 1 && len <= 40 ? null : NAME);
    }
  });

  // @trace FR-5
  it('count classes 0..3 are accepted, the 4th Add is rejected with any input', () => {
    for (const [i, l] of ['A', 'B', 'C'].entries()) {
      expect(cw.addCustomWildcard(l)).toBeNull();
      expect(cw.customWildcards().length).toBe(i + 1);
    }
    for (const input of ['D', '', '   ', 'a'.repeat(41), 'a']) {
      expect(cw.addCustomWildcard(input)).toBe(MAX);
    }
    expect(labels()).toEqual(['A', 'B', 'C']);
  });

  // @trace FR-5
  it.each([
    ['mars colony', 'Mars Colony', true],
    ['Mars colony', 'Mars colony', true],
    ['Mars colony', '  Mars colony  ', true],
    ['Mars colony', 'MARS COLONY', true],
    ['Mars  colony', 'Mars colony', false],
    ['Mars colony', 'New pandemic', false],
  ])('duplicate check %j vs %j -> duplicate=%s', (first, second, dup) => {
    expect(cw.addCustomWildcard(first)).toBeNull();
    expect(cw.addCustomWildcard(second)).toBe(dup ? DUP : null);
  });

  // @trace FR-5
  it('check order: count, then length, then duplicate', () => {
    for (const l of ['A', 'B', 'C']) cw.addCustomWildcard(l);
    expect(cw.addCustomWildcard('a')).toBe(MAX);
    cw.removeCustomWildcard(2);
    expect(cw.addCustomWildcard('')).toBe(NAME);
    expect(cw.addCustomWildcard('a')).toBe(DUP);
  });

  // @trace FR-5
  it('a rejected add leaves the state unchanged', () => {
    cw.addCustomWildcard('A');
    const before = JSON.stringify(store.configuration());
    cw.addCustomWildcard('');
    cw.addCustomWildcard('a');
    expect(JSON.stringify(store.configuration())).toBe(before);
  });

  // @trace FR-5
  it('custom wildcard changes never touch other fields, and vice versa', () => {
    store.setRealism(3);
    store.setDarkness(7);
    store.setOptimism(9);
    store.setHorizon('5y');
    store.enableWildcard('biology-new-pandemic');
    const others = () => {
      const { customWildcards: _c, ...rest } = store.configuration();
      return JSON.stringify(rest);
    };
    const before = others();
    cw.addCustomWildcard('A');
    cw.addCustomWildcard('B');
    cw.setCustomWildcardIntensity(1, 9);
    cw.removeCustomWildcard(0);
    expect(others()).toBe(before);
    store.setRealism(4);
    store.disableWildcard('biology-new-pandemic');
    expect(cw.customWildcards()).toEqual([{ label: 'B', intensity: 9 }]);
  });

  // @trace FR-5
  it('load keeps customWildcards as given', () => {
    const config: ScenarioConfiguration = {
      realism: 8,
      darkness: 5,
      optimism: 5,
      horizon: '1y',
      wildcards: [],
      customWildcards: [{ label: 'Mars colony', intensity: 8 }],
      output: { story: true, illustration: false },
    };
    store.load(config);
    expect(cw.customWildcards()).toEqual([{ label: 'Mars colony', intensity: 8 }]);
    expect(store.configuration()).toEqual(config);
  });
});
