## What

<!-- What does this change do? Link the ticket, e.g. "Closes #12" or "KST-104". -->

## Why

<!-- The problem it solves or the value it brings. Reviewers should not have to guess. -->

## How to test

<!-- Steps a reviewer can follow locally: requests to send, expected responses... -->

## Checklist

- [ ] Tests added or updated (unit and/or integration)
- [ ] `make verify` passes locally
- [ ] Database migration is backward compatible with the previous version of the service (or not applicable)
- [ ] API changes are backward compatible, or the change is versioned (or not applicable)
- [ ] Events published on Kafka are backward compatible (or not applicable), `docs/events.md` updated
- [ ] Documentation updated (README, runbook, ADR...) if behaviour or operations changed
- [ ] No secrets, credentials or personal data in the code, logs or tests
