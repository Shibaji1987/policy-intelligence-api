# ADR 0001: Start as a modular monolith

- Status: Accepted
- Date: 2026-06-14

## Context

The platform needs meaningful domain boundaries, but the MVP does not need the
operational cost or distributed failure modes of microservices.

## Decision

Use one deployable Spring Boot application with package-enforced modules.
Modules expose application services and immutable contracts. Persistence and
provider adapters remain internal infrastructure.

## Consequences

- Local development and transactional consistency remain straightforward.
- Module boundaries can be tested before deployment boundaries are introduced.
- A module may become a service only when independent scaling, ownership, or
  release evidence justifies the additional operational complexity.
