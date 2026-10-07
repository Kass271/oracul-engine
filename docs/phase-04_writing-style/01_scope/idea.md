# Idea — phase-04_writing-style

Received: 2026-10-07

> start new phase development :
>
> # Future Narrative Style
>
> The application must allow the user to select the **writing style used to present the generated future scenario**.
>
> This setting affects the presentation and narrative form of the generated future, but it must **not change the underlying forecasting logic, retrieved evidence, wildcard interpretation, or scenario parameters**.
>
> In other words:
>
> `Forecasting Engine → Future Events / Scenario → Selected Writing Style → Final Output`
>
> The same underlying future scenario may therefore be presented differently depending on the selected style.
>
> ## 1. Writing Style Selector
>
> Add a new **Writing Style** selector to the scenario configuration UI.
>
> At minimum, the application should support two primary modes:
>
> - **Official / Briefing**
> - **Narrative**
>
> The default should be **Official / Briefing** to preserve the application's current behavior unless the user explicitly selects another style.
>
> ## 2. Official / Briefing Style
>
> The **Official / Briefing** style should present the future as a structured analytical or intelligence-style report.
>
> The writing should be:
>
> - factual;
> - concise;
> - structured;
> - neutral in tone;
> - focused on events, consequences, actors, dates, and causal relationships;
> - free from unnecessary literary descriptions.
>
> The result should feel similar to an analytical briefing, situation report, intelligence summary, or historical chronicle written shortly after the described events.
>
> Example tone:
>
> > By September 2032, disruptions in global semiconductor supply had affected several major industrial sectors. Governments introduced temporary export controls while manufacturers accelerated regional production programs.
>
> This mode should prioritize clarity and information density over literary expression.
>
> ## 3. Narrative Style
>
> The **Narrative** style should transform the generated future scenario into a more literary and immersive form.
>
> Instead of simply reporting what happened, the output may use:
>
> - atmosphere;
> - scene setting;
> - stronger narrative flow;
> - imagery;
> - tension;
> - emotional framing;
> - literary pacing;
> - more expressive descriptions of consequences.
>
> However, the narrative layer must remain based on the **same forecast and evidence context** produced by the forecasting engine.
>
> The writing style must not independently invent a completely different future simply because a literary mode was selected.
>
> ## 4. Author-Inspired Narrative Styles
>
> When **Narrative** is selected, the user should be able to choose an optional author-inspired style.
>
> Initial presets should contain approximately **5–7 distinct styles**.
>
> Suggested initial set:
>
> ### Edgar Allan Poe
>
> Dark, gothic, psychologically intense, atmospheric, ominous, and focused on dread and decay.
>
> ### Franz Kafka
>
> Bureaucratic absurdity, alienation, impersonal systems, helplessness, surreal institutional logic, and understated anxiety.
>
> ### H. G. Wells
>
> Speculative and observational, with strong emphasis on technological or societal transformation and its consequences.
>
> ### Jules Verne
>
> Exploratory, technological, adventurous, detailed, and driven by curiosity about future inventions and environments.
>
> ### George Orwell
>
> Clear and restrained prose focused on institutions, political power, social control, language, surveillance, and systemic consequences.
>
> ### Arthur C. Clarke
>
> Large-scale scientific and technological perspective, rational tone, sense of discovery, and emphasis on humanity's relationship with transformative technologies.
>
> ### Harry Harrison
>
> Fast-moving science-fiction narrative with irony, social satire, technological absurdity, and energetic descriptions of future societies.
>
> These presets should be implemented as **style instructions/prompt profiles**, not as separate forecasting models.
>
> ## 5. Separation of Forecast and Presentation
>
> Style selection must be separated from scenario generation conceptually.
>
> The forecasting process determines:
>
> `What happens`
>
> The writing-style configuration determines:
>
> `How it is told`
>
> Therefore:
>
> `Search + Evidence + Wildcards + Scenario Parameters`
> → `Forecast`
> → `Narrative Style`
> → `Final Text`
>
> Changing the writing style should not automatically alter:
>
> - wildcard levels;
> - optimism/pessimism;
> - darkness;
> - probability assumptions;
> - retrieved sources;
> - event selection;
> - causal relationships.
>
> For example, the user should be able to generate the same underlying future as:
>
> `Official Briefing`
>
> or
>
> `Kafka-inspired Narrative`
>
> or
>
> `Poe-inspired Narrative`
>
> without requiring a new search or fundamentally different forecast.
>
> ## 6. Prompt Construction
>
> The final generation prompt should contain a dedicated style section.
>
> Conceptually:
>
> `FORECASTING PARAMETERS`
>
> `EVIDENCE / SOURCES`
>
> `GENERATED FUTURE STRUCTURE`
>
> `WRITING STYLE`
>
> Example:
>
> `Writing Mode: Narrative`
>
> `Style Profile: Franz Kafka`
>
> `Style Characteristics: bureaucratic absurdity, institutional alienation, restrained surrealism, impersonal systems, escalating administrative helplessness`
>
> The style instructions should be applied to the **presentation layer**, while forecasting instructions retain priority over stylistic instructions.
>
> ## 7. UI Concept
>
> The UI may expose the configuration approximately as:
>
> `Writing Style`
>
> `○ Official / Briefing`
>
> `○ Narrative`
>
> When Narrative is selected:
>
> `Narrative Style`
>
> `○ Neutral Narrative`
>
> `○ Edgar Allan Poe`
>
> `○ Franz Kafka`
>
> `○ H. G. Wells`
>
> `○ Jules Verne`
>
> `○ George Orwell`
>
> `○ Arthur C. Clarke`
>
> `○ Harry Harrison`
>
> The architecture should allow additional style profiles to be added later without changing the core forecasting pipeline.
>
> ## Core Requirement
>
> **The future itself and the way that future is written must be treated as separate concepts.**
>
> Scenario parameters and retrieved evidence determine **what future is generated**.
>
> The Writing Style setting determines **how that future is communicated to the user**.
