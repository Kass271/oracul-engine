import type { HttpTestingController } from '@angular/common/http/testing';

import type { OutputSettings } from '../../api/models/output-settings';
import type { StoryLanguage } from '../../api/models/story-language';
import type { WritingStyleCatalogue } from '../../api/models/writing-style-catalogue';
import type { WritingStyleProfile } from '../../api/models/writing-style-profile';

/**
 * Shared fixtures of the panel specs (slice 02_style-selection): the shipped writing style catalogue as
 * GET /api/writing-styles answers it, and the panel's default output. Specs of other slices import these so that the
 * third load request and the two new output fields are one line in each of them.
 */

type Mode = 'OFFICIAL' | 'NARRATIVE';

const profile = (
  id: string,
  mode: Mode,
  label: string,
  characteristics: string,
): WritingStyleProfile => ({
  id,
  mode,
  label,
  characteristics,
  isDefault: id === 'official-briefing',
  isModeDefault: id === 'official-briefing' || id === 'neutral-narrative',
});

export const SHIPPED_PROFILES: WritingStyleProfile[] = [
  profile(
    'official-briefing',
    'OFFICIAL',
    'Official / Briefing',
    'factual, concise, structured, neutral',
  ),
  profile('neutral-narrative', 'NARRATIVE', 'Neutral Narrative', 'literary and immersive prose'),
  profile(
    'poe',
    'NARRATIVE',
    'Narrative — inspired by Edgar Allan Poe',
    'dark, gothic, psychologically intense',
  ),
  profile(
    'kafka',
    'NARRATIVE',
    'Narrative — inspired by Franz Kafka',
    'bureaucratic absurdity, alienation',
  ),
  profile(
    'wells',
    'NARRATIVE',
    'Narrative — inspired by H. G. Wells',
    'speculative and observational',
  ),
  profile(
    'verne',
    'NARRATIVE',
    'Narrative — inspired by Jules Verne',
    'exploratory, technological, adventurous',
  ),
  profile(
    'orwell',
    'NARRATIVE',
    'Narrative — inspired by George Orwell',
    'clear, restrained prose',
  ),
  profile(
    'clarke',
    'NARRATIVE',
    'Narrative — inspired by Arthur C. Clarke',
    'large-scale scientific perspective',
  ),
  profile(
    'harrison',
    'NARRATIVE',
    'Narrative — inspired by Harry Harrison',
    'fast-moving science fiction with irony',
  ),
];

export const NARRATIVE_IDS: string[] = SHIPPED_PROFILES.filter((p) => p.mode === 'NARRATIVE').map(
  (p) => p.id,
);

export const WRITING_STYLES: WritingStyleCatalogue = {
  modes: [
    {
      id: 'OFFICIAL',
      label: 'Official / Briefing',
      isDefault: true,
      defaultProfileId: 'official-briefing',
    },
    {
      id: 'NARRATIVE',
      label: 'Narrative',
      isDefault: false,
      defaultProfileId: 'neutral-narrative',
    },
  ],
  profiles: SHIPPED_PROFILES,
  languages: [
    { code: 'en', label: 'English', isDefault: true },
    { code: 'uk', label: 'Ukrainian', isDefault: false },
  ],
};

/** The output a panel in its default state sends (and the store holds after loading a configuration without style). */
export const PANEL_OUTPUT: OutputSettings = {
  story: true,
  illustration: false,
  writingStyle: { mode: 'OFFICIAL', profileId: 'official-briefing' },
  language: 'en',
};

export const panelOutput = (
  mode: Mode = 'OFFICIAL',
  profileId = 'official-briefing',
  language: StoryLanguage = 'en',
): OutputSettings => ({
  story: true,
  illustration: false,
  writingStyle: { mode, profileId },
  language,
});

/** Answers every open GET /api/writing-styles request with the shipped catalogue. */
export function flushWritingStyles(
  http: HttpTestingController,
  catalogue: WritingStyleCatalogue = WRITING_STYLES,
): void {
  http.match((r) => r.url.endsWith('/api/writing-styles')).forEach((r) => r.flush(catalogue));
}
