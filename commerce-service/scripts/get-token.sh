#!/usr/bin/env bash
# Prints an access token from the local Keycloak, for calling the API with curl or Swagger UI.
#
# Usage:   scripts/get-token.sh [username] [password]
# Example: curl -H "Authorization: Bearer $(scripts/get-token.sh alice)" localhost:8080/api/v1/customers/me
#
# Local users (password = username): alice and bob are customers, olivia is an admin.
set -euo pipefail

USERNAME="${1:-alice}"
PASSWORD="${2:-$USERNAME}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"

response=$(curl --silent --show-error --fail-with-body \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=commerce-web" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  "${KEYCLOAK_URL}/realms/kestrel/protocol/openid-connect/token") || {
  echo "Could not get a token for '${USERNAME}'. Is Keycloak running (make up)? Response: ${response:-none}" >&2
  exit 1
}

# Extract the access_token field without requiring jq.
token=$(printf '%s' "$response" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
if [[ -z "$token" ]]; then
  echo "Unexpected response from Keycloak: $response" >&2
  exit 1
fi
printf '%s\n' "$token"
