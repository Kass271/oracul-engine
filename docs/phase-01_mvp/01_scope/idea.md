# Idea — phase-01_mvp

Received: 2026-10-02

> Start new phase development: here is requirements @/Users/frederiks/courses/agent_crash/ORACUL_SPECIFICATION.md

## Attached specification (verbatim copy of ORACUL_SPECIFICATION.md)

# ORACUL

## Complete Product & Functional Specification

### Version 1.0 --- Development Baseline

> **Source of truth for development.**

## 1. PRODUCT

**Name:** ORACUL

Always spell the product **O-R-A-C-U-L**. Never use "Oracle" as the
product name.

ORACUL is an interactive AI-powered **Future Scenario Engine**.

Its purpose is to let users explore possible futures based on: - real
current news; - current real-world events; - user-selected scenario
parameters; - optional hypothetical wildcards; - controlled AI
reasoning.

ORACUL does **not** claim to predict the future. It generates possible
scenarios.

> **ORACUL explores possible futures by combining the world as it is
> with the world the user asks it to imagine.**

## 2. CORE PRODUCT EQUATION

``` text
CURRENT REALITY
       +
USER SCENARIO SETTINGS
       +
WILDCARDS
       +
CONTROLLED AI REASONING
       =
POSSIBLE FUTURE
```

**ORACUL determines:** what is happening now.\
**ChatGPT determines:** what could follow from the evidence ORACUL
provided.\
**The user determines:** what kind of future should be explored.

## 3. FUNDAMENTAL ARCHITECTURAL PRINCIPLE

The application must **not** work as:

``` text
Find random news
      +
Ask ChatGPT to make it pessimistic
      =
Future story
```

It must work as:

``` text
USER SETTINGS
      ↓
RESEARCH PROFILE
      ↓
SEARCH STRATEGY
      ↓
NEWS SEARCH
      ↓
RAW RESULTS
      ↓
NORMALIZATION
      ↓
DEDUPLICATION
      ↓
CLASSIFICATION
      ↓
SCENARIO-AWARE RANKING
      ↓
EVIDENCE SELECTION
      ↓
EVIDENCE PACK
      ↓
DYNAMIC PROMPT
      ↓
ChatGPT
      ↓
STRUCTURED FUTURE SCENARIO
      ↓
VALIDATION
      ↓
FINAL FUTURE STORY
```

User scenario controls influence both **research** and **AI reasoning**.

## 4. PRIMARY USER EXPERIENCE

ORACUL should primarily operate as one interactive page.

Desktop layout:

``` text
┌───────────────────────────────────────────────────────────────┐
│ ORACUL                                  Continue with ChatGPT │
├──────────────────┬────────────────────────────────────────────┤
│ SCENARIO         │                  ORACUL                    │
│                  │                                            │
│ Darkness     7   │          What happens next?                │
│ Optimism     5   │                                            │
│ Realism      8   │        GENERATE THE FUTURE                 │
│                  │                                            │
│ Horizon          │                                            │
│ 1m 1y 5y 20y    │                                            │
│                  │                                            │
│ WILDCARDS        │                                            │
│ □ Robots         │                                            │
│ □ Viruses        │                                            │
│ □ AGI            │                                            │
│ □ Energy         │                                            │
│ □ Climate        │                                            │
│                  │                                            │
│ OUTPUT           │                                            │
│ ☑ Story          │                                            │
│ □ Illustration   │                                            │
└──────────────────┴────────────────────────────────────────────┘
```

The left panel remains available after generation. The center changes
from welcome state into generated scenario state.

## 5. CHATGPT INTEGRATION

The MVP supports only ChatGPT/OpenAI.

Do not initially build Claude, Gemini, provider selectors, or
unnecessary multi-provider UI.

Intended flow:

``` text
ORACUL
   ↓
Continue with ChatGPT
   ↓
Official OpenAI authentication
   ↓
User authorization
   ↓
ORACUL
```

Use only officially supported OpenAI authentication/integration
mechanisms. Where officially supported, use the user's eligible ChatGPT
subscription/plan rather than requiring a manually entered API key.

Do not scrape ChatGPT, extract cookies, automate private ChatGPT
endpoints, impersonate the web client, or bypass provider restrictions.

