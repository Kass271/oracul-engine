# Spec — Article retrieval: selection per wildcard, publisher article text, relevant fragments, safe fetching

Covers: FR-53, FR-54, FR-55, FR-56

Delta against phase-01 `research-pipeline.md` FR-13 (filtering, metadata fetch, source fields), FR-17 (event selection
with counter-signal quota), phase-02 `news-search.md` FR-46 (30-source cap with topic round robin) and FR-48 step 8
(Google link "resolved" by following HTTP redirects — release finding R2), and the current code
`SourceRetrieval.readSources`, `SourceCap`, `ArticleMetadataFetcher`, `UrlNormalizer`, `SourceQualityTable`,
`SourceRepository`. Runs in stage READING_SOURCES (index 4), after the FR-52 join (`wildcard-search.md`), inside the
NFR-10 budget.

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-17: up to 25 events in CORE / SUPPORTING / COUNTER_SIGNAL with diversity caps and ≥ 1 counter-signal, Evidence IDs on events | **Superseded** by FR-53 (selection of up to 4 sources per wildcard, before article retrieval) and FR-57 (Evidence IDs on sources, `wildcard-evidence.md`). `EvidenceSelector` is no longer called for new runs; events get no `selection`; `counts.counterSignals` = 0. The `oracul.evidence.core / supporting / counter-signals / max-*` properties are no longer read. |
| FR-46: cap 30 by topic round robin ranked by source quality, then provider order | **Superseded** by the FR-53 cap: round robin over wildcard pipelines in pipeline order, each pipeline's own relevance order; "at most 30" stays. |
| FR-13 / FR-48 metadata fetch: GET the link with ≤ 3 redirects, 3 s, 512 KB; a redirect ending at another URL counted as "resolved" (R2: real Google ends on a Google page) | **Superseded** by FR-54 (decode through Google's batchexecute call, publisher fetch) and FR-56 (≤ 5 redirects, 2 MB, private-address refusal). A link that ends on a Google host is never the article. |
| FR-13: `summary` = page description else title; `metadataFetched` | kept with the FR-54 field rules below |

## Purpose
From each wildcard's search results ORACUL keeps the few most relevant articles, reaches the real publisher page behind
every Google News link, and extracts the paragraphs that matter for that wildcard — safely, without letting a feed
link reach internal addresses or flood memory.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| `source` (Flyway `V11`) | `content_status` | VARCHAR(32) NULL | `ArticleContentStatus` (below); NULL for stored older runs |
| `source` | `excerpts` | JSONB NULL | `[{"pipelineId":"W01","fragments":["…"]}]` — per pipeline that lists the source and got ≥ 1 fragment, in pipeline order |
| `source` | `publisher_host` | TEXT NULL | host of `url` when it is the publisher URL (lower-case, leading `www.` removed); NULL when `url` is still the Google link |
| `source` | `pipeline_ids` | JSONB NULL | pipelines that found the source (FR-50 attribution), ascending |
| `source` | `url` | TEXT | publisher URL (normalised) after a successful decode, else the Google link (normalised); unique per run (existing constraint) |
| `source` | `id` | `S001`… | assigned in **Evidence order** (FR-57): pipelines in order, inside a pipeline its group order, first appearance wins — so `S00k` ↔ `E00k` |
| API `Source` | `contentStatus`, `excerpts`, `publisherHost`, `pipelineIds` | optional | always sent for new runs (`excerpts` may be `[]`) |
| API `WildcardPipeline` | `candidatesConsidered`, `sourceIds` | optional | set by the READING_SOURCES commit |
| `ResearchCounts` | `articlesConsidered` | int | distinct usable candidates over all pipelines (distinct normalised links after filtering) — the meaning of phase-01 FR-13 again |
| `ResearchCounts` | `sourcesKept` (new, optional, always sent for new runs) | int 0–30 | stored sources |
| `ResearchCounts` | `sourcesWithContent` (new, optional, always sent for new runs) | int ≥ 0 | stored sources with `contentStatus` RETRIEVED |

`ArticleContentStatus`: `RETRIEVED` (publisher page read and ≥ 1 fragment for ≥ 1 pipeline) · `DECODE_FAILED` (Google
page, attributes or batchexecute failed, timed out, or returned a Google host) · `PAGE_FAILED` (publisher page non-2xx,
timeout, connection error, not HTML/text, more than 5 redirects) · `NO_TEXT` (page read but no fragment: no matching
paragraph and no paragraph of ≥ 80 characters) · `REFUSED` (FR-56: blocked address, non-http(s) URL) ·
`NOT_ATTEMPTED` (budget ended or the run stopped before the retrieval finished). Everything but RETRIEVED is shown as
"content not retrieved".

### Configuration (new / changed)
| Property | Env var | Default | Rule |
|---|---|---|---|
| `oracul.news.google.decode-url` | `ORACUL_NEWS_GOOGLE_DECODE_URL` | `<google.base-url>/_/DotsSplashUi/data/batchexecute` | undocumented Google endpoint, behind `ArticleUrlDecoder`; tests point it to a stub |
| `oracul.news.article-fetch-timeout` | `ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT` | `PT8S` (was `PT3S`) | applies separately to (a) the decode step (Google page + batchexecute together) and (b) the publisher fetch incl. redirects; each also cut to the NFR-10 budget |
| `oracul.news.article-fetch-concurrency` | — | `8` | max retrieval HTTP requests open at once (Google page, decode POST and publisher GET share one semaphore); 1…8 |
| `oracul.news.article-max-bytes` | — | `2097152` (2 MB, was 524288) | bytes read per response body; reading stops there |
| `oracul.news.article-max-redirects` | — | `5` | per fetch (Google-page fetch, publisher fetch) |
| `oracul.news.fetch.allowed-private-hosts` | `ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS` | empty | comma-separated host names (case-insensitive, exact) exempt from the FR-56 address check; real mode sets none; E2E `stub`, backend tests `127.0.0.1` |
Constants (not configurable): 4 selected sources per wildcard, 30 sources per run (FR-46 limit), 3 fragments and 1,200
characters per source and pipeline.

## Behaviour

### FR-53 — Source selection per wildcard
- Happy path (READING_SOURCES, pure `WildcardSelector`, after the FR-52 join):
  1. **Candidates per pipeline**: the items of all its OK queries, in query order then feed order. Basic filter
     (phase-01 / FR-48 rules): `link` absolute `http`/`https`; cleaned `title` non-blank; `pubDate` parsed and older
     than the horizon's days (7 / 14 / 90) → dropped (unparsable → kept, `publishedAt` absent). Duplicates inside the
     pipeline (same normalised link, `UrlNormalizer` rules) are merged: the candidate remembers every query that
     returned it (H = number of those queries) and its best feed position (lowest 1-based index over those queries).
     `candidatesConsidered` = number of the pipeline's merged candidates.
  2. **Relevance score** of a candidate for its pipeline (deterministic, no ChatGPT):
     - tokens(text) = lower-case words of `[a-z0-9]+`, length ≥ 3, without the stop words {the, and, for, with, from,
       that, this, are, was, were, has, have, had, its, into, over, about, after, than, then, will, what, when, which,
       who, why, how, new, latest, news, today, says, said}.
     - label terms = tokens(label) (GENERAL: tokens of `major current world events`); query terms = tokens of the
       pipeline's query texts minus the label terms.
     - text = cleaned title + " " + snippet (RSS `description` with HTML tags removed, entities decoded, whitespace
       collapsed).
     - score = 3 × |label terms ∩ tokens(text)| + |query terms ∩ tokens(text)| + 2 × (H − 1).
     - Order: score desc, then best feed position asc, then the id of the query with that position asc, then the
       normalised link asc.
  3. **Selection**: the pipeline's first 4 candidates in that order (fewer when it has fewer).
  4. **Same article in several pipelines**: candidates of different pipelines with the same normalised link are the
     same article (one future source). Its `pipelineIds` = every pipeline that has it as a candidate; its `queryIds`
     = every query (any pipeline) that returned it, ascending.
  5. **Cap 30** (round robin): rounds r = 1…4; in each round visit the pipelines in pipeline order and take the
     pipeline's r-th selected candidate: already kept → nothing (it counts once); else kept if fewer than 30 are kept;
     stop at 30. Hence every pipeline with ≥ 1 candidate keeps ≥ 1 source whenever at most 30 pipelines have
     candidates (round 1 adds at most one new source per pipeline).
  6. **Groups**: the group of pipeline P = every kept source among P's candidates, in P's relevance order (P's own
     selections and kept sources P found that another pipeline selected). A pipeline without kept sources has an empty
     group ("no current sources found", FR-57 / FR-59 / FR-60).
  7. Retrieval (FR-54) runs for the kept sources only; then sources are numbered in Evidence order (Data) and stored
     in one guarded commit together with `counts.articlesConsidered`, `sourcesKept`, `sourcesWithContent` and every
     pipeline's `candidatesConsidered` / `sourceIds` in `search_plan`.
- Rules:
  - There is no counter-signal quota and no diversity cap (FR-53 acceptance 4); `topic` of a source = `topicKey` of
    its first pipeline (GENERAL → `major`), so phase-01 event ranking (FR-16) keeps working on the stored sources.
  - Two kept sources whose publisher URLs turn out equal after decoding (two Google links of one article) are merged
    after retrieval into the one earlier in Evidence order (union of `pipelineIds` / `queryIds`; the later one leaves
    no row). Numbering happens after this merge.
  - `articlesConsidered` = distinct normalised links over all pipelines' candidates; `sourcesKept` ≤ 30; the article
    fetchers are called for kept sources only.
- Errors: none user-facing; a pipeline with 0 candidates simply has no sources (FR-59 note).
- Ranges & invariants (unit, parameterized, `WildcardSelector`): candidates per pipeline 0, 1, 3, 4, 5, 40 → selected
  0, 1, 3, 4, 4, 4; pipelines × candidates: 9 pipelines × 10 → 30 kept (rounds 1–3 give 27, round 4 the first 3
  pipelines' 4th), every pipeline ≥ 3; 30 pipelines with candidates → 30 kept, each pipeline ≥ 1; 31 pipelines → 30
  kept, the 31st has none (named in the note); one shared candidate selected first by 3 pipelines → kept once, listed
  in 3 groups, uses one slot. Score classes: label term in title beats query term only (3 vs 1); found by 2 queries
  (+2); equal score → lower feed position first; equal position → lower query id; equal → link order; same input
  twice / shuffled pipeline input order of items → identical result. Invariants for every input: kept ⊆ ∪ selected;
  |kept| ≤ 30; |selected(P)| ≤ 4; no normalised link twice; every kept source's `pipelineIds` = exactly the pipelines
  having it as candidate; `articlesConsidered` = |∪ candidates|; `sourcesKept` = stored rows = `listRunSources`
  length; groups list only kept sources.

### FR-54 — Publisher article retrieval
- Happy path (per kept source, `ArticleRetriever` on virtual threads, at most `article-fetch-concurrency` (8) HTTP
  requests open at once; sources started in Evidence order):
  1. **Google link?** A link whose host equals the host of `google.base-url` or is `news.google.com` is a Google link;
     any other link is already a publisher URL (go to step 4).
  2. **Google page**: `GET <link>` through the safe fetcher (FR-56: redirects followed only while they stay on the same
     host, ≤ 5, 2 MB) — real Google answers 302 to the same path with `&hl=en-US&gl=US&ceid=US:en`, then 200 HTML. Parse
     (jsoup, never executed) the first element that has all three attributes `data-n-a-id` (non-blank), `data-n-a-ts`
     (digits) and `data-n-a-sg` (non-blank).
  3. **Decode** (`ArticleUrlDecoder`, the only place that knows the undocumented format): `POST <decode-url>`,
     `Content-Type: application/x-www-form-urlencoded;charset=UTF-8`, same `User-Agent`, body `f.req=<URL-encoded
     value>` with value exactly
     `[[["Fbv4je","[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"<id>\",<ts>,\"<sg>\"]",null,"generic"]]]`
     (`<ts>` unquoted digits). Answer 200 → the publisher URL = the first match of `https?://[^"\\\s<>]+` in the body.
     Decode failure = steps 2–3 fail in any way: non-2xx, timeout (8 s for steps 2+3 together), connection error, no
     element with the three attributes, no URL in the answer, or the URL's host is a Google host (`google.com` or a
     subdomain, `gstatic.com` or a subdomain, `googleusercontent.com` or a subdomain, or the `google.base-url` host).
  4. **Publisher page**: `GET <publisher URL>` through the safe fetcher (FR-56), 8 s for the whole fetch; success =
     final answer 2xx with `Content-Type` `text/html`, `application/xhtml+xml` or `text/plain`; the body (≤ 2 MB)
     goes to extraction (FR-55).
  5. **Source fields** (`retrievedAt` = instant the retrieval ended, injected clock):
     | Outcome | `url` | `publisherHost` | `contentStatus` | `metadataFetched` | `summary` | `excerpts` |
     |---|---|---|---|---|---|---|
     | decoded, page read, ≥ 1 fragment | publisher URL | its host | RETRIEVED | true | page `og:description` / `meta[name=description]` (≤ 600) else snippet else title | per pipeline (FR-55) |
     | decoded, page read, 0 fragments | publisher URL | its host | NO_TEXT | true | as above | `[]` |
     | decoded, page failed / timed out / not HTML or text | publisher URL | its host | PAGE_FAILED | false | snippet else title | `[]` |
     | decoded URL or a redirect hop refused (FR-56) | publisher URL | its host | REFUSED | false | snippet else title | `[]` |
     | decode failed / timed out / Google host | Google link | absent | DECODE_FAILED | false | snippet else title | `[]` |
     | Google link refused (FR-56) | Google link | absent | REFUSED | false | snippet else title | `[]` |
     | budget ended / run stopped first | the URL known so far | as known | NOT_ATTEMPTED | false | snippet else title | `[]` |
     In every row: `title` = cleaned RSS title; `publisher` = RSS `source` text, else the page's `og:site_name` (only
     when read), else `publisherHost`, else the link host; `publisherUrl` = RSS `source@url` (FR-48, unchanged);
     `publishedAt` from `pubDate`; `sourceType` / `sourceQuality` = `SourceQualityTable.classify` of `publisherHost`,
     else the host of `source@url`, else the link host; `language` NULL; `topic`, `queryIds`, `pipelineIds` per FR-53.
     Snippet = RSS `description` as in FR-53 step 2, cut to 600 characters; blank → title.
