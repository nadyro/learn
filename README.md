# Kestrel Outfitters — Engineering

Welcome to the engineering repository of **Kestrel Outfitters**, an online retailer of outdoor gear (tents,
backpacks, sleeping bags...). You just joined the team: this repository contains the backend services you will work on.

> **About this repository.** Kestrel Outfitters is fictional. This repository is a training ground for backend
> engineering, built to look and behave like a real company's production codebase: real frameworks, real
> infrastructure (run locally with Docker), tests, CI, operational docs and a backlog of tickets to implement.

## Services

| Service                                  | Stack                                      | Owner         | Description                                                    |
|------------------------------------------|--------------------------------------------|---------------|----------------------------------------------------------------|
| [`commerce-service`](commerce-service/)  | Java 21, Spring Boot 4, PostgreSQL, Kafka  | Commerce team | Catalog, inventory, customers and orders. The core of the shop |
| _coming soon_                            | Python                                     | Data team     | Consumes order events from Kafka                               |

## Where to start

1. Read [How we work](CONTRIBUTING.md): branches, commits, pull requests, definition of done.
2. Follow the [onboarding guide](commerce-service/docs/onboarding.md) to run the service on your machine.
3. Pick your first ticket in the [backlog](commerce-service/docs/backlog.md).

## Repository layout

```
.
├── commerce-service/        Java backend (see its README)
│   ├── src/                 Application code and tests
│   ├── docs/                Architecture, ADRs, runbook, backlog...
│   ├── infra/               Local infrastructure config (Keycloak realm, Prometheus, Grafana)
│   ├── deploy/k8s/          Kubernetes manifests (Kustomize)
│   └── compose.yaml         Local environment (Postgres, Keycloak, Kafka, observability)
└── .github/                 CI workflows, Dependabot, PR and issue templates, code owners
```
