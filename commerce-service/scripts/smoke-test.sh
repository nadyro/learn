#!/usr/bin/env bash
# End-to-end smoke test of a running environment: browse, order, pay, ship, deliver.
# Run it after `make run` or `make up-app` to check that everything is wired correctly.
#
# Usage: scripts/smoke-test.sh
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PRODUCT_ID="0192f0a0-0000-7000-8000-000000000004" # "Daypack 22 L" from the local seed data, 79.90 EUR

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }
json_field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

step "Getting tokens from Keycloak"
CUSTOMER_TOKEN=$("$SCRIPTS_DIR/get-token.sh" alice)
ADMIN_TOKEN=$("$SCRIPTS_DIR/get-token.sh" olivia)

step "Browsing the catalog anonymously"
curl --silent --fail "$API_URL/api/v1/products/$PRODUCT_ID" | grep -q '"sku":"PACK-DAY-22"' || fail "product not found"

step "Placing an order as alice"
ORDER=$(curl --silent --fail-with-body -X POST "$API_URL/api/v1/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: smoke-$(date +%s%N)" \
  -d "{\"items\":[{\"productId\":\"$PRODUCT_ID\",\"quantity\":1}],\"shippingAddress\":{\"recipientName\":\"Alice Martin\",\"line1\":\"12 Rue des Alpes\",\"city\":\"Grenoble\",\"postalCode\":\"38000\",\"countryCode\":\"FR\"}}") \
  || fail "order not placed: $ORDER"
ORDER_ID=$(printf '%s' "$ORDER" | json_field id)
echo "Order $(printf '%s' "$ORDER" | json_field orderNumber) ($ORDER_ID) is $(printf '%s' "$ORDER" | json_field status)"

step "Paying through the payment provider webhook"
"$SCRIPTS_DIR/send-payment-webhook.sh" "$ORDER_ID" 79.90 | grep -q '"outcome":"PROCESSED"' || fail "payment not processed"

step "Shipping as olivia (admin)"
curl --silent --fail -X POST "$API_URL/api/v1/admin/orders/$ORDER_ID/shipment" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"carrier":"Colissimo","trackingNumber":"SMOKE-TEST"}' | grep -q '"status":"SHIPPED"' || fail "not shipped"

step "Marking as delivered"
curl --silent --fail -X POST "$API_URL/api/v1/admin/orders/$ORDER_ID/delivery" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | grep -q '"status":"DELIVERED"' || fail "not delivered"

printf '\n\033[1;32mSmoke test passed.\033[0m Order events are visible in Kafka UI: http://localhost:8085\n'
