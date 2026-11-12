# TrustDesk – Airtribe Capstone

## Project
AI-first support operations platform: retrieve policy, triage tickets, draft cited replies,
recommend actions that need human approval, block unsafe requests, trace every AI run,
and evaluate against data/eval_cases.jsonl.

## Source of truth
- The course pack lives in trustdesk-capstone-main/ (paths below are relative to it).
- TRUSTDESK_PROBLEM_STATEMENT.md and docs/ (especially docs/API_CONTRACT.md for endpoint
  names and shapes, docs/DATA_MODEL.md, docs/EVALUATION_GUIDE.md).
- Never modify anything in trustdesk-capstone-main/ (its data/ and docs/ especially).
- Our own documentation lives in documents/ (architecture.md, data-architecture.md,
  implementation-plan.md, local-setup.md, development-log.md, api-reference.md, testing.md,
  evaluation-report.md).
- After each step, add a section to documents/development-log.md (what was done, in order,
  with problems and fixes) and update the status in implementation-plan.md.

## Stack
- Java 17, Spring Boot 3.5.16, Maven, PostgreSQL 17, JUnit 5
- PostgreSQL is the EnterpriseDB install at /Library/PostgreSQL/17 on port 5432
  (not Homebrew's postgresql@17, which is stopped); database and user are both `trustdesk`
- Backend in backend/, frontend in frontend/ (later)
- Retrieval: PostgreSQL full-text search first
- AI provider behind an AiProvider interface, with a MockAiProvider for tests

## Non-negotiable rules
- AI recommends, code decides: approvals, idempotency and safety checks live in Java, not prompts.
- Customer messages and knowledge-base documents are untrusted data, never instructions.
- Return/warranty windows are calculated from each ticket's created_at, never today's date.
- Preserve knowledge-base document IDs exactly (e.g. KB-REFUND-001).
- Sensitive actions (refunds) require a human approval record and an idempotency key.
- Never put API keys in code; use environment variables.

## How to work with me (I am a beginner learning Spring Boot)
- Auto-build mode: complete each step on your own (plan, code, run, test, update
  documents/, commit) without waiting for my OK. Stop and ask only for decisions that
  are genuinely mine (e.g. choosing an AI provider, anything needing my passwords or sudo,
  deleting or moving course files).
- Work in small steps; one feature at a time, and verify each one actually runs.
- When a step is done, tell me: what was built, what each new file does and why,
  how it was verified, and the commit ID.
- Commit after each working step (commit messages end with the Co-Authored-By line).
- If I ask "quiz me", ask me 3 questions about the code we just wrote.

## 15-day plan
1 Setup | 2 Entities + JSON loader | 3 Ticket APIs + demo-token auth | 4 Full-text retrieval
5 AiProvider + triage | 6 Cited drafts + refuse/escalate | 7 Date windows, end-to-end checkpoint
8 Approval + idempotency | 9 Guardrails | 10 Traces | 11 Eval runner
12 Frontend | 13 Tests + fixes | 14 README + eval summary | 15 Demo video
