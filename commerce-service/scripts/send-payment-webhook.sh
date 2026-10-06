#!/usr/bin/env bash
# Simulates our payment provider calling the payment webhook, with a valid HMAC signature.
#
# Usage:   scripts/send-payment-webhook.sh <order-id> <amount> [currency] [event-type]
# Example: scripts/send-payment-webhook.sh 01a1...808a 448.80
#
# The signature is: hex(HMAC-SHA256(secret, "<unix-timestamp>.<raw-body>")), sent as
#   Payment-Signature: t=<unix-timestamp>,v1=<signature>
set -euo pipefail

if [[ $# -lt 2 ]]; then
  sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi

ORDER_ID="$1"
AMOUNT="$2"
CURRENCY="${3:-EUR}"
EVENT_TYPE="${4:-payment.succeeded}"
SECRET="${PAYMENT_WEBHOOK_SECRET:-local-dev-webhook-signing-secret-not-for-production}"
API_URL="${API_URL:-http://localhost:8080}"

EVENT_ID="evt_$(date +%s%N)"
PAYLOAD=$(printf '{"id":"%s","type":"%s","createdAt":"%s","data":{"orderId":"%s","paymentReference":"pay_%s","amount":%s,"currency":"%s"}}' \
  "$EVENT_ID" "$EVENT_TYPE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$ORDER_ID" "$EVENT_ID" "$AMOUNT" "$CURRENCY")
TIMESTAMP=$(date +%s)
SIGNATURE=$(printf '%s' "${TIMESTAMP}.${PAYLOAD}" | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.* //')

echo "POST ${API_URL}/api/v1/webhooks/payments  (event ${EVENT_ID})" >&2
curl --silent --show-error \
  -H "Content-Type: application/json" \
  -H "Payment-Signature: t=${TIMESTAMP},v1=${SIGNATURE}" \
  --data "$PAYLOAD" \
  "${API_URL}/api/v1/webhooks/payments"
echo
