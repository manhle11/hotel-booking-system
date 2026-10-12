#!/usr/bin/env bash

set -euo pipefail

repository="${GITHUB_REPOSITORY:-manhle11/hotel-booking-system}"

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required." >&2
  echo "Use a fine-grained token with Administration: read and write." >&2
  exit 1
fi

if [[ ! "$repository" =~ ^[^/]+/[^/]+$ ]]; then
  echo "GITHUB_REPOSITORY must use the owner/repository format." >&2
  exit 1
fi

api_url="https://api.github.com/repos/${repository}/branches/main/protection"

curl --fail-with-body --silent --show-error \
  --request PUT \
  --url "$api_url" \
  --header "Accept: application/vnd.github+json" \
  --header "Authorization: Bearer ${GH_TOKEN}" \
  --header "X-GitHub-Api-Version: 2026-03-10" \
  --data-binary @- <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "Backend CI",
      "Frontend CI"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

echo
echo "Protection configured for ${repository}:main."