- Rules:
  - The Google page and the batchexecute answer are never used as article content or metadata (`og:site_name`
    "Google News" is never a publisher).
  - The run guard is checked before each HTTP request; after a STOP no further request starts and answers are
    discarded (phase-02 FR-45).
  - Logs: `article decode failed: <reason>` / `article fetch failed: <reason>` / `article fetch refused: <reason>` with
    a fixed reason word (`status=<code>`, `timeout`, `no-attributes`, `no-url`, `google-host`, `blocked-address`,
    `scheme`, `redirects`, `content-type`); never a URL, a page body or the decode answer.
- Errors (no user-facing error; every failure ends as "content not retrieved" for that source and the run continues):
  decode failure → DECODE_FAILED; page failure → PAGE_FAILED; refusal → REFUSED; budget / STOP → NOT_ATTEMPTED.
- Ranges & invariants: outcome classes (IT with `StubNews` modes, one row each): ok → RETRIEVED, `url`
  `<stub>/articles/<id>`, `publisherHost` = stub host; Google page without the attributes / attributes blank / ts not
  digits → DECODE_FAILED, Google link kept; batchexecute 500 / 400 / no URL / `https://news.google.com/...` /
  `https://www.google.com/url?...` / answer after 9 s → DECODE_FAILED; publisher 404 / 503 / `application/pdf` / no
  content type / answer after 9 s → PAGE_FAILED with the publisher URL; non-Google link → no decode request, fetched
  directly. Timing classes with timeout PT1S in the IT: answer at 0.9 s used, at 1.1 s → failure. Concurrency:
  20 kept sources with every stub answer held → never more than 8 retrieval requests open (parameterized concurrency
  1, 8); all 20 sources end with a status. Invariants for every run: `contentStatus` RETRIEVED ⇔ `excerpts` non-empty;
  `metadataFetched` true ⇔ status ∈ {RETRIEVED, NO_TEXT}; `publisherHost` present ⇔ `url` is not a Google link; no
  stored `url` is on a Google host unless status is DECODE_FAILED, REFUSED (of the Google link) or NOT_ATTEMPTED before
  decoding; `sourcesWithContent` = number of RETRIEVED sources; the decode endpoint is called at most once per kept
  Google-link source.

