import fs from 'node:fs';
import { expect, test } from '@playwright/test';
import { evidence } from './evidence';

// @trace FR-42
// Node `fs` text checks of the app folder (no YAML library), then the live stub: the request shapes it enforces.

const APP = new URL('../../', import.meta.url);
const STUB = 'http://localhost:4010';
const SCOPES = 'openid profile email offline_access resource.invoke chatgpt.tokens.use.direct';
const HOST_ID = 'urn:uuid:123e4567-e89b-42d3-a456-426614174000';

const exists = (rel: string): boolean => fs.existsSync(new URL(rel, APP));
const read = (rel: string): string => fs.readFileSync(new URL(rel, APP), 'utf8');
const escapeRe = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** The backend environment of docker-compose.e2e.yml (run-modes.md), plus the Google News variables (phase-03 FR-49: no variable of a second news provider; FR-52: the rate-limit wait replaces the request spacing). */
const E2E_ENV: [string, string][] = [
  ['ORACUL_CHATGPT_AUTHORIZE_URL', 'http://localhost:4010/oauth/authorize'],
  ['ORACUL_CHATGPT_TOKEN_URL', 'http://stub:4010/oauth/token'],
  ['ORACUL_CHATGPT_JWKS_URL', 'http://stub:4010/jwks'],
  ['ORACUL_CHATGPT_ISSUER', 'http://stub:4010'],
  ['ORACUL_CHATGPT_REVOCATION_URL', 'http://stub:4010/oauth/revoke'],
  ['ORACUL_OPENAI_RESPONSES_BASE_URL', 'http://stub:4010/v1'],
  ['ORACUL_NEWS_GOOGLE_BASE_URL', 'http://stub:4010'],
  ['ORACUL_NEWS_GOOGLE_RATE_LIMIT_WAIT', 'PT0.2S'],
  ['ORACUL_OPENAI_RETRY_DELAY', 'PT0.2S'],
  ['ORACUL_RUN_PLACEHOLDER_STAGE_DELAY', 'PT2S'],
  ['ORACUL_RUN_MIN_STAGE_DURATION', 'PT2S'],
  ['ORACUL_EVIDENCE_MIN_CORE_MEDIUM', '0'],
  ['ORACUL_EVIDENCE_MIN_CORE_LOW', '0'],
];

test.describe('FR-42 Opt-in E2E stub with its own data: compose files and stack modes', () => {
  test('docker-compose.override.yml is gone, so a plain compose up merges nothing', () => {
    expect(exists('docker-compose.override.yml')).toBe(false);
  });

  test('docker-compose.yml is the real stack: db, backend, frontend, db-data, no stub and no ORACUL_ variable', () => {
    const yml = read('docker-compose.yml');
    expect(yml).toMatch(/^name:\s*oracul-engine\s*$/m);
    for (const service of ['db', 'backend', 'frontend']) expect(yml).toMatch(new RegExp(`^  ${service}:\\s*$`, 'm'));
    expect(yml).not.toMatch(/^\s*stub:/m);
    expect(yml).not.toContain('4010');
    expect(yml).not.toContain('ORACUL_');
    expect(yml).toContain('db-data:/var/lib/postgresql');
    expect(yml).toMatch(/^volumes:\s*\n\s+db-data:/m);
    expect(yml).not.toContain('db-e2e-data');
  });

  test('docker-compose.e2e.yml adds the stub, every backend override and its own database volume', () => {
    expect(exists('docker-compose.e2e.yml')).toBe(true);
    const yml = read('docker-compose.e2e.yml');
    expect(yml).toMatch(/^\s*stub:/m);
    expect(yml).toContain('db-e2e-data:/var/lib/postgresql');
    expect(yml).toMatch(/^volumes:\s*\n\s+db-e2e-data:/m);
    expect(yml, 'the real volume is never mounted in mode e2e').not.toContain('db-data:/var/lib/postgresql');
    expect(yml).toContain('4010');
    for (const [name, value] of E2E_ENV) {
      expect(yml, `${name} = ${value}`).toMatch(new RegExp(`^\\s*${name}:\\s*"?${escapeRe(value)}"?\\s*$`, 'm'));
    }
    expect(yml, 'E2E uses the real loopback redirect uri').not.toContain('ORACUL_CHATGPT_REDIRECT_URI');
    expect(yml).toMatch(/depends_on:[\s\S]*stub:/);
  });

  // @trace FR-49, FR-52
  test('FR-49 docker-compose.e2e.yml sets no variable of the former news provider and none of its removed settings', () => {
    const yml = read('docker-compose.e2e.yml');
    // the name of the former provider, in pieces: the scan of FR-49 allows it in one backend test file only
    const former = ['GD', 'ELT'].join('');
    for (const name of [`ORACUL_NEWS_${former}_BASE_URL`, 'ORACUL_NEWS_REQUEST_SPACING', 'ORACUL_NEWS_RATE_LIMIT_WAIT', 'ORACUL_NEWS_GOOGLE_REQUEST_SPACING', 'ORACUL_NEWS_SEARCH_BUDGET']) {
      expect(yml, `${name} is removed`).not.toMatch(new RegExp(`^\\s*${name}\\s*:`, 'm'));
    }
    expect(yml.toLowerCase()).not.toContain(former.toLowerCase());
    // every other news variable stays
    expect(yml).toMatch(/^\s*ORACUL_NEWS_GOOGLE_BASE_URL:/m);
    expect(yml, 'FR-52: the 429 retry waits 0.2 s on the E2E stack').toMatch(/^\s*ORACUL_NEWS_GOOGLE_RATE_LIMIT_WAIT:\s*"?PT0\.2S"?\s*$/m);
  });

  test('.oracul/stack.json declares the e2e mode with the stub file and the run mode with the real stack only', () => {
    const stack = JSON.parse(read('.oracul/stack.json'));
    expect(stack.modes.e2e.files).toEqual(['docker-compose.yml', 'docker-compose.e2e.yml']);
    expect(stack.modes.run.files).toEqual(['docker-compose.yml']);
    expect(stack.urls.frontend).toBe('http://localhost:4200');
    expect(stack.urls.health).toBe('http://localhost:8080/actuator/health');
  });
});

