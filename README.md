# Sewlect

AI-powered outfit discovery and virtual try-on. Master's thesis project — Spring Boot backend, Flutter frontend to follow.

## Overview

Sewlect recommends outfits assembled from a real product catalog, matched to a user's profile and budget, and lets them try any outfit on virtually before deciding whether they'd actually wear it. Recommendations come from three directions: an algorithmic feed that learns a user's taste over time, a free-text prompt that generates outfits on demand, and a stylist chat that asks the questions needed to pin down what the user is looking for, then runs that search for them. Prompt searches are either matched to the user's own profile or, if they opt out, matched to the prompt alone with gender and budget inferred straight from the text (useful for "find something for my sister," not just "find something for me").

## Features

- **Profile & body photos** — measurements, style preferences, budget, two reference photos for virtual try-on. Sex can be set once and never changed.
- **Account** — password change revokes every other session and returns a fresh token pair for the current device.
- **Personalized feed** — infinite-scroll outfit recommendations, ranked by a blend of intrinsic outfit quality and personal fit. The unseen part of the feed can be discarded after a budget or style change, so the next page regenerates against the new profile.
- **AI-prompt generation** — a free-text prompt returns a batch of outfits, not just one; an optional profile-independent mode lets the AI infer who the outfit is for from the prompt itself. When the profile has no sex set, the audience is read from the prompt even in profile mode.
- **Stylist chat** — a fashion stylist that only asks clarifying questions (occasion, who it's for, style, colours, budget), then shows a summary of the outfit it will search for. Once the user confirms, the search runs in the background through the prompt pipeline and its results stay linked to the chat. Guarded by input sanitisation, a [Llama Guard 4](https://deepinfra.com/meta-llama/Llama-Guard-4-12B) safety check on every message and reply, prompt-injection rules, hard output limits, and a strike system that pauses abusive accounts.
- **Outfit editing** — swap any item for another product of the same role, remove an outerwear or accessory item, or add one to an empty slot. Alternatives are ranked by how well they fit the rest of the outfit, and can also be found with a follow-up prompt. Every edit produces a new outfit; the original is never changed.
- **Virtual try-on** — generates an image of the user wearing a given outfit, powered by [FASHN AI](https://fashn.ai). Asynchronous: submit, then poll. Resubmitting the same outfit reuses the in-flight or already-rendered result instead of paying for a second render.
- **Likes, saves & shares** — save outfits for later, like them, share a direct link. Every outfit response carries its like count and the viewer's own like and save state. A shared link opens without an account through a rate-limited public endpoint.
- **Purchase** — whole outfit or individual pieces (planned last, once the frontend exists — see Status).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Maven (wrapper included) |
| Database | PostgreSQL 18 + `pgvector` — Docker locally, [Aiven](https://aiven.io) free tier for hosting; nearest-neighbor retrieval through native queries served by an HNSW index over a half-precision cast of `products.text_embedding`, with iterative index scans so filtered searches keep their recall |
| Migrations | Flyway — `ddl-auto=validate`, so the schema is always migration-driven |
| Shared state | Redis 8 — distributed rate limiting, background-job coordination, and caching of outfit views, the style-tag list and the catalog's occasion list; optional, with in-memory fallbacks |
| Security | Spring Security resource server — HS256 JWTs with `iss`/`aud` validation, DB-backed rotating refresh tokens, per-IP and per-email rate limiting on the auth endpoints and per-IP limiting on the public endpoints, both with pseudonymised (HMAC) keys |
| AI — catalog | Ollama running Qwen3-VL 4B (enrichment) and Qwen3-Embedding-4B (embeddings) locally, one-time batch jobs |
| AI — prompts & stylist | [DeepInfra](https://deepinfra.com) for everything at request time: `openai/gpt-oss-120b` for structured extraction and the stylist chat, `Qwen/Qwen3-Embedding-4B` for query embeddings (the same open-weight model the catalog uses, so no catalog re-embed was needed), and `meta-llama/Llama-Guard-4-12B` for the stylist's content-safety checks |
| AI — try-on | [FASHN AI](https://fashn.ai) — `tryon-v1.6` for tops/bottoms/full-body, `tryon-max` for footwear/accessories |
| Storage | Cloudflare R2 (S3-compatible, zero egress) for user photos and generated try-on images |
| API docs | springdoc OpenAPI — `/swagger-ui.html`, importable into Postman from `/v3/api-docs` |
| Frontend | Flutter (not started — begins once the backend, minus Commerce, is complete) |
| Containers | Multi-stage, multi-architecture (`amd64`/`arm64`) Docker image; Docker Compose for the local stack |
| CI/CD | GitHub Actions — tests on every push to `master` and every pull request; images published to GitHub Container Registry |
| Testing | JUnit 5 + Mockito; integration tests against real Postgres and Redis via Testcontainers; Postman/Swagger for manual testing |
| Dev environment | Windows, IntelliJ IDEA |

## API overview

All endpoints are under `/api/v1` and JWT-authenticated (Bearer token) unless noted. Full request/response shapes: `/swagger-ui.html` once running.

| Area | Endpoint | Notes |
|---|---|---|
| Auth | `POST /auth/register`, `/login`, `/refresh`, `/logout` | Public. Refresh tokens rotate on every use. Rate-limited per IP and per email. |
| Account | `POST /users/me/password` | Revokes every refresh token and returns a new token pair. A wrong current password is 400, not 401. Rate-limited per user. |
| Profile | `GET`/`PUT /users/me/profile`, `PUT /users/me/style-preferences` | `sex` can be set once; a different value afterwards is 400. |
| Body photos | `POST /users/me/photos/upload-url`, `/confirm`, `GET /users/me/photos` | Presigned R2 upload — image bytes never touch the backend. |
| Catalog | `GET /style-tags` | |
| Catalog (admin) | `POST /admin/catalog/enrich-next` | `ADMIN` role only. |
| Feed | `GET /feed`, `POST /feed/reset` | Cursor-paginated. Reset discards the unseen feed, so the next page regenerates; rate-limited per user. |
| Outfits | `GET /outfits/{outfitId}` | Any authenticated user — outfits have no owner. |
| Outfits (public) | `GET /public/outfits/{outfitId}` | No token needed. Items, total price and like count only. Rate-limited per IP. |
| Prompt | `POST /outfits/prompt`, `POST /outfits/{outfitId}/items/{itemId}/refine`, `POST /outfits/{outfitId}/slots/{role}/refine` | Rate-limited per user; prompt generation shares its hourly budget with stylist searches. |
| Outfit editing | `GET /outfits/{outfitId}/items/{itemId}/alternatives`, `POST .../items/{itemId}/swap`, `POST .../items/{itemId}/remove` | Browse vs. commit. Swaps accept any product of the same role; only outerwear and accessories can be removed. |
| Outfit editing | `GET /outfits/{outfitId}/slots/{role}/alternatives`, `POST .../slots/{role}/add` | For an empty `OUTERWEAR` or `ACCESSORY` slot; 409 if the slot is already filled. |
| Social | `POST`/`DELETE /outfits/{outfitId}/like`, `/save` | Idempotent toggles; the response carries the updated engagement. |
| Social | `GET /outfits/saved`, `GET /outfits/interactions/mine?type=` | Paginated list vs. full ID set for client-side state reconciliation. |
| Stylist | `POST /stylist/conversations`, `POST .../{conversationId}/messages`, `POST .../{conversationId}/proposal` | Start a chat, send a message, or ask for the summary now. 422 when a message is blocked by the safety check, 503 when the check is unavailable, 429 for message limits or a paused account. |
| Stylist | `POST /stylist/conversations/{conversationId}/confirm` | Confirms the latest summary (202). The search runs in the background and the chat closes. |
| Stylist | `GET /stylist/conversations`, `GET .../{conversationId}`, `GET .../{conversationId}/results`, `DELETE .../{conversationId}` | List, read, poll results, delete. Ownership-scoped. |
| Try-on | `POST /tryon`, `GET /tryon/{requestId}` | Async — submit returns `PENDING` immediately; poll for the result. Ownership-scoped. Returns 400 for an outfit with no try-on-eligible items. |

Every response carries an `X-Correlation-Id` header, and every error body repeats it alongside the status, path and any per-field validation messages — quote it when reporting a problem, since it matches the prefix on the corresponding log lines.

## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `social`, `tryon`, `stylist`, `commerce`, plus `common` for genuinely cross-cutting code. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project.

Within a feature package, subfolders are typed strictly by what they actually hold:

| Folder | Holds |
|---|---|
| `entity` | `@Entity` classes only |
| `enums` | Plain enums |
| `dto` | Records that actually cross the HTTP boundary — request and response bodies, nothing else |
| `domain` | Everything else structured-but-non-wire: value objects, AI-parsed result shapes, cross-feature query projections |
| `service` | `@Service` classes, plus the interface a `@Service` implements when that interface *is* the service's own contract |
| `support` | `@Component` beans that assist a service without being one — resolvers, hashers, guards, codecs, assemblers, classifiers |
| `repository` | Repository interfaces |
| `controller` | `@RestController` classes |
| `config` | `@Configuration` classes |
| `properties` | `@ConfigurationProperties` records |
| `pipeline` | `CommandLineRunner` and scheduled batch-job classes (established in `catalog`, reused wherever else it recurs) |
| `filter` | Servlet filters |
| `annotation` | Meta-annotations |
| `openapi` | springdoc configuration and the annotations built on it |

A feature only gets the subfolders it actually needs.

`common`'s own submodules (`security`, `storage`, `taxonomy`, `ai`, `logging`, `exception`, `openapi`, `cache`) follow the same typing once they've grown enough to mix layer-types.

What belongs in `common` isn't decided by who currently calls it but by who might *theoretically* need it later. Connection-level infrastructure for a shared external resource (an AI provider's base URL, API key, HTTP client tuning) stays `common` even with one current caller, since any future feature needing that provider would reuse the same wiring rather than redeclare it; what a feature actually *does* with that access stays in the feature. `DeepInfraConfig` and `FashnConfig` (plus their properties) live in `common.ai.config`/`common.ai.properties` on that basis, and so does `common.ai.service.ContentSafetyService`, the Llama Guard client the stylist uses.

Cross-feature reads go through a narrow, feature-owned query facade (e.g. `catalog.service.ProductSearchService`, `identity.service.UserReferenceQueryService`) rather than one feature injecting another's repository directly. Dependencies point one way: features depend on `common`, never the reverse. For example, a feature that wants a Redis cache contributes its own cache definition rather than `common` knowing about it. Where a lower feature needs data owned by a higher one, it defines a port that the higher feature implements: `outfit` declares `OutfitEngagementReader` and `social` implements it, so `outfit` never depends on `social`. `stylist` sits on top: it depends on `outfit`, `identity`, `catalog` and `common`, and nothing depends on it.

## Status

| # | Chapter | Status |
|---|---|---|
| 1 | Project Foundations & Environment Setup | ✅ Done |
| 2 | Relational Data Layer | ✅ Done |
| 3 | Cloudflare R2 Storage | ✅ Done |
| 4 | Authentication | ✅ Done |
| 5 | Profile & Body Photos | ✅ Done |
| 6.1 | Catalog Data Pipeline | ✅ Done |
| 6.2 | Enrichment & Embeddings | ✅ Done |
| 7 | Compatibility Scoring & Candidate Generation | ✅ Done |
| 8 | Infinite Scroll Feed | ✅ Done |
| 9 | Garment Alternatives | ✅ Done |
| 10 | Prompt-to-Outfit & Prompt-to-Garment-Refinement | ✅ Done |
| 11 | FASHN AI Integration & Async Job Pipeline | ✅ Done |
| 12 | Likes, Saves & Shares | ✅ Done |
| 13 | Logging, Security & API Documentation Audit | ✅ Done |
| 14.1 | Containerization & CI | ✅ Done |
| 14.2 | Shared State with Redis | ✅ Done |
| — | Enhancements: retrieval indexing, outfit editing, flow gaps, DeepInfra consolidation, stylist chat | ✅ Done |
| 14.3 | Deployment | ⬜ Next |
| 15+ | Frontend (Flutter) | ⬜ Not started |
| 16 | Orders & Checkout (post-frontend) | ⬜ Not started |

Rate limiting and background-job coordination live in Redis, so they hold across multiple instances and survive restarts. The deployment target is a single ARM VM running the application and Redis under Docker Compose, with Postgres on Aiven.

## Getting started

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Compose), plus API credentials for [Cloudflare R2](https://developers.cloudflare.com/r2/), [DeepInfra](https://deepinfra.com) and [FASHN AI](https://fashn.ai). One DeepInfra key covers the chat, embedding and safety models. A JDK 25 and IntelliJ IDEA are only needed to run or debug the application outside Docker.

1. Clone the repository.
2. Copy `.env.example` to `.env.local` and fill in the empty values. Every variable the application reads is listed there; non-secrets already have working defaults. `JWT_SECRET` and `RATE_LIMIT_HASH_SECRET` must each be at least 32 characters, or the application refuses to start.
3. Start the stack:
   ```
   docker compose --env-file .env.local up -d --build
   ```
   This builds the image and starts Postgres (with `pgvector`), Redis and the application. Flyway creates the schema on first start.
4. Optionally load the product catalog: download `catalog.dump` from the [`catalog-seed-v1` release](https://github.com/iamblue07/Sewlect/releases/tag/catalog-seed-v1) into a `seed/` folder at the project root, then run
   ```
   docker compose --env-file .env.local --profile seed up seed
   ```
   The seed only runs against an empty catalog.
5. Confirm it's up: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`. API documentation is at `http://localhost:8080/swagger-ui.html`. Everything else under `/actuator/**` requires the `ADMIN` role.
6. Stop with `docker compose --env-file .env.local down`. Data is kept in named volumes.

**Alternatively, running from the IDE.** Start only the backing services with `docker compose --env-file .env.local up -d postgres redis` (both are published on `localhost` only), then run `SewlectApplication` from IntelliJ. `.env.local` is picked up automatically. Setting `REDIS_ENABLED=false` runs the application without Redis, using in-memory rate limiting and no caching. Keep `OLLAMA_ENABLED=none` (the `.env.example` value, and what Compose sets for the app container) unless a local Ollama is running for the catalog pipelines.

**Tests.** `./mvnw verify` (or `mvnw.cmd verify` on Windows) runs the full suite. Docker must be running: Postgres and Redis are started automatically by Testcontainers.

**Catalog pipelines.** Enrichment and embedding of the raw dataset are one-time batch jobs that need a local [Ollama](https://ollama.com) with `qwen3-vl:4b` and `qwen3-embedding:4b`. They are not needed to run the application, and are switched off by default. The catalog dump contains both enriched data and embeddings.