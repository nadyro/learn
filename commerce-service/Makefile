# Day-to-day commands for commerce-service. Run `make` (or `make help`) to list them.

.DEFAULT_GOAL := help
.PHONY: help up up-observability up-app down reset ps logs run test verify format build image token db smoke-test

COMPOSE := docker compose
ALL_PROFILES := --profile app --profile observability

help: ## List available commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

# --- Local environment ------------------------------------------------------------------------------------------

up: ## Start Postgres, Keycloak, Kafka and Kafka UI, and wait until they are healthy
	$(COMPOSE) up --detach --wait postgres keycloak kafka kafka-ui

up-observability: up ## Also start Prometheus, Grafana and Jaeger
	$(COMPOSE) --profile observability up --detach --wait prometheus grafana jaeger

up-app: ## Build the Docker image and run the whole stack in containers (like a deployed environment)
	$(COMPOSE) --profile app up --detach --build --wait

down: ## Stop all containers (data is kept)
	$(COMPOSE) $(ALL_PROFILES) down

reset: ## Stop all containers and DELETE local data (database included)
	$(COMPOSE) $(ALL_PROFILES) down --volumes

ps: ## Show running containers and their health
	$(COMPOSE) $(ALL_PROFILES) ps

logs: ## Follow the logs of all containers
	$(COMPOSE) $(ALL_PROFILES) logs --follow

db: ## Open a psql shell on the local database
	$(COMPOSE) exec postgres psql -U commerce -d commerce

# --- Development ------------------------------------------------------------------------------------------------

run: ## Run the service from source against the local environment (needs `make up`)
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local

test: ## Run the unit tests (fast, no Docker needed)
	./mvnw test

verify: ## Run the full build exactly like CI: formatting, unit + integration tests, coverage gate
	./mvnw verify

format: ## Format the code (run before committing)
	./mvnw spotless:apply

build: ## Build the jar without running tests
	./mvnw package -DskipTests

image: ## Build the Docker image
	docker build --tag kestrel/commerce-service:local .

# --- Helpers ----------------------------------------------------------------------------------------------------

user ?= alice
token: ## Print an access token: make token user=olivia (alice/bob: customers, olivia: admin)
	@./scripts/get-token.sh $(user)

smoke-test: ## Place, pay, ship and deliver an order against the running service
	./scripts/smoke-test.sh
