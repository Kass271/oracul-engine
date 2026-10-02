import { TestBed } from '@angular/core/testing';

import type { HorizonCode } from '../api/models/horizon-code';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ScenarioStore } from './scenario.store';

const snapshot = (s: ScenarioStore) => ({
  realism: s.realism(),
  darkness: s.darkness(),
  optimism: s.optimism(),
  horizon: s.horizon(),
});

describe('ScenarioStore', () => {
  let store: ScenarioStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(ScenarioStore);
  });

  // @trace FR-2
  // @trace FR-3
  it('holds the defaults 8/5/5/1y before load', () => {
    expect(snapshot(store)).toEqual({ realism: 8, darkness: 5, optimism: 5, horizon: '1y' });
  });

  // @trace FR-2
  // @trace FR-3
  it('load(config) replaces the whole state', () => {
    const config: ScenarioConfiguration = {
      realism: 2,
      darkness: 9,
      optimism: 1,
      horizon: '20y',
      wildcards: [],
      customWildcards: [],
      output: { story: false, illustration: true },
    };
    store.load(config);
    expect(snapshot(store)).toEqual({ realism: 2, darkness: 9, optimism: 1, horizon: '20y' });
    expect(store.configuration()).toEqual(config);
  });

  // @trace FR-2
  it('each intensity setter changes only its own field', () => {
    store.setRealism(3);
    expect(snapshot(store)).toEqual({ realism: 3, darkness: 5, optimism: 5, horizon: '1y' });
    store.setDarkness(7);
    expect(snapshot(store)).toEqual({ realism: 3, darkness: 7, optimism: 5, horizon: '1y' });
    store.setOptimism(10);
    expect(snapshot(store)).toEqual({ realism: 3, darkness: 7, optimism: 10, horizon: '1y' });
  });

  // @trace FR-2
  it('accepts the boundary values 1 and 10', () => {
    store.setRealism(1);
    store.setDarkness(10);
    expect(store.realism()).toBe(1);
    expect(store.darkness()).toBe(10);
  });

  // @trace FR-2
  it.each([0, 11, 5.5, NaN])('leaves the state unchanged for intensity %s', (bad) => {
    const before = snapshot(store);
    store.setRealism(bad);
    store.setDarkness(bad);
    store.setOptimism(bad);
    expect(snapshot(store)).toEqual(before);
  });

  // @trace FR-3
  it('setHorizon changes only the horizon', () => {
    store.setHorizon('5y');
    expect(snapshot(store)).toEqual({ realism: 8, darkness: 5, optimism: 5, horizon: '5y' });
  });

  // @trace FR-3
  it('ignores an unknown horizon code', () => {
    store.setHorizon('3y' as HorizonCode);
    expect(store.horizon()).toBe('1y');
  });
});