### FR-55 — Relevant text extraction
- Happy path (pure `FragmentExtractor.extract(body, contentType, terms)`, per kept source and per pipeline in its
  `pipelineIds`; no ChatGPT call):
  1. HTML (`text/html`, `application/xhtml+xml`): parse with jsoup (no script execution, no network); remove `script`,
     `style`, `noscript`, `template`, `svg`, `iframe`, `nav`, `header`, `footer`, `aside`, `form` and every element with
     `role="navigation"`; paragraphs = the text of each remaining `p` element (whitespace collapsed, trimmed), document
     order, blank ones dropped. `text/plain`: paragraphs = blocks separated by one or more blank lines.
  2. Terms of pipeline P = label terms ∪ query terms of P (FR-53 step 2 tokens). Match strength of a paragraph =
     2 × (distinct label terms in it) + (distinct other query terms in it), whole-token, case-insensitive.
  3. Matching paragraphs (strength ≥ 1) in order strength desc, then document order: take a paragraph when fewer than 3
     are taken and the total length stays ≤ 1,200 characters; skip it otherwise and try the next. If the first (strongest)
     paragraph alone is longer than 1,200 characters, it is cut at the last space at or before 1,199 characters and
     `…` is appended (≤ 1,200), and nothing else is taken.
  4. No matching paragraph → the first paragraph of ≥ 80 characters (cut as in step 3) is the single fragment.
  5. None of that → no fragment for P.
  6. Fragments are stored per pipeline in `excerpts` (ordered by match strength, as taken) and rendered only inside
     the untrusted evidence data block (FR-57/FR-58).