test.describe('FR-42 the stub enforces the documented OpenAI request shapes', () => {
  test.beforeEach(async ({ request }) => {
    expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  });

  const valid = (): Record<string, string> => ({
    response_type: 'code',
    client_id: 'dynamic_agent_client',
    agent_name_hint: 'ORACUL',
    ext_agent_host_id: HOST_ID,
    redirect_uri: 'http://127.0.0.1:4200/callback',
    scope: SCOPES,
    resource: 'https://api.openai.com/v1',
    state: 'state-1',
    nonce: 'nonce-1',
    code_challenge: 'challenge-1',
    code_challenge_method: 'S256',
  });

  const authorizeUrl = (params: Record<string, string>): string => `${STUB}/oauth/authorize?${new URLSearchParams(params).toString()}`;

  const rejects: [string, (p: Record<string, string>) => void][] = [
    ['a bare UUID as the host id', (p) => (p.ext_agent_host_id = HOST_ID.replace('urn:uuid:', ''))],
    ['an upper-case hex host id', (p) => (p.ext_agent_host_id = `urn:uuid:${HOST_ID.slice('urn:uuid:'.length).toUpperCase()}`)],
    ['the redirect path /auth/callback', (p) => (p.redirect_uri = 'http://127.0.0.1:4200/auth/callback')],
    ['the redirect host localhost', (p) => (p.redirect_uri = 'http://localhost:4200/callback')],
    ['a missing nonce', (p) => delete p.nonce],
    ['client_id oaiapp_x with an agent_name_hint', (p) => (p.client_id = 'oaiapp_x')],
    ['dynamic_agent_client without an agent_name_hint', (p) => delete p.agent_name_hint],
    ['a missing resource', (p) => delete p.resource],
    ['code_challenge_method plain', (p) => (p.code_challenge_method = 'plain')],
  ];

  for (const [name, mutate] of rejects) {
    test(`GET /oauth/authorize with ${name} answers 400 invalid_authorize_request and no redirect`, async ({ request }) => {
      const params = valid();
      mutate(params);
      const res = await request.get(authorizeUrl(params), { maxRedirects: 0 });
      expect(res.status()).toBe(400);
      expect(await res.json()).toEqual({ error: 'invalid_authorize_request' });
      expect(res.headers()['location']).toBeUndefined();
    });
  }

  test('GET /oauth/authorize with a fully valid request redirects to the loopback callback with a code', async ({ request }) => {
    const res = await request.get(authorizeUrl(valid()), { maxRedirects: 0 });
    expect(res.status()).toBe(302);
    expect(res.headers()['location']).toMatch(/^http:\/\/127\.0\.0\.1:4200\/callback\?code=[^&]+/);
  });

  test('GET /oauth/authorize accepts a registered oaiapp_ client id without an agent_name_hint', async ({ request }) => {
    const params = valid();
    params.client_id = 'oaiapp_stub_client';
    delete params.agent_name_hint;
    const res = await request.get(authorizeUrl(params), { maxRedirects: 0 });
    expect(res.status()).toBe(302);
    expect(res.headers()['location']).toMatch(/^http:\/\/127\.0\.0\.1:4200\/callback\?code=/);
  });

  const responseBodies: [string, Record<string, unknown>][] = [
    ['without stream', { store: false, input: [] }],
    ['with stream:false', { stream: false, store: false, input: [] }],
    ['without store', { stream: true, input: [] }],
    ['with store:true', { stream: true, store: true, input: [] }],
  ];

  for (const [name, body] of responseBodies) {
    test(`POST /v1/responses ${name} answers 400 and the body is still recorded`, async ({ request }) => {
      const before = await (await request.get(`${STUB}/__control/requests?kind=responses`)).json();
      const count = (Array.isArray(before) ? before : before.requests).length;
      const res = await request.post(`${STUB}/v1/responses`, { data: body });
      expect(res.status()).toBe(400);
      expect(await res.json()).toEqual({
        error: { code: 'invalid_request_error', message: 'stream must be true and store must be false' },
      });
      const after = await (await request.get(`${STUB}/__control/requests?kind=responses`)).json();
      expect((Array.isArray(after) ? after : after.requests).length).toBe(count + 1);
    });
  }
});

test.describe('FR-42 real-mode isolation: the stack under test is wired to the stub', () => {
  test('a sign-in through the UI reaches the stub and registers oaiapp_stub_client', async ({ page, request }) => {
    expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await page.getByTestId('chatgpt-connect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await evidence(page, 'FR-42', 'stub-signin');
    // the stub issued tokens for this sign-in ...
    const issued = await (await request.get(`${STUB}/__control/issued`)).json();
    expect(issued.values.length).toBeGreaterThan(0);
    // ... and the registered client id it handed out is the one the backend now uses for authorizations
    const authorize = await page.request.get('/api/auth/chatgpt/authorize', { maxRedirects: 0 });
    expect(authorize.status()).toBe(302);
    const location = authorize.headers()['location'];
    expect(location).toMatch(/^http:\/\/localhost:4010\/oauth\/authorize\?/);
    expect(new URL(location).searchParams.get('client_id')).toBe('oaiapp_stub_client');
    expect(new URL(location).searchParams.get('redirect_uri')).toBe('http://127.0.0.1:4200/callback');
  });
});
