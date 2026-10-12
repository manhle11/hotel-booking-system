#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
backlog_dir="${repo_root}/.github/backlog/phase-5"
manifest="${backlog_dir}/manifest.tsv"

project_title="${PROJECT_TITLE:-Giai đoạn 5 — Backlog nhóm 2}"
backend_assignee="${BACKEND_ASSIGNEE:-manhle11}"
frontend_assignee="${FRONTEND_ASSIGNEE:-}"
project_number="${PROJECT_NUMBER:-}"
dry_run="${DRY_RUN:-false}"

usage() {
  printf '%s\n' 'Usage: FRONTEND_ASSIGNEE=<login> ./scripts/create-phase-5-backlog.sh [--dry-run]'
  printf '%s\n' '  --dry-run  Validate the backlog and preview GitHub changes without writing.'
  printf '%s\n' '             With authenticated gh, also inspect existing GitHub state.'
}

for argument in "$@"; do
  case "${argument}" in
    --dry-run) dry_run=true ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: ${argument}" >&2; usage >&2; exit 1 ;;
  esac
done

if [[ "${dry_run}" != true && "${dry_run}" != false ]]; then
  echo 'DRY_RUN must be true or false.' >&2
  exit 1
fi

fail() {
  echo "Preflight failed: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

valid_login() {
  [[ "$1" =~ ^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$ && ${#1} -le 39 ]]
}

workflow_options=(Backlog Ready 'In Progress' Review Testing Done)
work_ids=()
issue_files=()
titles=()
roles=()
initial_workflows=()
issue_labels=()
issue_urls=()

[[ -f "${manifest}" ]] || fail "Missing manifest: ${manifest}"
awk -F '\t' '!/^#/ && NF && NF != 5 { print "Invalid manifest columns at line " NR > "/dev/stderr"; exit 1 }' \
  "${manifest}" || fail 'Manifest must have five tab-separated columns per item.'

seen_ids='|'
seen_titles='|'
backend_count=0
frontend_count=0
while IFS=$'\t' read -r work_id file role workflow labels; do
  [[ -z "${work_id}" || "${work_id}" == \#* ]] && continue

  [[ "${work_id}" =~ ^P5-[0-9][0-9]$ ]] || fail "Invalid work ID: ${work_id}"
  [[ "${file}" =~ ^${work_id}-[a-z0-9-]+\.md$ ]] || fail "Invalid filename for ${work_id}: ${file}"
  [[ "${seen_ids}" != *"|${work_id}|"* ]] || fail "Duplicate work ID: ${work_id}"
  seen_ids+="${work_id}|"

  body_file="${backlog_dir}/${file}"
  [[ -f "${body_file}" ]] || fail "Missing issue body: ${body_file}"
  IFS= read -r title < "${body_file}" || fail "Empty issue body: ${body_file}"
  [[ "${title}" == "# [${work_id}] "* ]] || fail "Title must start with # [${work_id}]: ${body_file}"
  [[ "${seen_titles}" != *"|${title}|"* ]] || fail "Duplicate issue title: ${title}"
  seen_titles+="${title}|"

  for heading in '## Hiện trạng' '## Thay đổi dự kiến' '## Acceptance criteria' \
    '## Người phụ trách' '## Backend/frontend dependency' '## Test evidence' '## Pull Request'; do
    grep -Fqx "${heading}" "${body_file}" || fail "Missing ${heading} in ${body_file}"
  done

  case "${role}" in
    backend) backend_count=$((backend_count + 1)) ;;
    frontend) frontend_count=$((frontend_count + 1)) ;;
    *) fail "Invalid assignee role for ${work_id}: ${role}" ;;
  esac

  valid_workflow=false
  for option in "${workflow_options[@]}"; do
    [[ "${workflow}" == "${option}" ]] && valid_workflow=true
  done
  [[ "${valid_workflow}" == true ]] || fail "Invalid workflow for ${work_id}: ${workflow}"
  [[ "${labels}" =~ ^[a-zA-Z0-9:,_-]+$ && "${labels}" == phase:5,* ]] \
    || fail "Invalid labels for ${work_id}: ${labels}"

  work_ids+=("${work_id}")
  issue_files+=("${body_file}")
  titles+=("${title#\# }")
  roles+=("${role}")
  initial_workflows+=("${workflow}")
  issue_labels+=("${labels}")
done < "${manifest}"

[[ ${#work_ids[@]} -eq 20 && ${backend_count} -eq 10 && ${frontend_count} -eq 10 ]] \
  || fail 'Expected exactly 20 items: 10 backend and 10 frontend.'
[[ -n "${project_title}" && "${project_title}" != *$'\n'* ]] || fail 'Invalid PROJECT_TITLE.'
[[ -z "${project_number}" || "${project_number}" =~ ^[1-9][0-9]*$ ]] || fail 'Invalid PROJECT_NUMBER.'
valid_login "${backend_assignee}" || fail 'Invalid BACKEND_ASSIGNEE.'
if [[ -z "${frontend_assignee}" && "${dry_run}" != true ]]; then
  fail "Set FRONTEND_ASSIGNEE to Hoàng Thị Ánh Nguyệt's GitHub username."
fi
if [[ -n "${frontend_assignee}" ]]; then
  valid_login "${frontend_assignee}" || fail 'Invalid FRONTEND_ASSIGNEE.'
fi
if [[ -n "${PROJECT_OWNER:-}" ]]; then
  valid_login "${PROJECT_OWNER}" || fail 'Invalid PROJECT_OWNER.'
fi
echo "Local preflight passed: ${#work_ids[@]} items, ${backend_count} backend, ${frontend_count} frontend."

label_specs=(
  'phase:5|5319E7|Giai đoạn 5: audit and improvement backlog'
  'area:backend|0E8A16|Backend or AI implementation'
  'area:frontend|1D76DB|Frontend implementation'
  'priority:P0|B60205|Critical or blocking'
  'priority:P1|D93F0B|High priority'
  'type:security|D4C5F9|Security hardening'
  'type:test|BFDADC|Test coverage and evidence'
  'type:concurrency|F9D0C4|Concurrency and race-condition work'
  'type:enhancement|A2EEEF|Improvement to an existing capability'
  'type:integration|C5DEF5|Cross-module integration'
  'type:performance|FBCA04|Performance and query work'
  'type:refactor|EDEDED|Refactor without presenting legacy code as new'
)
for epic in authentication user-profile room-pricing booking payment operations reporting ai quality demo; do
  label_specs+=("epic:${epic}|7057FF|Phase 5 epic: ${epic}")
done

known_label_names='|'
for spec in "${label_specs[@]}"; do
  IFS='|' read -r name color description <<< "${spec}"
  known_label_names+="${name}|"
done
for index in "${!work_ids[@]}"; do
  IFS=',' read -r -a labels_for_issue <<< "${issue_labels[$index]}"
  for name in "${labels_for_issue[@]}"; do
    [[ "${known_label_names}" == *"|${name}|"* ]] \
      || fail "Unknown label ${name} in ${work_ids[$index]}."
  done
done

remote_available=true
if [[ "${dry_run}" == true ]]; then
  if ! command -v gh >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1 \
    || ! gh auth status >/dev/null 2>&1; then
    remote_available=false
  fi
else
  require_command gh
  require_command jq
  gh auth status >/dev/null || fail 'GitHub CLI is not authenticated.'
fi

if [[ "${remote_available}" == false ]]; then
  echo 'Dry run: GitHub CLI/authentication unavailable; remote checks skipped.'
  echo "Would create or reuse project: ${project_title}"
  echo 'Would create missing labels, link project to repository, and add missing issues/items.'
  for index in "${!work_ids[@]}"; do
    echo "Would create or reuse ${work_ids[$index]} (${roles[$index]}, ${initial_workflows[$index]}): ${titles[$index]}"
  done
  if [[ -z "${frontend_assignee}" ]]; then
    echo 'Set FRONTEND_ASSIGNEE before the real run to validate assignability.'
  fi
  exit 0
fi

cd "${repo_root}"
repo_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
[[ "${repo_name}" =~ ^[a-zA-Z0-9-]+/[a-zA-Z0-9._-]+$ ]] || fail "Could not resolve repository: ${repo_name}"
repo_owner="${repo_name%%/*}"
project_owner="${PROJECT_OWNER:-${repo_owner}}"

repo_json="$(gh api "repos/${repo_name}")" || fail "Cannot read repository ${repo_name}."
[[ "$(jq -r '.has_issues' <<< "${repo_json}")" == true ]] || fail 'GitHub Issues are disabled for this repository.'
for assignee in "${backend_assignee}" "${frontend_assignee}"; do
  [[ -z "${assignee}" ]] && continue
  gh api --silent "repos/${repo_name}/assignees/${assignee}" \
    || fail "${assignee} cannot be assigned issues in ${repo_name}."
done

if [[ -z "${project_number}" ]]; then
  projects_json="$(gh project list --owner "${project_owner}" --limit 100 --format json)" \
    || fail "Cannot list projects for ${project_owner}; check project scope."
  project_count="$(jq '.projects | length' <<< "${projects_json}")"
  [[ "${project_count}" -lt 100 ]] || fail 'Project list may be truncated; set PROJECT_NUMBER.'
  matching_projects="$(jq -r --arg title "${project_title}" \
    '[.projects[] | select(.title == $title) | .number] | length' <<< "${projects_json}")"
  [[ "${matching_projects}" -le 1 ]] || fail "Multiple projects named ${project_title}; set PROJECT_NUMBER."
  if [[ "${matching_projects}" -eq 1 ]]; then
    project_number="$(jq -r --arg title "${project_title}" \
      '.projects[] | select(.title == $title) | .number' <<< "${projects_json}")"
  fi
fi

project_id=''
workflow_field_id=''
project_linked=false
project_items_json='{"items":[]}'
if [[ -n "${project_number}" ]]; then
  project_id="$(gh project view "${project_number}" --owner "${project_owner}" --format json --jq '.id')" \
    || fail "Cannot read project #${project_number}."
  [[ -n "${project_id}" && "${project_id}" != null ]] || fail "Missing project ID for #${project_number}."

  project_fields_json="$(gh project field-list "${project_number}" --owner "${project_owner}" --format json)" \
    || fail "Cannot read fields of project #${project_number}."
  field_count="$(jq '[.fields[] | select(.name == "Workflow")] | length' <<< "${project_fields_json}")"
  [[ "${field_count}" -le 1 ]] || fail 'Project has multiple Workflow fields.'
  if [[ "${field_count}" -eq 1 ]]; then
    workflow_field_id="$(jq -r '.fields[] | select(.name == "Workflow") | .id' <<< "${project_fields_json}")"
    field_type="$(jq -r '.fields[] | select(.name == "Workflow") | .type' <<< "${project_fields_json}")"
    [[ "${field_type}" == ProjectV2SingleSelectField ]] || fail 'Workflow must be a single-select field.'
    for option in "${workflow_options[@]}"; do
      jq -e --arg option "${option}" \
        '.fields[] | select(.name == "Workflow") | .options[] | select(.name == $option)' \
        <<< "${project_fields_json}" >/dev/null || fail "Workflow is missing option: ${option}"
    done
  fi

  linked_json="$(gh api graphql -f query='query($id: ID!) { node(id: $id) { ... on ProjectV2 { repositories(first: 100) { nodes { nameWithOwner } pageInfo { hasNextPage } } } } }' -f id="${project_id}")" \
    || fail 'Cannot inspect project repository links.'
  project_linked="$(jq -r --arg repo "${repo_name}" \
    '[.data.node.repositories.nodes[] | select(.nameWithOwner == $repo)] | length > 0' \
    <<< "${linked_json}")"
  if [[ "${project_linked}" != true ]]; then
    has_more_links="$(jq -r '.data.node.repositories.pageInfo.hasNextPage' <<< "${linked_json}")"
    [[ "${has_more_links}" == false ]] || fail 'Project has more than 100 linked repositories; link check needs pagination.'
  fi

  project_items_json="$(gh project item-list "${project_number}" --owner "${project_owner}" \
    --limit 1000 --format json)" || fail 'Cannot inspect project items.'
  item_count="$(jq '.items | length' <<< "${project_items_json}")"
  [[ "${item_count}" -lt 1000 ]] || fail 'Project item list may be truncated.'
fi

labels_json="$(gh label list --repo "${repo_name}" --limit 1000 --json name)" \
  || fail 'Cannot read repository labels.'
issues_json="$(gh issue list --repo "${repo_name}" --state all --limit 1000 --json title,url)" \
  || fail 'Cannot read repository issues.'
issue_count="$(jq 'length' <<< "${issues_json}")"
[[ "${issue_count}" -lt 1000 ]] || fail 'Issue list may be truncated; cannot safely detect duplicates.'

for index in "${!work_ids[@]}"; do
  match_count="$(jq --arg prefix "[${work_ids[$index]}] " \
    '[.[] | select(.title | startswith($prefix))] | length' <<< "${issues_json}")"
  [[ "${match_count}" -le 1 ]] || fail "Multiple GitHub issues match ${work_ids[$index]}; resolve manually."
  issue_urls+=("$(jq -r --arg prefix "[${work_ids[$index]}] " \
    '.[] | select(.title | startswith($prefix)) | .url' <<< "${issues_json}")")
done
echo "GitHub preflight passed: ${repo_name}; both assignees checked when supplied."

if [[ "${dry_run}" == true ]]; then
  if [[ -z "${project_number}" ]]; then
    echo "Would create project: ${project_title}"
  else
    echo "Would reuse project #${project_number}: ${project_title}"
  fi
  [[ "${project_linked}" == true ]] || echo "Would link project to ${repo_name}"
  [[ -n "${workflow_field_id}" ]] || echo 'Would create Workflow field with six options.'
  for spec in "${label_specs[@]}"; do
    IFS='|' read -r name color description <<< "${spec}"
    jq -e --arg name "${name}" '.[] | select(.name == $name)' <<< "${labels_json}" >/dev/null \
      || echo "Would create label: ${name}"
  done
  for index in "${!work_ids[@]}"; do
    if [[ -z "${issue_urls[$index]}" ]]; then
      echo "Would create ${work_ids[$index]} for ${roles[$index]} and add it as ${initial_workflows[$index]}"
    else
      item_count="$(jq --arg url "${issue_urls[$index]}" \
        '[.items[] | select(.content.url == $url)] | length' <<< "${project_items_json}")"
      if [[ "${item_count}" -eq 0 ]]; then
        echo "Would keep ${work_ids[$index]} issue and add it to project as ${initial_workflows[$index]}"
      else
        echo "Would keep ${work_ids[$index]} issue and project item unchanged"
      fi
    fi
  done
  if [[ -z "${frontend_assignee}" ]]; then
    echo 'Set FRONTEND_ASSIGNEE before the real run to validate assignability.'
  fi
  exit 0
fi

for spec in "${label_specs[@]}"; do
  IFS='|' read -r name color description <<< "${spec}"
  if ! jq -e --arg name "${name}" '.[] | select(.name == $name)' <<< "${labels_json}" >/dev/null; then
    gh label create "${name}" --repo "${repo_name}" --color "${color}" --description "${description}"
  fi
done

if [[ -z "${project_number}" ]]; then
  project_number="$(gh project create --owner "${project_owner}" --title "${project_title}" \
    --format json --jq '.number')"
  project_id="$(gh project view "${project_number}" --owner "${project_owner}" --format json --jq '.id')"
fi

if [[ "${project_linked}" != true ]]; then
  gh project link "${project_number}" --owner "${project_owner}" --repo "${repo_name}"
fi

if [[ -z "${workflow_field_id}" ]]; then
  gh project field-create "${project_number}" --owner "${project_owner}" --name Workflow \
    --data-type SINGLE_SELECT --single-select-options 'Backlog,Ready,In Progress,Review,Testing,Done' >/dev/null
  project_fields_json="$(gh project field-list "${project_number}" --owner "${project_owner}" --format json)"
  workflow_field_id="$(jq -r '.fields[] | select(.name == "Workflow") | .id' <<< "${project_fields_json}")"
fi

for index in "${!work_ids[@]}"; do
  issue_url="${issue_urls[$index]}"
  if [[ -z "${issue_url}" ]]; then
    assignee="${backend_assignee}"
    if [[ "${roles[$index]}" == frontend ]]; then
      assignee="${frontend_assignee}"
    fi
    issue_url="$(sed '1d' "${issue_files[$index]}" | gh issue create --repo "${repo_name}" \
      --title "${titles[$index]}" --body-file - --assignee "${assignee}" \
      --label "${issue_labels[$index]}")"
    echo "Created ${work_ids[$index]}: ${issue_url}"
  else
    echo "Kept existing ${work_ids[$index]}: ${issue_url}"
  fi

  item_id="$(jq -r --arg url "${issue_url}" \
    '.items[] | select(.content.url == $url) | .id' <<< "${project_items_json}")"
  if [[ -z "${item_id}" ]]; then
    item_id="$(gh project item-add "${project_number}" --owner "${project_owner}" \
      --url "${issue_url}" --format json --jq '.id')"
    option_id="$(jq -r --arg option "${initial_workflows[$index]}" \
      '.fields[] | select(.name == "Workflow") | .options[] | select(.name == $option) | .id' \
      <<< "${project_fields_json}")"
    [[ -n "${option_id}" ]] || fail "Workflow option not found for ${work_ids[$index]}."
    gh project item-edit --id "${item_id}" --project-id "${project_id}" \
      --field-id "${workflow_field_id}" --single-select-option-id "${option_id}" >/dev/null
  fi
done

echo "Phase 5 backlog initialized: ${project_title} (#${project_number})."
echo 'Existing issues and project item values were left unchanged.'
echo 'Open the Project as a board and group by Workflow to show the six requested columns.'
