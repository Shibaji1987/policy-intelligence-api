# Contributing

## Local verification

Run the same quality gate used by CI:

```bash
./gradlew clean check bootJar
```

Start the full Docker stack with:

```bash
sh scripts/start-stack.sh
```

## Change discipline

- Keep module boundaries explicit.
- Add Flyway migrations; never edit an applied migration.
- Add tests at the narrowest useful level.
- Do not place provider calls inside database transactions.
- Record consequential architecture changes in `docs/adr`.
- Avoid secrets and machine-specific configuration in Git.

Commit messages should explain the outcome, not the editing activity.