## 6. AUTHENTICATION & CREDENTIAL SECURITY

ORACUL must never ask the user to enter a ChatGPT password directly.

ORACUL must not persistently store: - ChatGPT passwords; - OpenAI API
keys; - access tokens; - session cookies; - authorization headers; -
authentication codes; - authentication secrets.

Credentials should exist only in runtime memory for the minimum lifetime
required by the official authentication flow.

They must not be written to: - application databases; - PostgreSQL; -
Redis persistence; - files; - environment dumps; - browser
localStorage; - IndexedDB; - application logs; - telemetry; -
analytics; - crash reports; - exception traces; - debugging output.

If an official OpenAI flow technically requires secure refresh-token
persistence, the development agent must identify this explicitly before
implementation and isolate it behind a dedicated credential-management
design. Runtime-only credentials are preferred.

> **Security invariant: ORACUL stores scenarios and evidence --- never
> ChatGPT credentials.**

Logging middleware must automatically redact sensitive authentication
data. Never log bearer tokens, API keys, access tokens, refresh tokens,
cookies, or equivalent secrets.

## 7. MAIN SCENARIO CONTROLS

The left Scenario Panel is the primary control surface.

Initial controls: - Darkness - Optimism - Realism - Time Horizon -
Wildcards - Output options

Most intensity controls use a **1--10** scale.

## 8. DARKNESS

Darkness controls preference toward negative developments such as
conflict, economic instability, biological threats, technological
failures, social instability, environmental disasters, systemic
failures, and institutional instability.

Darkness affects: - search planning; - news ranking; - evidence
selection; - scenario generation.

It does **not** modify facts.

## 9. OPTIMISM

Optimism increases preference toward scientific breakthroughs, medical
advances, economic improvements, technological solutions, energy
breakthroughs, successful cooperation, environmental recovery, and
productivity improvements.

Optimism and Darkness are independent. `Darkness = 9` and `Optimism = 9`
is valid and may yield a severe crisis that triggers a major
breakthrough.

## 10. REALISM

Realism determines how far ORACUL may extrapolate from evidence.

-   **10:** extremely conservative; short causal chains and strong
    evidence.
-   **7--9:** plausible extrapolation; second-order consequences
    allowed.
-   **4--6:** speculative but logically coherent.
-   **2--3:** low-probability developments allowed.
-   **1:** highly imaginative alternative future.

Low Realism allows more speculative **future** events. It never permits
invented **current** facts.

## 11. TIME HORIZON

Initial options: - Tomorrow - 1 week - 1 month - 1 year - 5 years - 10
years - 20 years

Horizon affects search relevance, trend selection, causal-chain length,
and acceptable extrapolation.

## 12. WILDCARDS

Wildcards are hypothetical scenario factors.

Initial categories may include:

**AI** - AGI breakthrough - AI stagnation - AI loss of control

**Robotics** - Massive automation - Humanoid robot boom - Robot uprising

**Biology** - New pandemic - Dangerous mutation - Major medical
breakthrough - Synthetic biology breakthrough

**Political / institutional scenarios** - Democratic institutions
strengthen - Authoritarian systems expand - International institutions
strengthen - Global fragmentation increases

These are scenario assumptions, not endorsements or evaluations of
specific political actors.

**Economy** - Global economic boom - Global recession - Financial crisis

**Energy** - Fusion breakthrough - Cheap energy - Energy crisis

**Environment** - Extreme climate event - Climate stabilization -
Ecosystem collapse

**Space** - Major space discovery - Asteroid threat - Moon settlement -
Mars breakthrough

**Extreme speculation** - Alien contact - Unknown intelligence -
Unexplained global phenomenon

Wildcards may support intensity, e.g. `Viruses 8/10`, `Robotics 6/10`.
Enabled wildcards influence both research and future generation but must
not be forced into the final scenario without a coherent relationship.

Users may add custom wildcards.

## 13. OUTPUT SETTINGS

Initial defaults:

``` text
Story          ON
Illustration   OFF
```

Illustration may be MVP+1 depending on available OpenAI subscription
capabilities. Generated illustrations must be visibly marked
**AI-GENERATED ILLUSTRATION**.

