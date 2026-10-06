# 0006. Stateless JWT resource server

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

The storefront, the mobile app and the back-office need to authenticate users. Building login, password storage,
multi-factor authentication and account recovery ourselves would be costly and risky.

## Decision

- Authentication is delegated to an OpenID Connect identity provider: Keycloak (run locally from `compose.yaml`).
- The service is an OAuth2 **resource server**: it validates JWT access tokens locally with the provider's public keys
  (signature, issuer, expiry, and audience `commerce-api`), without calling the provider on each request.
- Authorization uses realm roles (`customer`, `admin`) mapped to Spring authorities, plus ownership checks in the
  services. Rules are deny-by-default and tested in `SecurityIT`.
- No sessions or cookies, hence no CSRF protection needed.
- The payment provider cannot obtain tokens: its webhook is authenticated with an HMAC signature instead.

## Consequences

- No credentials stored in our database; the identity provider can be replaced by any OIDC provider (Auth0, Okta,
  Cognito...) by changing configuration only.
- A token stays valid until it expires (15 minutes) even if the user is disabled. Keep lifetimes short.
- Customers are created locally on their first authenticated request ("just-in-time provisioning").
