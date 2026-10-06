# How we work

These are the team conventions. They exist so that anyone can pick up anyone else's work, and so that what reaches
production is reviewed, tested and reversible. If a rule gets in your way, propose a change to it in a pull request.

## Workflow: trunk-based development

- `main` is always releasable: every commit on `main` is built, tested and deployed to staging automatically.
- Work on a **short-lived branch** created from `main` (merge within a few days, not weeks).
- Name branches after the ticket: `feature/KST-105-expire-unpaid-orders`, `fix/KST-114-late-payment`,
  `chore/upgrade-spring-boot`.
- Unfinished work can be merged if it is not reachable by users yet (behind configuration or not wired to an endpoint).
  Prefer that to a long-lived branch.

## Commits

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(orders): cancel unpaid orders after 30 minutes

The job runs every minute on every replica and uses SKIP LOCKED so that
each expired order is processed exactly once.

Refs: KST-105
```

- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.
- Scope: the module (`orders`, `catalog`, `inventory`, `payments`, `customers`) or area (`ci`, `deps`).
- Subject in the imperative mood ("add", not "added"), no trailing period, under ~70 characters.
- The body explains **why**, the diff already shows what.

## Pull requests

1. Keep them small: under ~400 changed lines is reviewable, 2,000 is not. Split refactorings from behaviour changes.
2. Fill in the [template](.github/pull_request_template.md), including how to test.
3. Run `make verify` before pushing. CI runs the same build and must be green.
4. One approval is required. Code owners are requested automatically.
5. We **squash merge**: the PR title becomes the commit on `main`, so it must follow the commit convention.
6. Delete the branch after merging.

### Reviewing

- Review within one working day: unreviewed PRs block your teammates.
- Comment on the code, never on the person. Explain the why and suggest an alternative.
- Prefix optional remarks with `nit:`. Everything else is expected to be addressed or discussed.
- Check correctness, tests, naming, security (authorization!), backward compatibility and operability (logs,
  metrics, errors). Formatting is not a review topic: the build enforces it.

## Definition of done

A ticket is done when:

- [ ] The acceptance criteria are met and covered by automated tests.
- [ ] `make verify` passes (formatting, unit + integration tests, architecture rules, coverage gate).
- [ ] New endpoints have security rules and are listed in `SecurityIT`.
- [ ] Database migrations are backward compatible (see below).
- [ ] Logs, metrics and errors make the feature operable; the runbook is updated if on-call needs to know about it.
- [ ] Documentation and the OpenAPI annotations are up to date.
- [ ] The PR is reviewed, approved and merged, and the change works on staging.

## Rules that protect production

### Database migrations

- Never modify a migration that has been merged: it has already run somewhere. Add a new one.
- Migrations must work with **both** the old and the new version of the code, because during a rolling deployment
  both run at the same time. Use the expand/contract pattern: add the new column (nullable), deploy code that writes
  both, backfill, deploy code that reads the new one, then drop the old column in a later release.
- Large tables: create indexes `concurrently` and backfill in batches. Ask for a review from someone who did it before.

### APIs and events

- Never break a published contract (removing or renaming a field, changing a type or a meaning). Add fields instead,
  or version the endpoint/event (`/api/v2/...`, `order.placed` v2).
- Error `code` values are part of the API contract too.

### Secrets and personal data

- Never commit secrets, even for "just a test". Local development uses the well-known values in
  `application-local.yml` and `compose.yaml`; everything else comes from the secret manager.
- Never log personal data (emails, addresses, tokens). Log IDs instead.

## Getting help

Ask early. A question that saves you two hours is a good question. Use the team channel rather than private messages
so that others can learn from the answer.