## 14. GENERATE ACTION

Primary CTA: **GENERATE THE FUTURE**

This starts one Generation Run. Prevent accidental duplicate
simultaneous runs.

## 15. RESEARCH PROFILE

Before searching, ORACUL converts user settings into an internal
Research Profile.

``` json
{
  "darkness": 0.9,
  "optimism": 0.2,
  "realism": 0.8,
  "horizon": "5y",
  "topics": {
    "viruses": 0.8,
    "robotics": 0.6
  }
}
```

This controls research.

## 16. SEARCH PLAN & QUERY GENERATION

ORACUL converts Research Profile into search intents and dynamically
expands those intents into search queries.

For example, `Viruses = 8` and `Darkness = 9` may produce intents around
emerging infectious diseases, zoonotic transmission, unusual disease
clusters, mutations, epidemiological warnings, animal outbreaks, and
pandemic preparedness.

`Robotics = 6` may produce intents around humanoid deployment,
manufacturing, industrial automation, investment, safety incidents,
autonomous systems, and labor automation.

Do not rely exclusively on a static hardcoded query list.

## 17. BASELINE WORLD SEARCH

Wildcards must not create complete tunnel vision.

A conceptual configurable search budget may begin around: - 40%
wildcard-specific research; - 30% major current events; - 20% adjacent
topics; - 10% unexpected signals.

The purpose is discovery of unexpected causal connections.

## 18. CANDIDATE NEWS POOL

Search should intentionally retrieve more information than ultimately
reaches ChatGPT.

Example:

``` text
Search queries:             18
Raw articles:              240
Basic filtering:           130
Unique normalized events:   82
Evidence selected:          25
```

Do not send the full raw pool to ChatGPT.

## 19. SOURCE DATA

For each retrieved source retain: - URL; - publisher; - title; -
publication date; - retrieval timestamp; - extracted summary; - topic; -
entities; - source type.

Prefer primary sources, official publications, established news
organizations, research institutions, and scientific sources where
possible.

## 20. NORMALIZED EVENTS & DEDUPLICATION

Articles are not the primary reasoning unit. Convert them into
normalized events.

``` json
{
  "id": "E001",
  "date": "2026-10-02",
  "category": "biology",
  "entities": ["..."],
  "summary": "...",
  "sources": ["S001", "S014"],
  "confidence": 0.91
}
```

When several publications report the same event, create one event with
multiple sources rather than several separate events.

## 21. EVENT ENRICHMENT

Each normalized event receives semantic metadata such as:

``` json
{
  "topic": "biology",
  "subtopics": ["zoonotic disease", "virology"],
  "sentiment": -0.78,
  "risk": 0.87,
  "opportunity": 0.11,
  "impact": 0.72,
  "novelty": 0.64,
  "sourceQuality": 0.92,
  "trend": "emerging",
  "wildcardMatches": {
    "viruses": 0.96
  }
}
```

Do not determine positive/negative relevance using keywords alone.
Classify direction, risk, opportunity, severity, and impact
semantically.

## 22. SCENARIO-AWARE RANKING

Every normalized event receives a relevance score.

Conceptually:

``` text
EventScore =
    TopicMatch
  + WildcardMatch
  + DarknessMatch
  + OptimismMatch
  + Recency
  + SourceQuality
  + ImpactPotential
  + TrendStrength
  + CrossTopicPotential
  + RealismCompatibility
```

Exact weights belong to implementation/configuration.

Darkness and Optimism affect ranking, not factual truth.

Source reliability and scenario relevance are separate dimensions. An
unreliable sensational story must not outrank a high-quality source
merely because it matches a dark scenario.

High Realism favors recent, strong, confirmed, multi-source evidence and
established trends. Low Realism may increase the relevance of credible
weak signals, unusual research, early-stage technologies, and
low-probability developments. Low Realism never means accepting
misinformation.

## 23. DIVERSITY & COUNTER-SIGNALS

Do not select many nearly identical stories simply because they match a
scenario.

Maintain diversity across: - events; - entities; - geography; - causal
dimensions; - sources.

ORACUL intentionally retains relevant evidence contradicting the
dominant requested direction. These **Counter-Signals** reduce
confirmation bias and improve scenario quality.

