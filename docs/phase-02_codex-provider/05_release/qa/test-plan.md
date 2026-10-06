# Manual test plan — phase-02_codex-provider

Start the app: see how-to-run.md (E2E stub mode unless stated). One section per FR of this phase.

## FR-35 — Documented authorize request
| # | Step | Expected |
|---|---|---|
| 1 | Open http://localhost:4200 with a fresh E2E database, click Continue with ChatGPT. | Browser goes to the stub authorize URL with client_id=dynamic_agent_client, agent_name_hint=ORACUL, ext_agent_host_id=urn:uuid:<id>, S256 challenge, state and nonce; after sign-in the app shows Connected. |
| 2 | Disconnect, then sign in again. | Authorize URL now uses the stored oaiapp_ client id, no agent_name_hint, same ext_agent_host_id and fresh state/nonce; Connected again. |

## FR-36 — Callback handling, nonce check and issued client id persistence
| # | Step | Expected |
|---|---|---|
| 1 | Complete sign-in at the stub. | Browser returns to /callback, app shows Connected. |
| 2 | Cancel at the stub (not completed). | App shows a not-completed message and stays not connected. |
| 3 | Let the pending sign-in expire, then return. | Expired message is shown. |
| 4 | Return with a wrong nonce (stub control). | Not-verified message, not connected. |

## FR-37 — Reset ChatGPT connection
| # | Step | Expected |
|---|---|---|
| 1 | While connected, open the reset action. | A confirmation dialog appears. |
| 2 | Confirm reset. | Connection is cleared, app shows not connected; the next sign-in is a first registration. |
| 3 | Start a run and try to reset during it. | Reset is refused with a run-in-progress message. |

## FR-38 — Documented plan-usage Responses call
| # | Step | Expected |
|---|---|---|
| 1 | Connect, start a generation and wait for completion. | Run COMPLETED; the result shows a model chip (Model gpt-5 in stub mode). |

## FR-39 — Plain-language messages for documented OpenAI errors
| # | Step | Expected |
|---|---|---|
| 1 | Set the stub to answer not eligible, start a run. | Plain message that the plan is not eligible. |
| 2 | Set the stub to usage limit / a provider error code. | Plain usage-limit message / provider code shown. |
| 3 | Set the stub to fail once with a retryable error. | Call is retried and the run completes. |
| 4 | Set the stub to answer an expired session. | Session-expired message shown. |

## FR-40 — Refresh failures end the session cleanly
| # | Step | Expected |
|---|---|---|
| 1 | Make token refresh fail as session expired (stub). | App shows session expired and asks to sign in again. |
| 2 | Make refresh fail as registration invalid (stub). | App shows registration-invalid state. |
| 3 | Make refresh endpoint unavailable (stub). | Unavailable message; no crash, no stuck progress. |

## FR-41 — Sign-in conditions shown in the UI
| # | Step | Expected |
|---|---|---|
| 1 | Open the app while not connected. | Below Connect ChatGPT to generate it says: Signing in needs a personal ChatGPT Plus or Pro account. Open ORACUL in a browser on the same computer where ORACUL runs. |
| 2 | Connect. | The conditions text is gone. No password or API-key input exists on any screen. |

## FR-42 — Opt-in E2E stub with its own data
| # | Step | Expected |
|---|---|---|
| 1 | Start the plain stack (no e2e override). | No stub container, sign-in goes to real OpenAI. |
| 2 | Start with the e2e override. | Stub at :4010 is running, sign-in completes at the stub, data is in volume db-e2e-data. |

## FR-43 — Comprehensive README
| # | Step | Expected |
|---|---|---|
| 1 | Read README.md. | It covers prerequisites, run for real, E2E stack, port conflict fix, reset (down -v) and stopping. |

## FR-44 — Real news search within GDELT's limits
| # | Step | Expected |
|---|---|---|
| 1 | Run a generation with the stub in GDELT-fallback mode. | GDELT requests are spaced at least the configured spacing apart, rate limits are handled, and the run completes. |

## FR-45 — Stop a generation and start a new one
| # | Step | Expected |
|---|---|---|
| 1 | Start a run and press STOP in the progress view. | Stopped message appears; run is STOPPED and appears in Recent Futures as stopped. |
| 2 | Start a new run right after. | The new run starts normally. |
| 3 | Reset the connection after a stop. | Reset is allowed. |
| 4 | Make the stop request fail (stub/backend down). | A stop-failed message is shown. |

## FR-46 — At most 30 sources per run
| # | Step | Expected |
|---|---|---|
| 1 | Run an acceptance generation (stub returns many articles). | The Sources panel lists at most 30 sources. |

## FR-47 — Always generate; note insufficient evidence at the end
| # | Step | Expected |
|---|---|---|
| 1 | Start a run with realism 1 or a stub mode giving little evidence. | Run still completes and the story ends with an insufficient-evidence note. |
| 2 | Make the stub return no articles. | Run completes with a no-evidence note. |

## FR-48 — Google News RSS as the main news source, GDELT as fallback
| # | Step | Expected |
|---|---|---|
| 1 | Run a generation in stub mode. | Sources come from Google News RSS (stub); GDELT is used only when Google fails. |
| 2 | In real mode, run a generation. | KNOWN DEFECT: real Google News returns 0 items for OR-group queries, so the run ends with the no-evidence note (see ../real-check.md). |
