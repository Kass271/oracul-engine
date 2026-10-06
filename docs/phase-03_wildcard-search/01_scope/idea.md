# Idea — phase-03_wildcard-search

Received: 2026-10-06

> start new phase development
>
> # Wildcard-Based Search and Forecast Pipeline
>
> The search and retrieval process used for forecast generation must operate **independently for each selected wildcard**, rather than using a single combined search query across all selected wildcards.
>
> ## 1. Independent Search per Wildcard
>
> For every selected wildcard, the system must generate and execute its own set of search queries.
>
> For example, if the user selects:
>
> - Biology
> - AI
> - Geopolitics
>
> the system should execute three independent search pipelines:
>
> `Biology → Search → Results`
>
> `AI → Search → Results`
>
> `Geopolitics → Search → Results`
>
> Each wildcard must produce its own search results independently of the other selected wildcards.
>
> ## 2. Wildcard Level Must Affect Search Query Semantics
>
> The numeric level assigned to a wildcard must influence how extreme, disruptive, catastrophic, or speculative the corresponding search queries are.
>
> For example:
>
> ### Biology = 1
>
> Search queries should remain close to current reality and ordinary developments:
>
> `current virus research`
>
> `emerging infectious disease research`
>
> `recent studies of virus mutations`
>
> ### Biology = 5
>
> Search queries should move toward more serious risks and potentially disruptive developments:
>
> `dangerous emerging virus mutations`
>
> `research on viruses with pandemic potential`
>
> `new pathogens with high mortality risk`
>
> ### Biology = 10
>
> Search queries may target highly extreme or catastrophic developments:
>
> `deadly virus outbreak threatens millions`
>
> `Ebola-like virus catastrophic outbreak`
>
> `virus outbreak causing mass casualties`
>
> Therefore, the wildcard level must influence not only the final forecasting stage, but also the **semantics and intensity of the search queries themselves**.
>
> ## 3. Other Scenario Parameters Must Also Influence Search
>
> Search queries should be generated based on a combination of:
>
> `Wildcard Type + Wildcard Level + Optimism/Pessimism + Darkness + Other Scenario Parameters`
>
> For example, a high Darkness value should shift searches toward negative consequences, systemic risks, conflicts, failures, disasters, instability, and other adverse developments.
>
> A low Darkness value should shift searches toward neutral or positive technological, scientific, economic, or social developments.
>
> These parameters should influence the search direction rather than being applied only during final scenario generation.
>
> ## 4. Retrieval Pipeline
>
> For each wildcard, the system should execute the following pipeline:
>
> `Wildcard`
> → `Generate Search Queries`
> → `Execute Search`
> → `Select Several Relevant Results`
> → `Fetch Article/Source Content`
> → `Extract Relevant Text`
>
> Several of the most relevant results should be selected independently for each wildcard.
>
> Whenever possible, the system should retrieve the actual source/article content rather than relying only on search-engine titles or snippets.
>
> Relevant text fragments should then be extracted from those sources.
>
> ## 5. Remove GDELT
>
> **GDELT must be completely removed from the forecasting and retrieval architecture.**
>
> The system must no longer use GDELT as:
>
> - a news discovery provider;
> - a search provider;
> - a source of articles or events;
> - a fallback retrieval mechanism;
> - an input into ranking, filtering, or forecasting;
> - a dependency anywhere in the forecast-generation pipeline.
>
> Any existing GDELT-specific integration code, clients, configuration, environment variables, DTOs, adapters, scheduled jobs, parsing logic, fallback logic, and unused dependencies should be removed if they are no longer required by another part of the application.
>
> The new retrieval flow should rely on the wildcard-driven search mechanism described in this specification.
>
> There should be **no hidden fallback to GDELT** if another search mechanism fails.
>
> Failure of a wildcard search should instead be handled explicitly by the retrieval pipeline without silently switching to GDELT.
>
> ## 6. Forecast Context Construction
>
> After retrieval has been completed for all selected wildcards, the collected information should be combined into a structured context.
>
> Example:
>
> `Scenario Parameters`
>
> `Wildcard: Biology`
> - Source 1
> - Source 2
> - Source 3
>
> `Wildcard: AI`
> - Source 1
> - Source 2
> - Source 3
>
> `Wildcard: Geopolitics`
> - Source 1
> - Source 2
> - Source 3
>
> This structured evidence context should then be combined with the forecasting prompt and the complete set of user-selected scenario parameters.
>
> ## 7. Role of ChatGPT / Forecasting Model
>
> The forecasting model must **not simply summarize or rewrite the retrieved news**.
>
> The retrieved sources represent the **starting conditions and evidence base** from which the future scenario should evolve.
>
> The forecasting instruction should follow this principle:
>
> > Use the provided sources as signals describing the current state of the world and as starting conditions for the scenario.
> >
> > Generate the future scenario according to the supplied wildcard levels, optimism/pessimism, darkness, and other scenario parameters.
> >
> > Do not automatically normalize the scenario toward the most realistic, conservative, or statistically likely outcome. If the user has selected extreme parameter values, extrapolate developments according to those values.
> >
> > Real-world sources determine the starting point of the scenario, while the user-selected parameters determine the direction, intensity, and magnitude of future developments.
>
> ## 8. Overall Pipeline
>
> The complete pipeline should therefore be:
>
> `User Parameters`
> → `Wildcard Decomposition`
> → `Independent Query Generation per Wildcard`
> → `Independent Search per Wildcard`
> → `Source Selection`
> → `Source Content Retrieval`
> → `Relevant Content Extraction`
> → `Combined Evidence Context`
> → `Forecast Prompt + User Parameters`
> → `ChatGPT / Forecasting Model`
> → `Future Scenario`
>
> **GDELT is not part of this pipeline.**
>
> ## Core Requirement
>
> **Search determines the real-world starting point from which the forecast begins, while wildcard levels and other scenario parameters determine how far, how strongly, and in which direction the forecasting model extrapolates from that starting point.**
>
> The scenario parameters therefore operate at **two distinct stages**:
>
> 1. **Retrieval stage** — they influence what information and developments are searched for.
> 2. **Forecasting stage** — they influence how the retrieved real-world signals are extrapolated into a future scenario.
>
> This two-stage influence is a core part of the forecasting mechanism and should be preserved in the implementation.
>
> GDELT should be removed rather than adapted to the new architecture.