## 24. EVIDENCE SELECTION & IDs

A typical selection may contain:

``` text
Candidate unique events: 82

Core evidence:           10
Supporting evidence:     10
Counter-signals:          5

Evidence Pack:           25
```

Every selected event receives a stable Evidence ID such as `E001`,
`E002`, etc.

## 25. EVIDENCE PACK

Conceptually:

``` text
ORACUL EVIDENCE PACK

Generation:
ORC-2026-10-02-1842

Cutoff:
2026-10-02T18:42Z

SCENARIO
Realism:   8
Darkness:  9
Optimism:  2
Horizon:   5 years

WILDCARDS
Viruses:   8
Robotics:  6

CORE EVIDENCE
[E001] ...
[E002] ...

SUPPORTING EVIDENCE
[E010] ...

COUNTER-SIGNALS
[E021] ...
```

This is the **only current-world factual context** available to the
scenario-generation model.

## 26. CHATGPT RESPONSIBILITY BOUNDARY

ChatGPT is the **reasoning engine**.

ChatGPT is **not**: - a researcher; - a news search engine; - a
verifier; - a source of current-world facts.

ORACUL searches and selects evidence. ChatGPT reasons only from the
Evidence Pack.

## 27. CLOSED EVIDENCE MODE

Scenario generation operates under **Closed Evidence Mode**.

ChatGPT must not: - search the web; - call search tools; - browse
URLs; - retrieve additional news; - add remembered current events; -
supplement evidence using training knowledge; - independently verify
stories; - invent supporting current facts.

The generation model should not receive web-search capability.

Model knowledge may be used for general reasoning, e.g. "large-scale
manufacturing can reduce unit production costs," but not to introduce
specific current facts absent from Evidence Pack.

Missing information must remain unknown.

## 28. INFORMATION CLASSES

All reasoning distinguishes four classes:

### FACT

Directly supported by Evidence Pack. Must reference Evidence IDs.

### INFERENCE

Logical interpretation of facts. Should reference supporting Evidence
IDs.

### SPECULATION

Hypothetical development clearly identified as speculative.

### FUTURE EVENT

Final generated scenario, never represented as current reality.

Example: if evidence says a new virus was detected in animals, limited
animal-to-human transmission occurred, sustained human transmission is
unconfirmed, and a vaccine platform is being investigated, the model may
speculate: "If the virus develops efficient human-to-human
transmission..." It may not invent a current mutation that ORACUL never
supplied.

Darkness never grants permission to invent evidence.

## 29. DYNAMIC PROMPT BUILDER

The final prompt is created only after research:

``` text
USER SETTINGS
      ↓
SEARCH
      ↓
NORMALIZATION
      ↓
RANKING
      ↓
EVIDENCE PACK
      ↓
PROMPT BUILDER
      ↓
ChatGPT
```

It combines: 1. stable ORACUL instructions; 2. Scenario Configuration;
3. Wildcards; 4. Evidence Pack; 5. Counter-Signals; 6. generation
requirements.

Every request must include instructions equivalent to:

``` text
You are the scenario reasoning component of ORACUL.

You are NOT a researcher.

The provided ORACUL Evidence Pack is your ONLY source
of factual information about the current world.

Do not search, retrieve, recall, invent, verify or
supplement current-world facts.

Do not introduce factual claims from model training knowledge.

You may analyze provided evidence, identify relationships,
derive clearly labeled inferences, construct hypothetical
consequences, and create clearly labeled future events.

You must distinguish:
FACT
INFERENCE
SPECULATION
FUTURE EVENT

Every FACT must reference one or more ORACUL Evidence IDs.

If necessary information is missing, state that it is unknown.

Never fill factual gaps with plausible invented information.
```

## 30. CANDIDATE FUTURES & STRUCTURED OUTPUT

ChatGPT should consider multiple candidate scenarios and evaluate them
against evidence, Realism, Darkness, Optimism, Wildcards, Time Horizon,
causal coherence, and counter-signals.

The first model output should be structured rather than immediately
producing the final article.

Example:

``` json
{
  "factsUsed": ["E001", "E004", "E008"],
  "inferences": [],
  "speculations": [],
  "counterSignalsConsidered": [],
  "causalChain": [],
  "futureEvent": {}
}
```

