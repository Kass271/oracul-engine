// E2E stub server (port 4010). Dependency-free. NEVER forwards to real OpenAI (NFR-7): every handler is local.
// Structured as a small route table so later slices can register more fixtures (GDELT, Responses API).
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';

const PORT = Number(process.env.PORT ?? 4010);
const MODES = ['ok', 'not_eligible', 'deny', 'token_error', 'refresh_error'];
const SCOPES = 'openid profile email offline_access resource.invoke chatgpt.tokens.use.direct';

export const state = { mode: 'ok', counter: 0, codes: new Map(), issued: [] };

function reset() {
  state.mode = 'ok';
  state.counter = 0;
  state.codes.clear();
  state.issued.length = 0;
}

const json = (res, status, body) => {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
};
const empty = (res, status) => {
  res.writeHead(status);
  res.end();
};
const readBody = (req) =>
  new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
  });
const s256 = (v) => createHash('sha256').update(v).digest('base64url');

function tokens() {
  const n = ++state.counter;
  const scope = state.mode === 'not_eligible' ? SCOPES.replace('chatgpt.tokens.use.direct', '').replace(/\s+/g, ' ').trim() : SCOPES;
  const body = {
    access_token: `at-STUBSECRET-${n}`,
    refresh_token: `rt-STUBSECRET-${n}`,
    id_token: `id-STUBSECRET-${n}`,
    token_type: 'Bearer',
    expires_in: 3600,
    scope,
  };
  state.issued.push(body.access_token, body.refresh_token, body.id_token);
  return body;
}

// ---- route table: "METHOD /path" -> async (req, res, url, body) ----
export const routes = {
  'POST /__control/mode': async (req, res, url, body) => {
    let mode;
    try {
      mode = JSON.parse(body || '{}').mode;
    } catch {
      return json(res, 400, { error: 'invalid_json' });
    }
    if (!MODES.includes(mode)) return json(res, 400, { error: 'unknown_mode' });
    state.mode = mode;
    empty(res, 204);
  },
  'POST /__control/reset': async (req, res) => {
    reset();
    empty(res, 204);
  },
  'GET /__control/issued': async (req, res) => json(res, 200, { values: [...state.issued] }),

  'GET /oauth/authorize': async (req, res, url) => {
    const q = url.searchParams;
    const redirect = q.get('redirect_uri');
    if (!redirect) return json(res, 400, { error: 'invalid_request' });
    const target = new URL(redirect);
    const st = q.get('state');
    if (state.mode === 'deny') {
      target.searchParams.set('error', 'access_denied');
      if (st) target.searchParams.set('state', st);
    } else {
      const code = `stub-code-${++state.counter}`;
      state.codes.set(code, q.get('code_challenge'));
      state.issued.push(code);
      target.searchParams.set('code', code);
      if (st) target.searchParams.set('state', st);
      if (q.get('client_id') === 'dynamic_agent_client') target.searchParams.set('client_id', 'oaiapp_stub_client');
    }
    res.writeHead(302, { location: target.toString() });
    res.end();
  },
  'POST /oauth/token': async (req, res, url, body) => {
    const form = new URLSearchParams(body);
    const grant = form.get('grant_type');
    if (grant === 'authorization_code') {
      const code = form.get('code');
      const challenge = state.codes.get(code);
      const verifier = form.get('code_verifier');
      if (challenge === undefined || !verifier || s256(verifier) !== challenge) return json(res, 400, { error: 'invalid_grant' });
      if (state.mode === 'token_error') return json(res, 500, { error: 'server_error' });
      return json(res, 200, tokens());
    }
    if (grant === 'refresh_token') {
      if (state.mode === 'refresh_error') return json(res, 400, { error: 'invalid_grant' });
      if (state.mode === 'token_error') return json(res, 500, { error: 'server_error' });
      return json(res, 200, tokens());
    }
    return json(res, 400, { error: 'unsupported_grant_type' });
  },
};

export const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  const handler = routes[`${req.method} ${url.pathname}`];
  if (!handler) return json(res, 404, { error: 'not_found' });
  try {
    await handler(req, res, url, await readBody(req));
  } catch (e) {
    json(res, 500, { error: 'stub_failure', message: String(e) });
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`oracul e2e stub listening on ${PORT}`));