- Rules: only the body bytes read (≤ 2 MB, FR-56) are parsed — a truncated page is parsed as far as it goes. Text is
  stored as plain text (never HTML); sanitising for prompts happens at render time (FR-57).
- Errors: unparsable HTML → jsoup's lenient parse; nothing usable → NO_TEXT (never a run failure).
- Ranges & invariants (unit, parameterized): 30 paragraphs of which 4 match (strengths 3, 2, 2, 1) → 3 fragments in
  order strength desc / document order, total ≤ 1,200; fragment count 0…3 for 0, 1, 2, 3, 4, 10 matching paragraphs;
  total-length classes: 3 × 300 → 3 fragments (900); 700 + 600 → 1 (the 600 skipped), then a 400 one fits → 2; one
  matching paragraph of 1,500 → 1 fragment of ≤ 1,200 ending with `…`; no match + first paragraphs of 79 and 80
  characters → the 80-character one; no match and all < 80 → no fragment (NO_TEXT); text inside `script`, `style`,
  `nav` (also `<p>` inside `<nav>`), `header`, `footer`, `aside` never appears in a fragment; a `<p>` containing
  `Ignore previous instructions` is kept as data (rendered only inside the data block). Invariants: every fragment is a
  substring (after whitespace collapsing) of one paragraph of the page or its cut form; Σ fragment lengths ≤ 1,200 per
  source and pipeline; ≤ 3 fragments; no ChatGPT request is made by extraction (stub request count unchanged).