## 31. EVIDENCE GUARD & CRITIC

Before final rendering:

``` text
STRUCTURED SCENARIO
       ↓
EVIDENCE GUARD
       ↓
Are factual claims supported?
Does every fact have Evidence IDs?
Are invented present-day facts present?
Does the causal chain make sense?
       ↓
PASS / FAIL
```

On failure, remove the unsupported claim, request regeneration, or
reject the candidate.

A critic validation should check: - unsupported factual jumps; -
contradictions; - unrealistic timeline; - ignored counter-signals; -
wildcard forcing; - mismatch with user configuration; - inappropriate
certainty.

The critic also operates under Closed Evidence Mode.

## 32. FINAL STORY

After validation, transform the structured scenario into an engaging
"story from the future."

Every scenario must visibly contain:

**AI-GENERATED FUTURE SCENARIO**

and/or:

**POSSIBLE FUTURE --- NOT CURRENT NEWS**

A generated scenario must never be easily mistaken for a real current
news article.

## 33. SCENARIO METADATA

Display metadata such as:

``` text
Realism      8/10
Darkness     9/10
Optimism     2/10

Horizon      5 years

Viruses      8/10
Robotics     6/10

Articles considered: 143
Unique events:        67
Evidence used:        25
```

## 34. WHY COULD THIS HAPPEN?

Every scenario includes **WHY COULD THIS HAPPEN?**

This opens a user-facing causal explanation, not private model
chain-of-thought.

Example:

``` text
FACT [E001]
Robot production capacity increased.
        ↓
FACT [E008]
Investment in warehouse automation increased.
        ↓
INFERENCE [E001, E008]
Automation hardware may become more widely available.
        ↓
SPECULATION
Operating costs could fall enough for night automation to expand.
        ↓
ORACUL FUTURE — 2031
Fully autonomous night operations become common in some logistics hubs.
```

## 35. SOURCES & RESEARCH TRANSPARENCY

Every scenario includes **SOURCES**, linking to the actual source
material used.

Provide **WHY THESE NEWS?** explaining which topics ORACUL researched
because of the selected scenario controls.

Optionally show a research summary such as:

``` text
18 searches performed
143 articles considered
67 unique events identified
25 events selected
5 counter-signals retained
12 sources directly influenced the final scenario
```

## 36. GENERATION PROGRESS

During execution show high-level progress such as: - Understanding your
future... - Building research strategy... - Searching current
events... - Reading relevant sources... - Connecting signals... -
Ranking evidence... - Exploring possible futures... - Challenging
assumptions... - Constructing scenario... - Writing from the future...

Avoid unnecessary technical details.

## 37. QUICK REGENERATION & ALTERNATIVE FUTURES

After generation provide: - **MORE REALISTIC** - **DARKER** - **MORE
OPTIMISTIC** - **MORE EXTREME** - **ALTERNATIVE FUTURE**

`GENERATE ALTERNATIVE` should preserve the current configuration and,
where appropriate, Evidence Pack while exploring another causal path. It
must not merely paraphrase the previous story.

Core loop:

``` text
GENERATE
    ↓
READ
    ↓
UNDERSTAND WHY
    ↓
CHANGE ONE VARIABLE
    ↓
GENERATE AGAIN
```

## 38. DEFAULT SETTINGS

``` text
Realism      8
Darkness     5
Optimism     5
Horizon      1 year
Wildcards    OFF
Story        ON
Illustration OFF
```

## 39. SESSION STATE

Maintain during the current session: - scenario configuration; -
generated scenarios; - Evidence Packs; - source references; - recent
generations.

Credentials must never become part of session history.

## 40. FAILURE & UNCERTAINTY

For insufficient evidence at high Realism, ORACUL should be able to say:

> ORACUL found insufficient current evidence to construct this scenario
> at Realism 10.

Offer **LOWER REALISM** instead of inventing evidence.

If credible sources disagree, preserve uncertainty and pass that
uncertainty into the Evidence Pack. ChatGPT must not resolve it using
external knowledge.

## 41. PROMPT-INJECTION PROTECTION

