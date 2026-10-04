# Manual test plan — phase-01_mvp

Start the app: see how-to-run.md. One section per FR of this phase.

## FR-1 — Main page welcome state
| # | Step | Expected |
|---|---|---|
| 1 | Open http://localhost:4200 | Header ORACUL, Scenario Panel left, center 'What happens next?' and GENERATE THE FUTURE |
| 2 | Stop backend and reload | Unavailable error state, no panel |

## FR-2 — Intensity controls: Darkness, Optimism, Realism
| # | Step | Expected |
|---|---|---|
| 1 | Move Darkness, Optimism, Realism sliders 1-10 | Value labels update; values persist after reload |

## FR-3 — Time Horizon
| # | Step | Expected |
|---|---|---|
| 1 | Check default Time Horizon | 1 year selected |
| 2 | Select 5 years | 5 years selected |

## FR-4 — Wildcard catalogue
| # | Step | Expected |
|---|---|---|
| 1 | Open wildcard catalogue | All wildcards off, grouped by category |
| 2 | Enable New pandemic, set intensity 8 | Wildcard on with value 8 |

## FR-5 — Custom wildcard
| # | Step | Expected |
|---|---|---|
| 1 | Add a custom wildcard | Appears in list |
| 2 | Add wildcards past the limit | Limit message shown, no extra added |

## FR-6 — Output settings
| # | Step | Expected |
|---|---|---|
| 1 | Change output settings | Selection kept and sent with the run |

## FR-7 — Continue with ChatGPT (sign-in)
| # | Step | Expected |
|---|---|---|
| 1 | Click Continue with ChatGPT, complete sign-in on the stub | Header shows connected |
| 2 | Return without completing sign-in | Not-completed message shown |

## FR-8 — ChatGPT connection status and sign-out
| # | Step | Expected |
|---|---|---|
| 1 | Observe header status in each state | Connected / not connected / plan not eligible shown correctly |
| 2 | Sign out | Disconnected state |

## FR-9 — Runtime-only credential handling
| # | Step | Expected |
|---|---|---|
| 1 | Inspect browser storage and network after sign-in | No tokens or credentials in browser |

## FR-10 — Generate the Future (start a run)
| # | Step | Expected |
|---|---|---|
| 1 | Not connected: view GENERATE THE FUTURE | Button disabled |
| 2 | Connect and click it | Run starts, progress view opens |

## FR-11 — Research Profile
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the research profile behaviour | Behaviour matches the spec research-pipeline.md |

## FR-12 — Search plan and query generation
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the search plan and query generation behaviour | Behaviour matches the spec research-pipeline.md |

## FR-13 — Current-news search and source retrieval
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the current-news search and source retrieval behaviour | Behaviour matches the spec research-pipeline.md |

## FR-14 — Event normalisation and deduplication
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the event normalisation and deduplication behaviour | Behaviour matches the spec research-pipeline.md |

## FR-15 — Semantic classification (event enrichment)
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the semantic classification (event enrichment) behaviour | Behaviour matches the spec research-pipeline.md |

## FR-16 — Scenario-aware ranking
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the scenario-aware ranking behaviour | Behaviour matches the spec research-pipeline.md |

## FR-17 — Evidence selection with diversity and counter-signals
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the evidence selection with diversity and counter-signals behaviour | Behaviour matches the spec research-pipeline.md |

## FR-18 — Evidence Pack
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the evidence pack behaviour | Behaviour matches the spec research-pipeline.md |

## FR-19 — Dynamic prompt with Closed Evidence Mode and injection protection
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the dynamic prompt with closed evidence mode and injection protection behaviour | Behaviour matches the spec scenario-reasoning.md |

## FR-20 — Structured scenario output
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the structured scenario output behaviour | Behaviour matches the spec scenario-reasoning.md |

## FR-21 — Evidence Guard
| # | Step | Expected |
|---|---|---|
| 1 | Run the phase automated tests listed in traceability.md for this FR | All pass |
| 2 | Start a run with the stubs and observe the evidence guard behaviour | Behaviour matches the spec scenario-reasoning.md |

## FR-22 — Critic validation
| # | Step | Expected |
|---|---|---|
| 1 | Generate a future and open metadata | Critic status passed shown |
| 2 | Use a scenario with open issues | Open critic issues listed |

## FR-23 — Final future story with AI labels
| # | Step | Expected |
|---|---|---|
| 1 | Complete a run | Future story shown with AI labels |

## FR-24 — Generation progress
| # | Step | Expected |
|---|---|---|
| 1 | Start a run | Progress shows searching stage and advances to finished |
| 2 | Open a non-existent run id | Run not found message |

## FR-25 — Scenario metadata panel
| # | Step | Expected |
|---|---|---|
| 1 | Open a finished result | Scenario metadata panel shows configuration and counts |

## FR-26 — WHY COULD THIS HAPPEN?
| # | Step | Expected |
|---|---|---|
| 1 | Open WHY COULD THIS HAPPEN? | Causal chain shown; selecting an item highlights its source |

## FR-27 — SOURCES view
| # | Step | Expected |
|---|---|---|
| 1 | Open SOURCES | Sources list with metadata |

## FR-28 — WHY THESE NEWS? and research summary
| # | Step | Expected |
|---|---|---|
| 1 | Open WHY THESE NEWS? | Selection rationale and research summary shown |

## FR-29 — Quick regeneration controls
| # | Step | Expected |
|---|---|---|
| 1 | Click Darker on a result | New run starts with higher Darkness |
| 2 | Set Darkness to 10 and view result | Darker disabled |
| 3 | Open Recent futures | Previous result listed |

## FR-30 — Alternative future
| # | Step | Expected |
|---|---|---|
| 1 | Click Alternative future | Distinct alternative result shown |
| 2 | Trigger non-distinct case (stub) | Not-distinct message shown |

## FR-31 — Insufficient evidence
| # | Step | Expected |
|---|---|---|
| 1 | Run with Realism 1 on thin evidence (stub) | Insufficient evidence view with lower-realism action |
| 2 | Click lower realism | New run started |

## FR-32 — Run failure handling
| # | Step | Expected |
|---|---|---|
| 1 | Force a failing run (stub) | Failure view with message |
| 2 | Exceed deadline | Timeout message shown |

## FR-33 — Session state and Recent futures
| # | Step | Expected |
|---|---|---|
| 1 | Complete runs and open Recent futures | List shown, newest first |
| 2 | Open an entry | Result reopened |

## FR-34 — Mobile layout
| # | Step | Expected |
|---|---|---|
| 1 | Resize to 390 px width | Panel hidden, Scenario button in header |
| 2 | Open drawer | Drawer shows panel; settings retained on close |
