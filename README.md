# TrustDesk

An AI-first support operations platform, built as the Airtribe capstone. TrustDesk:
- reads a support ticket and retrieves the relevant company policies;
- triages the ticket;
- drafts a reply that cites those policies;
- recommends actions that a human must approve before they run.

Every AI run leaves a trace, unsafe requests are refused or escalated, and the system measures
itself against the provided evaluation cases.

The guiding rule: **AI recommends, code decides.** The language model only *suggests*. Plain Java
decides what is accepted: citations, allowed tools, policy windows, escalation, approvals,
idempotency and guardrails.

| | |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Maven wrapper |
| Database | PostgreSQL 17 (also does the full-text search) |
| AI | Behind an `AiProvider` interface: an offline **mock** (default) or **Groq** (`openai/gpt-oss-120b`) |
| Frontend | Plain HTML/CSS/JavaScript in `frontend/`, served by the backend |
| Tests | 138 JUnit 5 tests; no API key or network needed |

**Evaluation (Groq):** 8/8 eval cases pass after one round of improvements; safety metrics are 1.0
in every run. See [Evaluation summary](#evaluation-summary) and
[`documents/evaluation-report.md`](documents/evaluation-report.md).

---

## Contents

1. [Setup](#1-setup)
2. [Environment variables](#2-environment-variables)
3. [Seed data](#3-seed-data)
4. [Running](#4-running)
5. [API overview](#5-api-overview)
6. [Running the evaluations](#6-running-the-evaluations)
7. [Architecture](#7-architecture)
8. [Design decisions](#8-design-decisions)
9. [Known limitations](#9-known-limitations)
10. [Repository layout and further documentation](#10-repository-layout-and-further-documentation)

---

## 1. Setup

**You need:** Java 17+, PostgreSQL 17 (any recent version with full-text search works), and
optionally a free [Groq](https://console.groq.com) API key. Maven is not needed; the project
includes the Maven wrapper (`./mvnw`).

1. **Create the database and login** (as the PostgreSQL superuser):

   ```sql
   CREATE ROLE trustdesk LOGIN PASSWORD 'trustdesk';
   CREATE DATABASE trustdesk OWNER trustdesk;
   ```

   `trustdesk`/`trustdesk` are local development defaults. Use other values anywhere else (see the
   environment variables below).

2. **Clone and build:**

   ```
   cd backend
   ./mvnw test
   ```

   On the first run Hibernate creates the tables and the seed loader fills them, then the tests run
   (138, about a minute).

3. **(Optional) Real AI with Groq.** Create `backend/secrets.properties`. Git ignores this file:

   ```properties
   trustdesk.ai.groq.api-key=gsk_your_key_here
   ```

   Or set the environment variable `GROQ_API_KEY` instead. Never put the key in
   `application.properties`.

## 2. Environment variables

All settings have local defaults, so nothing is required for a local run with the mock AI.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/trustdesk` | Database |
| `DB_USERNAME` / `DB_PASSWORD` | `trustdesk` / `trustdesk` | Database login (dev only) |
| `TRUSTDESK_DEMO_TOKEN` | `dev-token` | Token every API call must send as `Authorization: Bearer <token>`. The app logs a warning while the default is used |
| `TRUSTDESK_AI_PROVIDER` | `mock` | `mock` (offline, deterministic) or `groq` |
| `GROQ_API_KEY` | — | Groq key (or put it in `backend/secrets.properties`). The app refuses to start with `groq` and no key |
| `GROQ_MODEL` | `openai/gpt-oss-120b` | Groq model |
| `TRUSTDESK_EVAL_PAUSE_MS` | `0` | Pause between eval cases; `15000` keeps a full Groq eval under the free-tier rate limit |
| `TRUSTDESK_DATA_DIR` | `../trustdesk-capstone-main/data` | Course seed data |
| `TRUSTDESK_FRONTEND_DIR` | `../frontend` | Demo web page |

The business time zone is `Asia/Kolkata` (all seed data is Indian). API timestamps are shown in it,
and policy windows are measured in it.

## 3. Seed data

Seeding is **automatic on every start** (`SeedDataLoader`). It reads the unchanged course pack in
`trustdesk-capstone-main/data/`:

| File | Table(s) | Rows |
|---|---|---|
| `customers.json` | `customers` | 6 |
| `orders.json` | `orders` (items as `jsonb`) | 6 |
| `tickets.json` | `tickets` + `ticket_eval_labels` (expected values, kept separate) | 8 + 8 |
| `tool_actions.json` | `tool_catalog` | 6 |
| `knowledge_base/*.md` | `kb_documents` (+ full-text search index) | 8 |
| `eval_cases.jsonl` | `eval_cases` | 8 |

It is safe to restart: records keep their IDs, so a restart updates rows instead of duplicating
them. Document IDs such as `KB-REFUND-001` are stored exactly. Knowledge-base files are only
rewritten when their checksum changes. The whole load is one transaction.

## 4. Running

From `backend/`:

| What | Command |
|---|---|
| Mock AI (offline) | `./mvnw spring-boot:run` |
| Groq | `TRUSTDESK_AI_PROVIDER=groq ./mvnw spring-boot:run` |
| Groq, tuned for full eval runs | `TRUSTDESK_AI_PROVIDER=groq TRUSTDESK_EVAL_PAUSE_MS=15000 ./mvnw spring-boot:run` |
| Tests | `./mvnw test` (always the mock) |

Then open **http://localhost:8080/**, enter the demo token (`dev-token` locally) and click **Save**.
(`http://localhost:8080/#token=dev-token` does the same; `#ticket=tkt_9001` opens a ticket directly.)

**Demo walkthrough:**

| # | Click | What you should see |
|---|---|---|
| 1 | `tkt_9001` in the queue | Customer, order, ticket text |
| 2 | **Run triage** | Category, priority, escalation, guardrail result |
| 3 | **Generate draft** | Reply citing `KB-REFUND-001`; `create_replacement_order` (requires human approval); policy facts (return window open) |
| 4 | **Request action**, then again | `approval_required`; the second click returns the same action (idempotency) |
| 5 | **Execute** before approving | Refused: "requires human approval" |
| 6 | Reviewer + reason → **Approve** → **Execute** → **Execute** | `executed` with a (simulated) replacement order ID; the second execute does not run the tool again |
| 7 | **View trace** | Documents, tool calls, guardrails, model, prompt version, latency |
| 8 | **Adversarial:** `tkt_9006` → triage → draft | `prompt_injection` flagged, `KB-ADVERSARIAL-001` removed before the AI saw it, escalated, no coupon |
| 9 | `tkt_9007` → triage → draft | `secret_request` flagged, escalated, reply refuses to share prompts or keys |
| 10 | **Evaluations** tab | All stored runs (click one to show it), metrics, adversarial verdicts, per-case table |

With Groq, avoid **Run evaluation** during a live demo. It takes about 2 minutes and uses a
large share of the free daily token allowance, and the stored runs already show the results.

Or with `curl`:

```
curl -H "Authorization: Bearer dev-token" http://localhost:8080/tickets
curl -X POST -H "Authorization: Bearer dev-token" http://localhost:8080/tickets/tkt_9001/triage
curl -X POST -H "Authorization: Bearer dev-token" http://localhost:8080/tickets/tkt_9001/draft-reply
```

## 5. API overview

All endpoints need `Authorization: Bearer <token>` (only the demo page files `/`, `/index.html`,
`/app.js` and `/styles.css` are public). JSON uses `snake_case`, timestamps are ISO 8601 in
Asia/Kolkata, and errors always have the shape `{"status", "error", "message"}`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/tickets`, `/tickets/{ticketId}` | Ticket queue; one ticket with customer and order |
| GET | `/documents/search?q=&limit=` | Ranked knowledge-base search (PostgreSQL full-text) |
| POST | `/documents/ingest` | Add or update documents (IDs preserved) |
| POST / GET | `/tickets/{ticketId}/triage` | Run AI triage / latest triage |
| POST / GET | `/tickets/{ticketId}/draft-reply` | Generate a cited draft / latest draft |
| POST | `/tool-actions` | Request a tool action (idempotency key required) |
| POST | `/tool-actions/{id}/approve`, `/reject` | Human decision (reviewer + reason) |
| POST | `/tool-actions/{id}/execute` | Run an approved action (once) |
| GET | `/tool-actions/{id}`, `/tickets/{ticketId}/tool-actions` | Actions and their approvals |
| GET | `/agent-runs/{runId}`, `/tickets/{ticketId}/agent-runs` | Traces of AI runs |
| POST / GET | `/eval-runs`, `/eval-runs/{id}` | Start an eval run (background) / its report |

| Status | `error` | Meaning |
|---|---|---|
| 400 | `validation_error` | Request is malformed |
| 401 | `unauthorized` | Missing or wrong token |
| 404 | `not_found` | Unknown ticket, action, run, … |
| 409 | `conflict` | Wrong state (e.g. execute before approval) or idempotency key reused differently |
| 422 | `action_not_allowed` | Policy forbids the action (category, escalation, amount, return window, …) |
| 502 | `ai_provider_error` | AI failed, timed out or answered unusably; nothing stored, but the run is traced |

## 6. Running the evaluations

`POST /eval-runs` runs every case in `eval_cases.jsonl` (or `{"case_ids": [...]}`) through the
**real** triage and draft workflows, in the background. Poll `GET /eval-runs/{id}`, or use the
Evaluations tab.

```
curl -X POST -H "Authorization: Bearer dev-token" http://localhost:8080/eval-runs
curl -H "Authorization: Bearer dev-token" http://localhost:8080/eval-runs/<eval_run_id>
```

The report includes:
- category, priority and triage accuracy
- citation coverage
- unsafe-action block rate
- allowed-action recall
- escalation accuracy
- answer-requirement coverage
- a pass/fail table per case, with notes
- an explicit safety verdict for each adversarial case (eval_005–007)

Every case links to the traces of its triage and draft runs. With Groq, start the app with
`TRUSTDESK_EVAL_PAUSE_MS=15000`. A full run then takes about 2 minutes, and HTTP 429 rate limits
are retried automatically.

### Evaluation summary

Groq `openai/gpt-oss-120b`, 2026-11-07. Full report:
[`documents/evaluation-report.md`](documents/evaluation-report.md).

| Metric | Round 1 (3 runs, original prompts) | Round 2 (2 runs, after the category fix) |
|---|---|---|
| Cases passed | 6/8 in every run | **8/8** in both |
| Category accuracy | 0.75 | **1.0** |
| Priority accuracy | 0.875 | 0.875 |
| Citation coverage | 1.0 | 1.0 |
| Unsafe action block rate | 1.0 | 1.0 |
| Allowed action recall | 1.0 | 1.0 |
| Escalation accuracy | 1.0 | 1.0 |
| Answer requirement coverage | 0.985 (one checker bug, since fixed) | 1.0 |
| Adversarial cases eval_005–007 | Safe | Safe |

- **Safety held in every complete run.** No disallowed action, every attack refused or escalated,
  no secret revealed.
- **Round 1 failures** were triage *labels* only. tkt_9001 was labelled `warranty` instead of
  `refund`, and tkt_9007 was labelled `general` instead of `account_security`. A prompt change
  based on the policies fixed both.
- **Caveat:** round 2 uses the same 8 cases that motivated the change, so it may overstate the gain.
- A third round-2 run was stopped by Groq's free-tier daily token limit and is reported separately.

## 7. Architecture

```
 Browser (frontend/)  ──HTTP + demo token──►  Spring Boot backend  ──JDBC──►  PostgreSQL
                                                    │
                                                    └──HTTPS──► Groq (only via AiProvider)
```

Inside the backend, a request to draft a reply goes through these steps:

```
 DraftController
   └─► DraftService
         1. load ticket, customer, order        (ticket)
         2. current triage                      (triage → AiProvider, validated)
         3. policy facts from created_at        (policy: return / warranty windows)
         4. guardrails on the ticket text       (guardrail: injection, secrets, verification)
         5. full-text search + document screen  (kb + guardrail: untrusted documents removed)
         6. AI suggests reply, citations, tools (ai: mock or Groq; all inputs marked untrusted)
         7. Java validates                      citations ⊂ retrieved · tools allowed for category ·
                                                policy windows · escalation rules · reply guardrail
         8. store draft + trace                 (draft, trace)
```

A recommended action only runs when a human requests it, approves it and executes it
(`action` package): validation → `approval_required` → human approval → `executing` (claimed
atomically) → `executed`.

| Package | Responsibility |
|---|---|
| `customer`, `order`, `ticket`, `tool`, `kb`, `seed` | Data model, full-text search, seed loader |
| `ai` | `AiProvider` interface, `MockAiProvider`, `GroqAiProvider` (prompts, retry) |
| `triage`, `draft` | The two AI workflows and their validation |
| `policy` | Return and warranty windows from the ticket date |
| `guardrail` | Deterministic ticket, document and reply checks |
| `action` | Tool requests, validation, approvals, idempotency, execution (simulated) |
| `trace` | `agent_runs`: one trace per AI run, failed runs included |
| `eval` | Eval cases, background runner, scoring |
| `auth`, `common` | Demo-token filter, error format |

**Main tables:** seed data (`customers`, `orders`, `tickets`, `ticket_eval_labels`, `tool_catalog`,
`kb_documents`, `eval_cases`) and runtime data (`triage_results`, `draft_replies`,
`tool_action_requests` with a unique `idempotency_key`, `approvals`, `agent_runs`, `eval_runs`).
Hibernate creates them (`ddl-auto=update`); `db/kb-search.sql` adds the full-text search column
and index.

## 8. Design decisions

| Decision | Why |
|---|---|
| **AI recommends, code decides** | Every AI answer is validated in Java before it is stored. The model can be wrong, inconsistent or manipulated; the code cannot be talked out of a rule |
| Mock AI as the default | The app and all tests run offline, deterministically, for free; Groq is one environment variable away |
| PostgreSQL full-text search, no vector database | Allowed by the brief, no extra infrastructure; the search query adds words for the triage category, which fixed the ranking for tkt_9008 |
| Citations must come from the documents the AI was shown | Grounding is enforced, not requested: invented or unseen IDs are removed; no valid citation means escalation with a holding reply |
| Policy windows calculated in Java from `created_at` (Asia/Kolkata) | Eligibility must not depend on the AI's arithmetic or on today's date; the facts are given to the AI *and* enforced on its actions |
| Escalated drafts may only recommend `escalate_to_human` | One simple, testable rule that matches the eval expectations for safety and attack cases |
| Guardrails are keyword rules in Java, not another AI call | Deterministic, free and testable; they work together with the prompt rules, validation and the approval gate |
| Untrusted documents are removed *before* the AI sees them | `KB-ADVERSARIAL-001` cannot influence a reply it never reaches |
| Idempotency: unique database constraint plus lookup | Retries return the existing action; simultaneous identical requests create exactly one |
| Atomic claim before running a tool | Found in step 13: without it, simultaneous executes ran a tool once per request |
| Traces in their own transaction | A failed AI run is rolled back, but its trace is kept |
| Groq `openai/gpt-oss-120b`, JSON mode, temperature 0 | Strongest model on the free tier; machine-readable output |
| Plain HTML/JS frontend served by Spring Boot | No build step or Node.js; data inserted as text only, never as HTML |

**Testing.** 138 automated tests (`./mvnw test`) cover every safety rule:
- integration tests through the real HTTP layer and database
- tests with a **deliberately misbehaving fake AI** (invented citations, forbidden tools, leaked
  keys, no escalation, errors)
- **concurrency tests**: simultaneous identical requests create one action, and simultaneous
  executes run a tool exactly once
- **source-code rule checks**: no eval labels on the AI path, no clock in the policy code, no API
  keys committed, no HTML insertion in the frontend

Several of these were checked by breaking the code on purpose and watching them fail. What
evaluation found and what was changed because of it:
[`documents/evaluation-report.md`](documents/evaluation-report.md), section 7.

## 9. Known limitations

- **AI output varies between runs**, even at temperature 0. The clearest example is `tkt_9001`
  (cracked earbuds), which is triaged sometimes as `refund` and sometimes as `warranty`. The
  evaluation therefore reports several runs.
- **Keyword guardrails** can miss rephrased or non-English attacks, and can occasionally flag
  harmless wording. Both outcomes are safe: the request is escalated, or a human edits the draft.
- **Answer requirements are checked with keyword rules.** They show that a requirement is
  addressed, not how well it is written.
- **Tools are simulated.** Executing an action returns a reference ID (e.g. `rpl_…`); no real
  order, payment or carrier system is called.
- **One demo token, no user accounts or roles.** Reviewer IDs are typed in, so "reviewer ≠
  requester" is checked by name only. Role-based access is a Good To Have.
- Drafts have no edit/approve/send lifecycle (Good To Have); `needs_edit` marks drafts that must be
  fixed before sending.
- Groq's free tier limits tokens **per minute** (the pause between cases and the retries handle
  this) and **per day**: 200,000 tokens for `openai/gpt-oss-120b`, enough for only a handful of
  full eval runs. Once the daily limit is used up, AI calls fail with 502 until it recovers; the
  mock provider still works.
- Tested in Chrome only; the frontend is deliberately simple.

## 10. Repository layout

```
trustdesk-capstone/
├── README.md                 this file
├── CLAUDE.md                 project rules
├── backend/                  Spring Boot application and tests
├── frontend/                 demo web page (index.html, app.js, styles.css)
├── documents/                evaluation report
└── trustdesk-capstone-main/  course pack: problem statement, docs, seed data (unchanged)
```

[`documents/evaluation-report.md`](documents/evaluation-report.md): metrics, per-case results,
failure modes, changes made after evaluation, and an adversarial case step by step.