Retrieved web/news content is untrusted input.

Instructions embedded in articles must remain source content and must
never modify ORACUL system instructions.

Strictly separate system instructions from untrusted source content
throughout the pipeline.

## 42. RESPONSIVE & VISUAL DESIGN

Desktop is primary. On mobile, Scenario Panel becomes a
drawer/collapsible panel while the generated story remains primary.

ORACUL should feel like:

**future intelligence + scenario analysis + modern AI product**

It should not feel like a horoscope, fortune teller, fantasy game, or
generic ChatGPT wrapper.

Desired characteristics: - modern; - elegant; - slightly mysterious; -
analytical; - immersive; - restrained; - information-rich without
clutter.

## 43. MVP FUNCTIONAL SCOPE

MVP must include:

1.  ORACUL main page.
2.  Scenario Control Panel.
3.  Realism.
4.  Darkness.
5.  Optimism.
6.  Time Horizon.
7.  Wildcards.
8.  Custom Wildcard.
9.  Continue with ChatGPT.
10. ChatGPT connection status.
11. Secure runtime credential handling.
12. Generate the Future.
13. Research Profile generation.
14. Search Plan generation.
15. Query generation.
16. Current-news search.
17. Source retrieval.
18. Event normalization.
19. Deduplication.
20. Semantic classification.
21. Scenario-aware ranking.
22. Counter-signal selection.
23. Evidence Pack generation.
24. Dynamic prompt generation.
25. Closed Evidence Mode.
26. Structured ChatGPT output.
27. Evidence Guard.
28. Critic validation.
29. Future Story generation.
30. Sources view.
31. Why Could This Happen view.
32. Why These News view.
33. Alternative Future.
34. Quick regeneration controls.
35. Proper failure handling.

## 44. OUT OF SCOPE FOR INITIAL MVP

Do not initially build: - Claude; - Gemini; - complex provider
switching; - native mobile application; - social network; - comments; -
enterprise administration; - prediction markets; - complex calibrated
probabilities; - microservices; - historical backtesting; - complex user
analytics.

Prefer a modular monolith.

## 45. FUTURE FEATURES

### ORACUL Replay

Allow a historical cutoff date and generate using only information
available before that date. Strictly prevent future-data leakage.
Generated scenarios may later be compared with actual history.

### Future Tree

Visualize branching future scenarios.

### Presets

Examples: Realist, Dark Future, Techno Optimist, Chaos.

These are not MVP requirements.

## 46. MVP ACCEPTANCE TEST

Given:

``` text
Realism      8
Darkness     9
Optimism     2
Horizon      5 years
Viruses      8
Robotics     6
Story        ON
Illustration OFF
```

When **GENERATE THE FUTURE** is clicked, ORACUL must:

1.  read configuration;
2.  create Research Profile;
3.  generate relevant search intents;
4.  generate search queries;
5.  search current information;
6.  retrieve candidate articles;
7.  evaluate source quality;
8.  normalize articles into events;
9.  deduplicate events;
10. classify events semantically;
11. score risk/opportunity/impact;
12. rank events against scenario settings;
13. retain diverse evidence;
14. retain relevant counter-signals;
15. create Evidence Pack;
16. assign Evidence IDs;
17. construct dynamic ChatGPT prompt;
18. invoke ChatGPT without search capability;
19. prevent ChatGPT from adding current facts;
20. request structured scenario output;
21. validate factual references against Evidence IDs;
22. reject unsupported current-world claims;
23. perform critic validation;
24. produce final future story;
25. mark it as AI-generated future scenario;
26. display active settings;
27. display real sources;
28. provide user-facing causal explanation;
29. explain why those sources/topics were selected;
30. allow immediate alternative generation.

## 47. CRITICAL INVARIANTS

1.  **ORACUL searches. ChatGPT reasons.**
2.  ChatGPT never independently introduces current-world news.
3.  Every current factual claim used by ChatGPT must originate from
    Evidence Pack.
4.  Low Realism permits fictional future developments, not fictional
    current evidence.
5.  Scenario settings affect research and ranking before ChatGPT
    receives evidence.
6.  Darkness/Optimism influence selection, not factual truth.
7.  Counter-evidence must not be silently removed because it conflicts
    with user settings.
