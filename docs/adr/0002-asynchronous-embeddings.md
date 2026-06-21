# ADR 0002: Generate embeddings after ingestion commits

- Status: Accepted
- Date: 2026-06-14

## Context

Embedding providers are remote systems and cannot participate atomically in a
PostgreSQL transaction. Holding database locks during provider calls increases
latency and creates ambiguous rollback behavior.

## Decision

Persist chunks with `PENDING` embedding status. After commit, dispatch retryable
embedding work through an application port. The concrete delivery mechanism
will remain replaceable.

## Consequences

- Ingestion is durable even during provider outages.
- Retrieval must exclude chunks without completed embeddings.
- Retry count, latency, failures, and backlog become observable operational
  signals.