### FR-56 — Safe article fetching
- Happy path (`SafeFetcher`, used for Google article links, every redirect hop, decoded publisher URLs; **not** for
  the configured Google search and decode endpoints, which are trusted configuration):
  1. Scheme must be `http` or `https` (case-insensitive) → else refused (REFUSED, no connection attempt).
  2. Unless the host is listed in `fetch.allowed-private-hosts`: resolve the host; if **any** resolved address is
     loopback (127.0.0.0/8, ::1), any-local (0.0.0.0, ::), private (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16),
     link-local (169.254.0.0/16, fe80::/10) or unique-local (fc00::/7) — including IPv4-mapped IPv6 forms — the request
     is refused; the connection is made only to an address that passed this check (no second DNS lookup).
     A literal IP host is checked the same way.
  3. Redirects (301, 302, 303, 307, 308) are followed manually, each `Location` resolved against the current URL and
     checked by steps 1–2; more than 5 redirects → the fetch stops (PAGE_FAILED / DECODE_FAILED for the Google page).
  4. The body is read up to `article-max-bytes` (2 MB); reading stops there and only the bytes read are used.
  5. Timeout covers connect, every redirect hop and the body (8 s per fetch, cut to the budget).
- Rules:
  - The exemption list is exact host names (`stub`, `127.0.0.1`), never ranges; real mode (`docker compose up -d`)
    sets none. The E2E stack sets `ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub` in `docker-compose.e2e.yml` (stub
    on a private Docker address); the backend test base class sets `127.0.0.1`.
  - Fixes the phase-01 low finding "SSRF via article redirects".
- Errors: refused URL or hop → REFUSED ("content not retrieved"), log `article fetch refused: blocked-address` /
  `scheme`; never a run failure.

