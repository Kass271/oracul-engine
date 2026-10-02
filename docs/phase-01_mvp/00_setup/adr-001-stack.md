# ADR-001 — Technology stack

Date: 2026-10-02 · Status: accepted

## Decision
| Area | Choice | Version |
|---|---|---|
| Language | Java | 25 (Gradle toolchain) |
| Backend framework | Spring Boot | 4.1.1 |
| Build | Gradle (Kotlin DSL) | 9.7.1 |
| Database | PostgreSQL + Flyway | postgres:18 |
| Contract | OpenAPI 3 — openapi-generator (Spring interfaces), ng-openapi-gen (Angular client) | 7.25.0 / 1.1.0 |
| Frontend | Angular + Angular Material (Material 3) | 22.2.1 / 22.2.1 |
| Unit tests | JUnit 5 + Testcontainers + JaCoCo · Angular unit-test builder (Vitest) | — |
| E2E | Playwright | 1.63.0 |
| Runtime | Docker Compose (db · backend · frontend/nginx) | — |

## Why
- Contract first: both sides are generated from `api/openapi.yaml`, so backend and frontend cannot drift.
- Versions are the latest stable at scaffold time and pinned in the build files.

## Consequences
- Docker is required for tests (Testcontainers) and for running the app.