8.  Source reliability remains independent from scenario preference.
9.  Generated future content must always be distinguishable from current
    real news.
10. ORACUL never stores or logs ChatGPT passwords, API keys,
    authorization secrets, or equivalent credentials.

## 48. DEVELOPMENT APPROACH

Before implementing large amounts of code, the development agent must:

1.  analyze this specification;
2.  identify unresolved technical risks;
3.  verify the currently supported official OpenAI/ChatGPT
    authentication mechanism;
4.  confirm which ChatGPT subscription-backed capabilities are
    available;
5.  design architecture;
6.  define data contracts;
7.  define database schema;
8.  define `ResearchProfile`;
9.  define `Event`;
10. define `EvidencePack`;
11. define `ScenarioConfiguration`;
12. define `StructuredScenario`;
13. define `GenerationRun`;
14. define ranking strategy;
15. define credential lifecycle;
16. define prompt contracts;
17. define Evidence Guard;
18. define UI component hierarchy;
19. present an implementation plan.

Do not invent unsupported OpenAI authentication mechanisms. If an
official limitation conflicts with this specification, identify the
conflict before implementing a workaround. Never use unofficial
credential extraction.

## 49. IMPLEMENTATION PRIORITY

Build a working vertical slice as early as possible:

``` text
USER SETTINGS
      ↓
SEARCH PLAN
      ↓
REAL SEARCH
      ↓
REAL SOURCES
      ↓
NORMALIZED EVENTS
      ↓
RANKING
      ↓
EVIDENCE PACK
      ↓
ChatGPT
      ↓
VALIDATED SCENARIO
      ↓
ORACUL STORY
```

Do not spend excessive time on secondary UI before this vertical slice
works.

## 50. DEFINITION OF DONE --- FIRST VERTICAL SLICE

A developer can launch ORACUL locally.

A user can: 1. open ORACUL; 2. connect ChatGPT; 3. set Darkness,
Optimism, Realism, Horizon, and Wildcards; 4. press **GENERATE THE
FUTURE**; 5. see ORACUL perform real research; 6. receive a future
scenario; 7. open **WHY?**, **SOURCES**, and **WHY THESE NEWS?**; 8.
change one setting; 9. receive a meaningfully different future.

At the same time: - current facts remain traceable; - unsupported facts
are rejected; - ChatGPT cannot search independently; - generated futures
are clearly identified; - credentials are not persisted or logged.

## 51. FINAL CONCEPTUAL ARCHITECTURE

``` text
                       USER
                         │
                         ▼
              ┌────────────────────┐
              │ SCENARIO CONTROLS  │
              │ Darkness           │
              │ Optimism           │
              │ Realism            │
              │ Horizon            │
              │ Wildcards          │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ RESEARCH PROFILE   │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ SEARCH PLANNER     │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ NEWS / WEB SEARCH  │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ EVENT EXTRACTION   │
              │ + DEDUPLICATION    │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ SEMANTIC           │
              │ CLASSIFICATION     │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ SCENARIO-AWARE     │
              │ RANKING            │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ EVIDENCE PACK      │
              │ E001 E002 E003     │
              │ Counter-signals    │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ PROMPT BUILDER     │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ ChatGPT            │
              │ CLOSED EVIDENCE    │
              │ MODE               │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ STRUCTURED         │
              │ SCENARIO           │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ EVIDENCE GUARD     │
              │ + CRITIC           │
              └─────────┬──────────┘
                        ▼
              ┌────────────────────┐
              │ ORACUL             │
              │ POSSIBLE FUTURE    │
              └────────────────────┘
```

## 52. PRODUCT PHILOSOPHY

ORACUL does not answer:

> "What will happen?"

It explores:

> **"Given what is happening now, and given the assumptions I choose,
> what could happen next?"**

The left Scenario Panel changes: - what ORACUL looks for; - what ORACUL
finds relevant; - what ORACUL ranks highly; - what evidence ChatGPT
receives; - how far ChatGPT may extrapolate; - which possible future is
generated.

> **The user changes the lens.\
> ORACUL examines the present through that lens.\
> ChatGPT reasons only from what ORACUL found.\
> The result is one possible future.**
