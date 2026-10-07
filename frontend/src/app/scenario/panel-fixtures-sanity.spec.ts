import { NARRATIVE_IDS, SHIPPED_PROFILES, WRITING_STYLES } from './testing/panel-fixtures';

// @trace FR-63
describe('panel fixtures (slice 02_style-selection)', () => {
  it('the shipped catalogue has 9 profiles, 8 narrative ones, and the two default flags per spec', () => {
    expect(SHIPPED_PROFILES.length).toBe(9);
    expect(NARRATIVE_IDS.length).toBe(8);
    expect(SHIPPED_PROFILES.filter((p) => p.isDefault).map((p) => p.id)).toEqual([
      'official-briefing',
    ]);
    expect(WRITING_STYLES.languages.map((l) => l.code)).toEqual(['en', 'uk']);
  });
});