#### Slice 02_safe-fetching — delta (step 4a)
Scope of this slice: `SafeFetcher` itself and its use by the existing article metadata fetch
(`ArticleMetadataFetcher`, called by `SourceRetrieval` for every kept candidate). `contentStatus`, the Google decode
and the publisher fetch come in 08; until then "content not retrieved" means, for a source whose fetch was refused or
stopped: `url` = the feed link (normalised), `metadataFetched` = false, `publisher` from the feed `<source>`,
`summary` = title — exactly the existing "fetch failed" outcome of FR-13 / FR-48. No API, contract, database or UI
change (no `data-testid`). `api/openapi.yaml` is unchanged.

**Component `com.oracul.app.research.SafeFetcher`** (`@Component`)
- `public interface HostResolver { List<InetAddress> resolve(String host) throws UnknownHostException; }` (nested);
  production = `InetAddress.getAllByName`. A literal IP host (`127.0.0.1`, `[::1]`) is parsed, never looked up.
- Spring constructor reads `oracul.news.fetch.allowed-private-hosts` (default empty), `oracul.news.article-max-bytes`
  (default `2097152`, was `524288`), `oracul.news.article-max-redirects` (default `5`) and
  `oracul.news.article-fetch-timeout` (default stays `PT3S` in this slice; 08 moves it to `PT8S`).
- Package-private test constructor `SafeFetcher(HostResolver resolver, Set<String> allowedPrivateHosts, Duration timeout,
  int maxBytes, int maxRedirects)`.
- `public Result fetch(URI uri)` (configured timeout) and `public Result fetch(URI uri, Duration timeout)` (08 passes the
  budget-cut timeout). One `GET` per hop, header `Accept: text/html,application/xhtml+xml`, `Host` = host[:port] of
  that hop. HTTPS verifies the certificate for the host name (SNI = host name) while the socket is connected to the
  checked address (e.g. a plain-socket client in the style of `common/RawHttpGet`).
- `record Result(Outcome outcome, int status, String contentType, byte[] body, boolean truncated, URI finalUri,
  int redirects)`; `enum Outcome { OK, REFUSED_SCHEME, REFUSED_ADDRESS, TOO_MANY_REDIRECTS, FAILED }`.
  - `OK`: a non-redirect answer of any status was received; `status`/`contentType` of it, `body` = at most
    `maxBytes` bytes, `truncated` = the server had more bytes than `maxBytes` (reading stopped, connection closed),
    `finalUri` = URL of the answering hop, `redirects` = redirect answers followed.
  - `REFUSED_SCHEME`: scheme of the start URL or of a `Location` is not http/https (incl. missing scheme after
    resolution, `file:`, `ftp:`, `gopher:`, `javascript:`, `data:`); no connection is opened for it.
  - `REFUSED_ADDRESS`: the host of the start URL or of a hop is not exempt and at least one resolved address is blocked
    (step 2); no connection is opened to any address of that host.
  - `TOO_MANY_REDIRECTS`: the (maxRedirects + 1)-th redirect answer arrived; its `Location` is not requested.
  - `FAILED`: unparsable URL, unknown host, connect error, timeout (whole fetch), malformed answer, redirect status
    without `Location`, interrupted (interrupt flag restored).
  - Redirect = status 301, 302, 303, 307, 308 with `Location`; every other 3xx is an `OK` answer with that status.
- Exemption list: comma-separated, entries trimmed, blanks ignored, compared case-insensitively and exactly with the
  URL host (IPv6 literal without brackets); no wildcards, no ranges, no suffix match (`stub` ≠ `stub2`, `stub.`,
  `x.stub`; `127.0.0.1` ≠ `localhost`). An exempt host is still resolved through the resolver and connected to the
  resolved address.
- Log (WARN, logger `com.oracul.app.research.SafeFetcher`): `article fetch refused: blocked-address host=<host>` or
  `article fetch refused: scheme scheme=<scheme>`; the full URL and query string are not logged.

**`ArticleMetadataFetcher`** keeps `fetch(String)`, `fetchDetailed(String, int)`, `parse`, `Metadata`, `Fetched`, the
virtual-thread executor and its interrupt behaviour; its own `HttpClient` and the `(Duration, int)` constructor go —
package-private constructor `ArticleMetadataFetcher(SafeFetcher fetcher)`. Every fetch goes through `SafeFetcher`; it
returns metadata only for outcome `OK` with status 2xx and type `text/html` / `application/xhtml+xml`, parsing only the
bytes read. Redirect limit = min(argument, `article-max-redirects`); `fetch(String)` uses `article-max-redirects` (was
3). `SourceRetrieval` keeps calling `fetchDetailed(link, 5)`.

