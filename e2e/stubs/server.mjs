// E2E stub server (port 4010). Dependency-free. NEVER forwards to real OpenAI (NFR-7): every handler is local.
// Structured as a small route table so later slices can register more fixtures (GDELT, Responses API).
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';

const PORT = Number(process.env.PORT ?? 4010);
const MODES = ['ok', 'not_eligible', 'deny', 'token_error', 'refresh_error'];
const SCOPES = 'openid profile email offline_access resource.invoke chatgpt.tokens.use.direct';

export const state = { mode: 'ok', counter: 0, codes: new Map(), issued: [], news: 'ok', events: 'ok', requests: { responses: [], gdelt: [] } };

function reset() {
  state.mode = 'ok';
  state.counter = 0;
  state.codes.clear();
  state.issued.length = 0;
  state.news = 'ok';
  state.events = 'ok';
  state.requests.responses.length = 0;
  state.requests.gdelt.length = 0;
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
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const sha1 = (v) => createHash('sha1').update(v).digest('hex');
const seendate = () => new Date(Date.now() - 86_400_000).toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');
const dataBlock = (text, name) => {
  const open = `<<<ORACUL_UNTRUSTED_DATA name="${name}">>>`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const end = text.indexOf('<<<END_ORACUL_UNTRUSTED_DATA>>>', start);
  return end < 0 ? '' : text.slice(start + open.length, end);
};
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

  'POST /__control/news': async (req, res, url, body) => {
    let mode;
    try {
      mode = JSON.parse(body || '{}').mode;
    } catch {
      return json(res, 400, { error: 'invalid_json' });
    }
    if (!['ok', 'down'].includes(mode)) return json(res, 400, { error: 'unknown_mode' });
    state.news = mode;
    empty(res, 204);
  },
  'POST /__control/events': async (req, res, url, body) => {
    let mode;
    try {
      mode = JSON.parse(body || '{}').mode;
    } catch {
      return json(res, 400, { error: 'invalid_json' });
    }
    if (!['ok', 'malformed-classification', 'rate-limited'].includes(mode)) return json(res, 400, { error: 'unknown_mode' });
    state.events = mode;
    empty(res, 204);
  },
  'GET /__control/requests': async (req, res, url) => {
    const kind = url.searchParams.get('kind');
    if (!Object.hasOwn(state.requests, kind)) return json(res, 400, { error: 'unknown_kind' });
    json(res, 200, { requests: [...state.requests[kind]] });
  },

  // OpenAI Responses API stub: routed by the "ORACUL REQUEST <PURPOSE>" marker in the prompt.
  'POST /v1/responses': async (req, res, url, body) => {
    let parsed;
    try {
      parsed = JSON.parse(body || '{}');
    } catch {
      return json(res, 400, { error: 'invalid_json' });
    }
    state.requests.responses.push(parsed);
    const responseId = `resp_${state.requests.responses.length}`; // taken synchronously: stays unique under concurrent requests
    const text = (parsed.input ?? [])
      .flatMap((m) => (Array.isArray(m.content) ? m.content : []))
      .map((c) => c.text ?? '')
      .join('\n');
    const purpose = /ORACUL REQUEST ([A-Z_]+)/.exec(text)?.[1];
    // The backend sends up to 4 EVENT_* batches in parallel (research-pipeline.md "Parallel batches"): answer with a small
    // asynchronous delay so concurrent requests really overlap. Every answer depends on its own request text only.
    if (purpose === 'EVENT_NORMALIZATION' || purpose === 'EVENT_CLASSIFICATION') await sleep(40);
    let output;
    if (purpose === 'QUERY_EXPANSION') {
      const queries = [];
      for (const m of text.matchAll(/^- (\S+) \| \S+ \| (\d+) \|/gm)) {
        for (let i = 1; i <= Number(m[2]); i++) queries.push({ intentId: m[1], text: `${m[1]} stub query ${i}` });
      }
      output = JSON.stringify({ queries });
    } else if (purpose === 'EVENT_NORMALIZATION') {
      if (state.events === 'rate-limited') return json(res, 429, { error: 'rate_limited' });
      const ids = [...dataBlock(text, 'sources').matchAll(/^(S\d+) \| /gm)].map((m) => m[1]);
      const events = [];
      for (let i = 0; i < ids.length; i += 2) {
        const group = ids.slice(i, i + 2);
        events.push({ sourceIds: group, date: null, category: 'general', entities: [`Entity ${group[0]}`], summary: `Stub event ${group[0]}`, disagreement: null, confidence: 0.8 });
      }
      output = JSON.stringify({ events });
    } else if (purpose === 'EVENT_CLASSIFICATION') {
      if (state.events === 'malformed-classification') {
        output = 'not json';
      } else {
        // exactly the backend StubResponses default: wildcardMatches is empty, so the backend's 0.0 fill per profile topic
        // is exercised end to end
        const ids = [...dataBlock(text, 'events').matchAll(/^(EV\d+) \| /gm)].map((m) => m[1]);
        output = JSON.stringify({
          classifications: ids.map((eventId) => ({
            eventId, topic: 'general', subtopics: [], sentiment: 0.1, risk: 0.4, opportunity: 0.6, impact: 0.5, novelty: 0.5,
            trend: 'ESTABLISHED', geography: 'global', wildcardMatches: [],
          })),
        });
      }
    } else {
      return json(res, 400, { error: 'unsupported_purpose', purpose: purpose ?? null });
    }
    json(res, 200, {
      id: responseId,
      status: 'completed',
      output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: output }] }],
    });
  },

  // GDELT DOC 2.0 artlist stub: 5 articles per query, the first is shared by all queries (dedup).
  'GET /api/v2/doc/doc': async (req, res, url) => {
    const query = url.searchParams.get('query') ?? '';
    state.requests.gdelt.push({ query, params: Object.fromEntries(url.searchParams) });
    if (state.news === 'down') return json(res, 503, { error: 'unavailable' });
    const n = state.requests.gdelt.length;
    const key = sha1(query).slice(0, 8);
    const base = 'http://stub:4010/articles';
    const articles = [`${base}/shared?utm_source=${n}`, ...[2, 3, 4, 5].map((a) => `${base}/${key}-${a}`)].map((u, i) => ({
      url: u,
      url_mobile: '',
      title: i === 0 ? 'Shared stub article' : `Stub article ${key}-${i + 1}`,
      seendate: seendate(),
      socialimage: '',
      domain: 'reuters.com',
      language: 'English',
      sourcecountry: 'United States',
    }));
    json(res, 200, { articles });
  },
  'GET /articles/*': async (req, res, url) => {
    const name = url.pathname.slice('/articles/'.length);
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(`<html><head><meta property="og:site_name" content="Stub Site"><meta property="og:description" content="Summary of ${name.replace(/[^\w-]/g, '')}"></head><body>x</body></html>`);
  },

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
  const handler = routes[`${req.method} ${url.pathname}`] ?? (url.pathname.startsWith('/articles/') ? routes[`${req.method} /articles/*`] : undefined);
  if (!handler) return json(res, 404, { error: 'not_found' });
  try {
    await handler(req, res, url, await readBody(req));
  } catch (e) {
    json(res, 500, { error: 'stub_failure', message: String(e) });
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`oracul e2e stub listening on ${PORT}`));
