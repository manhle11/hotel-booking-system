# Repository security review

Review date: 2026-10-10

## Scope

The baseline review covered the tracked environment templates, client build
environments, Spring configuration, HTTP request collections, SQL migrations and
seed data, project documentation and the complete Git history available in this
repository.

At review time the history contained one commit, `Initial commit`
(`ec3c7c14bc63f206e8e38f94fcc1a5dce9de6ced`). No history rewrite was performed.

## Result

- No live credential or private key was identified in tracked files or the
  available commit history.
- Secret fields in tracked environment templates are empty. Non-empty values are
  configuration, explicit local-development defaults or example data.
- HTTP request credentials and tokens are placeholders. The SePay fallback key is
  also a replacement placeholder rather than a usable literal.
- `application-prod.yml` resolves credentials from environment variables.
- SQL migrations define credential-related columns but do not seed passwords,
  tokens, real users or customer records.
- Old personal contact and VPS endpoint details were removed from active
  configuration and documentation. Historical author names remain only where
  needed for attribution.
- The inherited production deployment assets were subsequently removed. The
  repository currently contains CI verification and local container support, but
  no production deployment workflow or VPS target.

## Local files that must remain untracked

- `.env`
- `client/.env.local`
- `hotel-booking/src/main/resources/application-local.yml`

The first three paths were present locally during the review and were confirmed
as ignored and untracked. Local files may contain real credentials; never paste
their values into issues, documentation, HTTP collections or commits.

## Required response if a secret is committed

1. Revoke or rotate the credential before any repository cleanup.
2. Remove it from the current tree and stop using the exposed value.
3. Assess history rewriting separately with all collaborators because it changes
   commit IDs and normally requires a coordinated force-push.
4. Treat forks, clones, CI logs and build artifacts as additional copies that may
   require cleanup.

Deleting a value in a later commit is not sufficient to remove it from history.