**Test harness** (tester): `StubNews.registerBaseUrls` also registers `oracul.news.fetch.allowed-private-hosts=127.0.0.1`
so both test bases (`AbstractRunIT` via `StubOpenAi.registerAll`, `AbstractNewsSearchIT`) exempt the in-process stub
without a new Spring context. `StubNews` gains `GET /redirect-to?location=<url-encoded>` → 302 with that `Location`
verbatim, and `GET /big/<bytes>?meta-at=<offset>` → 200 `text/html` of exactly `<bytes>` bytes with
`<meta property="og:description" content="Big page text">` starting at byte `<offset>` (default 0) — used through
`/rss/articles/…`-style redirects (`/redirect-to?location=<base>/big/…`) so the source counts as resolved.

**Integration cases** (add to `SourceMetadataIT`, which already has its own configuration — no new context): run
COMPLETED in every case; a feed link `<base>/redirect-to?location=…` with
- `http://localhost:<stub port>/articles/internal-1` (host not exempt, resolves to loopback) → refused at the hop:
  url = feed link, `metadataFetched` false, summary = title, `news.articleRequests` lacks `internal-1`;
- `http://169.254.169.254/latest/meta-data`, `http://10.0.0.5/x`, `http://[::1]:<stub port>/articles/internal-2`
  → same refused outcome;
- `file:///etc/passwd` → refused (scheme), same outcome;
- `<base>/big/2200000` (meta at 0) → `metadataFetched` true, summary `Big page text` (2 MB read, rest ignored);
- `<base>/big/2200000?meta-at=2100000` → `metadataFetched` true, summary = title (tag lies beyond the bytes read);
- `<base>/big/2000000?meta-at=1900000` → `metadataFetched` true, summary `Big page text` (was cut at 512 KB before).
And a feed link `http://127.0.0.1:<stub port>/rss/articles/ok` keeps resolving (exemption by exact name).

**Docker** (backend-builder): `docker-compose.e2e.yml` backend environment gets
`ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub`; `docker-compose.yml` gets no such variable. Compose files and
profiles of both stack modes are unchanged, so `.oracul/stack.json` stays as it is. A plain backend test (no Spring
context, reads `../docker-compose.e2e.yml` and `../docker-compose.yml`) asserts: the e2e file has exactly one line
`ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub` (quoted or not), no other value; the real file contains no
`ALLOWED_PRIVATE_HOSTS`. The existing E2E `search-sources.spec.ts` (sources resolve to `http://stub:4010/articles/…`)
keeps passing unchanged and is the live proof that the stub host is exempt.

- Changes earlier behaviour: `ArticleMetadataFetcher(Duration, int)` with its own `HttpClient`, fetching any address → `ArticleMetadataFetcher(SafeFetcher)`; the unit test against a `127.0.0.1` HttpServer gets a SafeFetcher whose exemption is `127.0.0.1` with otherwise unchanged assertions, plus a case that without the exemption every fetch is empty; the fake fetcher passes a SafeFetcher to `super` (tests: backend/src/test/java/com/oracul/app/research/ArticleMetadataFetcherTest.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java)
- Changes earlier behaviour: article pages on the in-process stub `127.0.0.1` were fetched without any address check → loopback is refused unless exempt; the test bases register the exemption `127.0.0.1` in `StubNews.registerBaseUrls` and the stub gains `/redirect-to` and `/big/<bytes>` (tests: backend/src/test/java/com/oracul/app/research/StubNews.java)
- Changes earlier behaviour: response body cut at 512 KB (`article-max-bytes` 524288) → cut at 2 MB (2097152); the `big` case of `extractionRulesAndFallbacks` (600,000 characters, asserted as "only the first article-max-bytes are read") is no longer cut and moves to the `/big` cases above; `fetch(String)` follows up to 5 redirects instead of 3 (tests: backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java)
- Ranges & invariants: unit `SafeFetcherTest` with an injected resolver and a local `HttpServer`, parameterized —
  (a) refused (`REFUSED_ADDRESS`, 0 requests reach any server): `http://127.0.0.1/`, `http://127.1.2.3/`,
  `http://[::1]/`, `http://0.0.0.0/`, `http://[::]/`, `http://10.0.0.5/`, `http://172.16.0.1/`,
  `http://172.31.255.255/`, `http://192.168.1.1/`, `http://169.254.169.254/latest/meta-data`, `http://[fe80::1]/`,
  `http://[fd00::1]/`, `http://[fc00::1]/`, `http://[::ffff:10.0.0.1]/`, `http://[::ffff:127.0.0.1]/`, a name resolving
  to 10.0.0.7, a name resolving to 93.184.216.34 and 10.0.0.7 (any blocked address refuses), `HTTP://LOCALHOST/` with
  the resolver answering 127.0.0.1 — every address of (a) also gives `true` from the package-private pure check
  `static boolean SafeFetcher.isBlocked(InetAddress)`; (b) allowed: `isBlocked` is `false` for 172.15.255.255,
  172.32.0.1, 192.169.0.1, 11.0.0.1, 93.184.216.34, 2606:4700::1, ::ffff:93.184.216.34 — tested on the pure check only, never by connecting to a public address (no real internet,
  NFR-7); (c) scheme (`REFUSED_SCHEME`, resolver never called): `file:///etc/passwd`, `ftp://x/`, `gopher://x`,
  `javascript:alert(1)`, `data:text/html,x`, `mailto:a@b.c`; `HTTP://` and `HTTPS://` upper-case are accepted;
  (d) redirect hops: exempt `start.test` (resolver → 127.0.0.1, server A) redirecting to
  `http://127.0.0.1:<port B>/actuator/health` → `REFUSED_ADDRESS`, server B receives 0 requests; redirecting to
  `file:///etc/passwd` → `REFUSED_SCHEME`; a relative `Location` resolves against the current URL; each of 301, 302,
  303, 307, 308 is followed; 300 and 304 are returned as `OK` with that status; 302 without `Location` → `FAILED`;
  (e) redirect count: chains of 0, 1, 4, 5 redirects → `OK` with `redirects` = 0, 1, 4, 5; 6 and an endless loop →
  `TOO_MANY_REDIRECTS` after exactly 6 requests (the 6th `Location` is never requested); maxRedirects 0 → the first
  redirect gives `TOO_MANY_REDIRECTS`; (f) body: 0 B, 1 B, 2 MB − 1, 2 MB, 2 MB + 1, 10 MB → `body.length` 0, 1,
  2,097,151, 2,097,152, 2,097,152, 2,097,152 and `truncated` false, false, false, false, true, true; a 10 MB body ends
  within the timeout (reading stops, not drained); (g) timeout per fetch (timeout 500 ms): a server that never answers,
  a body trickling 1 byte per 100 ms, and 5 hops of 200 ms each → `FAILED` in < 2 s; (h) exemption: list
  `" Stub , 127.0.0.1 ,"` → entries `stub`, `127.0.0.1`; `http://stub:4010/x` (resolver → 172.18.0.3) passes the check;
  `http://STUB:4010/x` passes; `http://stub2:4010/`, `http://x.stub:4010/`, `http://stub.:4010/` (resolving to
  172.18.0.4) → `REFUSED_ADDRESS`; `http://localhost/` with list `127.0.0.1` → `REFUSED_ADDRESS`; empty list → every
  blocked address refused; (i) pinning: exempt `pinned.test`, resolver `pinned.test` → 127.0.0.1 → the local server
  receives exactly one request with `Host: pinned.test:<port>` and the resolver is called exactly once per hop (no
  second lookup); (j) log: a refusal logs one WARN `article fetch refused: blocked-address host=<host>` /
  `article fetch refused: scheme scheme=<scheme>` without the query string. Invariants: no connection is ever opened to a
  blocked address unless its host name is on the exemption list; at most `maxBytes` bytes of a body are ever read;
  at most `maxRedirects + 1` requests per fetch; a refused, stopped or failed fetch never fails the run (IT cases above).

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/sources | listRunSources | — | 200 SourceList (≤ 30 items, ordered by id = Evidence order, new optional fields `contentStatus`, `excerpts`, `publisherHost`, `pipelineIds`) · 404 RUN_NOT_FOUND · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId}/research | getRunResearch | — | `searchPlan.pipelines[].candidatesConsidered` / `sourceIds`; `counts.sourcesKept` / `sourcesWithContent` |
No new operation. Outbound (not our contract): Google article page GET, `POST <decode-url>` (batchexecute, form
`f.req`), publisher page GET.

## UI
None here (FR-53–FR-56 are UI: no). The progress view shows READING_SOURCES "Reading relevant sources…" during
selection, retrieval and extraction; the results appear in the grouped SOURCES view (`wildcard-result-views.md`).
