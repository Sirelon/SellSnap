#!/usr/bin/env bash
#
# Deploys the SellSnap Threads agent (Cloud Run service "sellsnap-agent") and everything around it.
# Design: server/THREADS-AGENT-DESIGN.md. Live check after the deploy: server/deploy/STAGING-CHECKLIST.md.
#
# Run from anywhere:   OWNER_TELEGRAM_ID=<id> OWNER_EMAIL=<email> server/deploy/deploy.sh [step ...]
#
#   OWNER_TELEGRAM_ID   the owner's numeric Telegram user id (the only user the bot obeys)
#   OWNER_EMAIL         receives the Cloud Monitoring alert when a tick fails
#   [step ...]          run only these steps (1-12); with no argument steps 1-9, 11 and 12 run in order
#
# Every step looks at the live state first and skips what already exists, so the script is safe to
# run again. Each step that creates a billable or public resource, changes IAM, or changes live
# rules is marked "# [APPROVE]" and asks `confirm` before it acts. Answer anything but y to skip it.
#
# Secrets are read into variables and passed to curl through stdin or a mode-600 temp file. Nothing
# secret is echoed, and `set -x` must never be turned on here.
#
# Needs: gcloud (signed in as a project owner), curl, jq, openssl, npx (Node), and a JDK for Gradle.

set -euo pipefail

PROJECT=sellsnap-6e85c
REGION=europe-west1
BUCKET=sellsnap-6e85c.firebasestorage.app
SERVICE=sellsnap-agent
AGENT_SA_NAME=sellsnap-agent
SCHEDULER_SA_NAME=sellsnap-scheduler
AGENT_SA="$AGENT_SA_NAME@$PROJECT.iam.gserviceaccount.com"
SCHEDULER_SA="$SCHEDULER_SA_NAME@$PROJECT.iam.gserviceaccount.com"
PLAY_PACKAGE=com.sirelon.sellsnap
APP_STORE_ID=6776001373
LANDING_URL=https://sirelon.github.io/SellSnap/
# What a post links to: the website, which reports visits and store taps to /click (design D15).
# {lang} and {code} are filled by the service per post; the website reads them as ?lang= and ?p=.
POST_LINK_TEMPLATE='https://sirelon.github.io/SellSnap/?lang={lang}&p={code}'

# Secrets the service reads. The first two are created by the owner (THREADS-OWNER-SETUP.html, step 10);
# GEMINI_API_KEY is an AI Studio key (project without billing) that the owner stores in Secret Manager;
# TELEGRAM_WEBHOOK_SECRET is created by step 3.
OWNER_SECRETS=(TELEGRAM_BOT_TOKEN THREADS_APP_SECRET GEMINI_API_KEY)
WEBHOOK_SECRET_NAME=TELEGRAM_WEBHOOK_SECRET

APIS=(
  run.googleapis.com
  cloudbuild.googleapis.com
  artifactregistry.googleapis.com
  cloudscheduler.googleapis.com
  monitoring.googleapis.com
)

LOG_EXCLUSION_NAME=sellsnap-agent-go-requests
LOG_EXCLUSION_FILTER='resource.type="cloud_run_revision" AND resource.labels.service_name="sellsnap-agent" AND (httpRequest.requestUrl:"/go/" OR httpRequest.requestUrl:"/click")'

ALERT_DISPLAY_NAME="SellSnap agent: tick attempt failed"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_FILE="$ROOT/server/deploy/tick-alert-policy.json"

# Filled in by derive_url (step 6).
URL=""

# Mode-600 temp file that step 8 writes the webhook secret into; main removes it on exit.
SECRET_TMP=""

log() { printf '\n== %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# Asks the owner before a [APPROVE] action. Returns non-zero unless the answer is y or yes.
confirm() {
  local answer
  printf '\n[APPROVE] %s\nProceed? [y/N] ' "$1" >&2
  read -r answer || answer=n
  [[ "$answer" == [yY] || "$answer" == [yY][eE][sS] ]]
}

# Calls a Google REST API with the signed-in user's token. Usage: api METHOD URL [curl args...]
api() {
  local method="$1" url="$2"
  shift 2
  curl -sS -X "$method" \
    -H "Authorization: Bearer $(gcloud auth print-access-token)" \
    -H "x-goog-user-project: $PROJECT" \
    -H "Content-Type: application/json" \
    "$@" "$url"
}

secret_exists() { gcloud secrets describe "$1" --project "$PROJECT" >/dev/null 2>&1; }
service_account_exists() { gcloud iam service-accounts describe "$1" --project "$PROJECT" >/dev/null 2>&1; }
job_exists() { gcloud scheduler jobs describe "$1" --location "$REGION" --project "$PROJECT" >/dev/null 2>&1; }

# Reads an IAM policy (JSON) and tells whether MEMBER holds ROLE. Usage: member_has_role POLICY ROLE MEMBER
member_has_role() {
  jq -e --arg r "$2" --arg m "$3" \
    '[.bindings[]? | select(.role == $r and (.members | index($m)))] | length > 0' <<<"$1" >/dev/null
}

# IAM grants collected by want_* and applied by apply_wanted. Each entry is "kind|target|role".
WANT=()

want_secret_access() {
  local policy
  policy="$(gcloud secrets get-iam-policy "$1" --project "$PROJECT" --format=json)"
  member_has_role "$policy" roles/secretmanager.secretAccessor "serviceAccount:$AGENT_SA" \
    || WANT+=("secret|$1|roles/secretmanager.secretAccessor")
  return 0
}

# Confirms the collected grants once, then adds them. Re-adding an existing binding is harmless.
apply_wanted() {
  local what="$1" grant kind target role
  if (( ${#WANT[@]} == 0 )); then
    log "$what: nothing to grant, already in place"
    return 0
  fi
  # [APPROVE] IAM change: $what
  confirm "IAM change for $AGENT_SA, free: ${WANT[*]}" || { WANT=(); return 0; }
  for grant in "${WANT[@]}"; do
    IFS='|' read -r kind target role <<<"$grant"
    case "$kind" in
      project)
        gcloud projects add-iam-policy-binding "$target" --member "serviceAccount:$AGENT_SA" \
          --role "$role" --condition=None --quiet >/dev/null ;;
      bucket)
        gcloud storage buckets add-iam-policy-binding "gs://$target" --member "serviceAccount:$AGENT_SA" \
          --role "$role" >/dev/null ;;
      secret)
        gcloud secrets add-iam-policy-binding "$target" --project "$PROJECT" \
          --member "serviceAccount:$AGENT_SA" --role "$role" >/dev/null ;;
    esac
    log "granted $role on $kind $target"
  done
  WANT=()
}

# Step 6. The Cloud Run URL is deterministic, so the service can know its own address before it exists.
# No trailing slash: PUBLIC_BASE_URL and OIDC_AUDIENCE must be byte-identical to the audience Scheduler signs.
derive_url() {
  local number
  number="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
  [[ "$number" =~ ^[0-9]+$ ]] || die "could not read the project number"
  URL="https://$SERVICE-$number.$REGION.run.app"
  log "Step 6: service URL is $URL"
}

# The --set-env-vars argument of step 5. The values hold no comma, so gcloud's default list separator
# would work, but POST_LINK_TEMPLATE carries ? & = { }, so the list uses gcloud's alternate-delimiter
# syntax (^|^ makes | the separator; no value holds a |). See `gcloud topic escaping`.
build_env_vars() {
  local v="^|^OWNER_TELEGRAM_ID=$OWNER_TELEGRAM_ID"
  v+="|SCHEDULER_SA_EMAIL=$SCHEDULER_SA"
  v+="|OIDC_AUDIENCE=$URL"
  v+="|PUBLIC_BASE_URL=$URL"
  v+="|POST_LINK_TEMPLATE=$POST_LINK_TEMPLATE"
  v+="|STORAGE_BUCKET=$BUCKET"
  v+="|PLAY_PACKAGE=$PLAY_PACKAGE"
  v+="|APP_STORE_ID=$APP_STORE_ID"
  v+="|LANDING_URL=$LANDING_URL"
  # APPLE_PROVIDER_TOKEN is optional (Config.kt); without it App Store links carry only ct=.
  if [[ -n "${APPLE_PROVIDER_TOKEN:-}" ]]; then v+="|APPLE_PROVIDER_TOKEN=$APPLE_PROVIDER_TOKEN"; fi
  printf '%s' "$v"
}

# Step 1. Free: enabling an API costs nothing; the billable use comes in later steps.
step1_apis() {
  log "Step 1: enable APIs"
  local enabled api
  local missing=()
  enabled="$(gcloud services list --enabled --project "$PROJECT" --format='value(config.name)')"
  for api in "${APIS[@]}"; do
    grep -qx "$api" <<<"$enabled" || missing+=("$api")
  done
  if (( ${#missing[@]} == 0 )); then
    echo "all APIs are enabled"
    return 0
  fi
  # [APPROVE] enable APIs (free)
  confirm "Enable APIs ${missing[*]} on $PROJECT (free)" || return 0
  gcloud services enable "${missing[@]}" --project "$PROJECT"
}

# Step 2. The agent runs as sellsnap-agent. sellsnap-scheduler only signs OIDC tokens: it gets no
# run.invoker grant, because the service is public and the code checks the token itself.
step2_service_accounts() {
  log "Step 2: service accounts and roles"
  local name sa
  for name in "$AGENT_SA_NAME" "$SCHEDULER_SA_NAME"; do
    sa="$name@$PROJECT.iam.gserviceaccount.com"
    if service_account_exists "$sa"; then
      echo "service account $sa exists"
      continue
    fi
    # [APPROVE] create service account (free)
    confirm "Create service account $sa (free)" || continue
    gcloud iam service-accounts create "$name" --display-name "SellSnap $name" --project "$PROJECT"
  done

  service_account_exists "$AGENT_SA" || { echo "no $AGENT_SA, skipping its roles"; return 0; }

  local project_policy bucket_policy secret
  project_policy="$(gcloud projects get-iam-policy "$PROJECT" --format=json)"
  bucket_policy="$(gcloud storage buckets get-iam-policy "gs://$BUCKET" --format=json)"
  WANT=()
  member_has_role "$project_policy" roles/datastore.user "serviceAccount:$AGENT_SA" \
    || WANT+=("project|$PROJECT|roles/datastore.user")
  member_has_role "$bucket_policy" roles/storage.objectAdmin "serviceAccount:$AGENT_SA" \
    || WANT+=("bucket|$BUCKET|roles/storage.objectAdmin")
  for secret in "${OWNER_SECRETS[@]}"; do
    secret_exists "$secret" || die "secret $secret does not exist yet; create it first (THREADS-OWNER-SETUP.html, step 10)"
    want_secret_access "$secret"
  done
  # [APPROVE] datastore.user, storage.objectAdmin on the bucket, secretAccessor on the owner's secrets
  apply_wanted "agent roles"
}

# Step 3. The webhook secret is random and lives only in Secret Manager.
step3_webhook_secret() {
  log "Step 3: $WEBHOOK_SECRET_NAME"
  if secret_exists "$WEBHOOK_SECRET_NAME"; then
    echo "secret $WEBHOOK_SECRET_NAME exists"
  else
    # [APPROVE] create secret (free tier: 6 active versions)
    confirm "Create secret $WEBHOOK_SECRET_NAME from openssl rand -hex 32 (free tier)" || return 0
    openssl rand -hex 32 | tr -d '\n' \
      | gcloud secrets create "$WEBHOOK_SECRET_NAME" --data-file=- --replication-policy=automatic --project "$PROJECT"
  fi
  if service_account_exists "$AGENT_SA"; then
    WANT=()
    want_secret_access "$WEBHOOK_SECRET_NAME"
    # [APPROVE] secretAccessor on the webhook secret
    apply_wanted "webhook secret access"
  fi
}

# Step 4. Firestore deletes social_updates documents once expireAt has passed.
step4_firestore_ttl() {
  log "Step 4: Firestore TTL on social_updates.expireAt"
  if gcloud firestore fields ttls list --project "$PROJECT" --format='value(name)' \
      | grep -q 'collectionGroups/social_updates/fields/expireAt'; then
    echo "TTL policy exists"
    return 0
  fi
  # [APPROVE] live Firestore setting (free tier: TTL deletes of a few documents a day)
  confirm "Enable the Firestore TTL policy on social_updates.expireAt (free tier)" || return 0
  gcloud firestore fields ttls update expireAt --collection-group=social_updates --enable-ttl \
    --project "$PROJECT" --async
}

# Step 5 (with step 6's URL). Builds on this Mac because :shared needs the Android SDK, then uploads only
# the Dockerfile and build/install (server/.gcloudignore) and lets Cloud Build make the image.
step5_deploy() {
  log "Step 5: build and deploy $SERVICE"
  local secret
  for secret in "${OWNER_SECRETS[@]}" "$WEBHOOK_SECRET_NAME"; do
    secret_exists "$secret" || die "secret $secret does not exist; run step 3 (and the owner's secret setup) first"
  done
  service_account_exists "$AGENT_SA" || die "service account $AGENT_SA does not exist; run step 2 first"
  [[ -n "$URL" ]] || derive_url

  local env_vars
  env_vars="$(build_env_vars)"

  local secrets="TELEGRAM_BOT_TOKEN=TELEGRAM_BOT_TOKEN:latest"
  secrets+=",THREADS_APP_SECRET=THREADS_APP_SECRET:latest"
  secrets+=",TELEGRAM_WEBHOOK_SECRET=TELEGRAM_WEBHOOK_SECRET:latest"
  secrets+=",GEMINI_API_KEY=GEMINI_API_KEY:latest"

  local what="Deploy Cloud Run service $SERVICE to $URL, public (--allow-unauthenticated), min 0 / max 2 instances, 512Mi, CPU boost. Billable, usage-based: a handful of requests an hour stays in the free tier. Cloud Build and Artifact Registry are free tier at this size."
  if gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" >/dev/null 2>&1; then
    what="Roll out a new revision of the existing service $SERVICE. $what"
  fi
  # [APPROVE] creates or updates a public, billable Cloud Run service
  confirm "$what" || return 0

  (cd "$ROOT" && ./gradlew :server:installDist)
  (cd "$ROOT" && gcloud run deploy "$SERVICE" \
    --source server \
    --project "$PROJECT" \
    --region "$REGION" \
    --service-account "$AGENT_SA" \
    --allow-unauthenticated \
    --min-instances 0 \
    --max-instances 2 \
    --timeout 1800 \
    --memory 512Mi \
    --cpu-boost \
    --concurrency 20 \
    --set-env-vars "$env_vars" \
    --set-secrets "$secrets")

  local urls
  urls="$(gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" \
    --format='value(metadata.annotations["run.googleapis.com/urls"])')"
  if grep -qF "$URL" <<<"$urls"; then
    echo "deployed; $URL is a URL of the service"
  else
    echo "WARNING: $URL is not among the service URLs ($urls). PUBLIC_BASE_URL and OIDC_AUDIENCE are wrong; fix them before step 8 and 9." >&2
  fi
}

# Step 7. /go answers redirects only and /click answers 204 only. Each click is already stored in
# Firestore, so their request logs are noise. An existing exclusion with another filter is updated.
step7_log_exclusion() {
  log "Step 7: Cloud Logging exclusion for /go and /click request logs"
  local current
  current="$(gcloud logging sinks describe _Default --project "$PROJECT" --format=json \
    | jq -r --arg n "$LOG_EXCLUSION_NAME" '.exclusions[]? | select(.name == $n) | .filter')"
  if [[ -z "$current" ]]; then
    # [APPROVE] changes the _Default log sink (free: it reduces log ingestion)
    confirm "Add exclusion $LOG_EXCLUSION_NAME to the _Default sink: $LOG_EXCLUSION_FILTER (free)" || return 0
    gcloud logging sinks update _Default --project "$PROJECT" \
      --add-exclusion "name=$LOG_EXCLUSION_NAME,description=Redirect and click request logs of the SellSnap agent,filter=$LOG_EXCLUSION_FILTER"
  elif [[ "$current" == "$LOG_EXCLUSION_FILTER" ]]; then
    echo "exclusion $LOG_EXCLUSION_NAME exists with the current filter"
  else
    # [APPROVE] changes the _Default log sink (free: it reduces log ingestion)
    confirm "Update exclusion $LOG_EXCLUSION_NAME on the _Default sink from: $current  to: $LOG_EXCLUSION_FILTER (free)" || return 0
    gcloud logging sinks update _Default --project "$PROJECT" \
      --update-exclusion "name=$LOG_EXCLUSION_NAME,description=Redirect and click request logs of the SellSnap agent,filter=$LOG_EXCLUSION_FILTER"
  fi
}

# Calls a Telegram Bot API method. The token goes to curl through stdin, so it never shows in `ps`.
# Usage: telegram METHOD [JSON_BODY_FILE]
telegram() {
  local method="$1" body="${2:-}"
  if [[ -n "$body" ]]; then
    printf 'url = "https://api.telegram.org/bot%s/%s"\n' "$TELEGRAM_TOKEN" "$method" \
      | curl -sS --config - -H 'Content-Type: application/json' --data-binary "@$body"
  else
    printf 'url = "https://api.telegram.org/bot%s/%s"\n' "$TELEGRAM_TOKEN" "$method" | curl -sS --config -
  fi
}

# Step 8. Points Telegram at the service and registers the command menu.
step8_telegram() {
  log "Step 8: Telegram webhook and commands"
  [[ -n "$URL" ]] || derive_url
  secret_exists TELEGRAM_BOT_TOKEN || die "secret TELEGRAM_BOT_TOKEN does not exist"
  secret_exists "$WEBHOOK_SECRET_NAME" || die "secret $WEBHOOK_SECRET_NAME does not exist; run step 3"

  local TELEGRAM_TOKEN WEBHOOK_SECRET tmp info current commands_now
  TELEGRAM_TOKEN="$(gcloud secrets versions access latest --secret TELEGRAM_BOT_TOKEN --project "$PROJECT")"
  WEBHOOK_SECRET="$(gcloud secrets versions access latest --secret "$WEBHOOK_SECRET_NAME" --project "$PROJECT")"
  tmp="$(umask 077 && mktemp)"
  SECRET_TMP="$tmp"

  info="$(telegram getWebhookInfo)"
  current="$(jq -r '.result.url // ""' <<<"$info")"
  if [[ "$current" == "$URL/telegram" ]]; then
    echo "webhook already points at $URL/telegram"
  else
    # [APPROVE] repoints the live bot and drops its pending updates (free)
    if confirm "Set the Telegram webhook to $URL/telegram with the secret token, allowed_updates message and callback_query, and drop pending updates (free)"; then
      WEBHOOK_SECRET="$WEBHOOK_SECRET" jq -n --arg url "$URL/telegram" \
        '{url: $url, secret_token: env.WEBHOOK_SECRET, allowed_updates: ["message", "callback_query"], drop_pending_updates: true}' >"$tmp"
      telegram setWebhook "$tmp" | jq '{ok, description}'
    fi
  fi

  commands_now="$(telegram getMyCommands | jq -r '[.result[]?.command] | sort | join(",")')"
  if [[ "$commands_now" == "approval,post,refresh,resume,token" ]]; then
    echo "command menu is set"
  else
    # [APPROVE] sets the bot's command menu (free)
    if confirm "Set the Telegram command menu: post, token, approval, resume, refresh (free)"; then
      jq -n '{commands: [
        {command: "post", description: "Post to an account: /post <account> [now] <text>"},
        {command: "token", description: "Store a Threads token: /token <account> <token>"},
        {command: "approval", description: "Back to approval mode: /approval <account>"},
        {command: "resume", description: "Resume a paused account: /resume <account>"},
        {command: "refresh", description: "Refresh a Threads token now: /refresh <account>"}
      ]}' >"$tmp"
      telegram setMyCommands "$tmp" | jq '{ok, description}'
    fi
  fi
}

# Creates one Cloud Scheduler job that posts to the service with an OIDC token for the scheduler account.
# Usage: create_job NAME SCHEDULE TIMEZONE PATH DEADLINE [extra gcloud flags...]
create_job() {
  local name="$1" schedule="$2" zone="$3" path="$4" deadline="$5"
  shift 5
  gcloud scheduler jobs create http "$name" \
    --project "$PROJECT" --location "$REGION" \
    --schedule "$schedule" --time-zone "$zone" \
    --uri "$URL$path" --http-method POST \
    --attempt-deadline "$deadline" \
    --oidc-service-account-email "$SCHEDULER_SA" \
    --oidc-token-audience "$URL" \
    "$@"
}

# The batch answers 503 while the model is busy and 409 while another run holds the lease, so Scheduler retries:
# 2 retries, at least 10 minutes apart.
BATCH_RETRY_FLAGS=(--max-retry-attempts 2 --min-backoff 600s)

batch_retry_is_set() {
  local count backoff
  count="$(gcloud scheduler jobs describe sellsnap-batch --location "$REGION" --project "$PROJECT" --format='value(retryConfig.retryCount)')"
  backoff="$(gcloud scheduler jobs describe sellsnap-batch --location "$REGION" --project "$PROJECT" --format='value(retryConfig.minBackoffDuration)')"
  [[ "$count" == "2" && "$backoff" == "600s" ]]
}

# Step 9. Three jobs, all free tier (3 jobs per billing account): the hourly tick, the weekly batch and a manual batch
# for the Ukrainian account. The batch jobs are created paused. OIDC audience = the base URL, no trailing slash.
step9_scheduler() {
  log "Step 9: Cloud Scheduler jobs"
  [[ -n "$URL" ]] || derive_url
  service_account_exists "$SCHEDULER_SA" || die "service account $SCHEDULER_SA does not exist; run step 2"

  if job_exists sellsnap-tick; then
    echo "job sellsnap-tick exists"
  else
    # [APPROVE] creates a scheduled job that calls the live service every hour (free tier)
    if confirm "Create job sellsnap-tick: 30 * * * * Etc/UTC, POST $URL/tick, OIDC audience $URL, deadline 600s (free tier)"; then
      create_job sellsnap-tick "30 * * * *" Etc/UTC /tick 600s
    fi
  fi

  if job_exists sellsnap-batch; then
    echo "job sellsnap-batch exists"
    if batch_retry_is_set; then
      echo "job sellsnap-batch retries twice, 600s apart"
    else
      # [APPROVE] changes the retry policy of the existing job (free tier)
      if confirm "Update job sellsnap-batch: retry up to 2 times, at least 600s apart (free tier)"; then
        gcloud scheduler jobs update http sellsnap-batch --location "$REGION" --project "$PROJECT" "${BATCH_RETRY_FLAGS[@]}"
      fi
    fi
  else
    # [APPROVE] creates a job and pauses it at once (free tier)
    if confirm "Create job sellsnap-batch: 0 9 * * 0 Europe/Kyiv, POST $URL/batch, deadline 1800s, 2 retries 600s apart, then pause it (free tier)"; then
      create_job sellsnap-batch "0 9 * * 0" Europe/Kyiv /batch 1800s "${BATCH_RETRY_FLAGS[@]}"
      gcloud scheduler jobs pause sellsnap-batch --location "$REGION" --project "$PROJECT"
    fi
  fi

  if job_exists sellsnap-batch-manual; then
    echo "job sellsnap-batch-manual exists"
  else
    # [APPROVE] creates a job that stays paused; it only ever runs when the owner starts it by hand (free tier)
    if confirm "Create job sellsnap-batch-manual: POST $URL/batch with {\"accounts\":[\"ua\"]}, deadline 1800s, paused for good (free tier)"; then
      create_job sellsnap-batch-manual "0 9 * * 0" Europe/Kyiv /batch 1800s \
        --headers "Content-Type=application/json" --message-body '{"accounts":["ua"]}'
      gcloud scheduler jobs pause sellsnap-batch-manual --location "$REGION" --project "$PROJECT"
    fi
  fi

  # The owner decides when weekly batches start. Not run by this script; run it by hand:
  #   gcloud scheduler jobs resume sellsnap-batch --location "$REGION" --project "$PROJECT"
  # A manual batch for the Ukrainian account, at any time:
  #   gcloud scheduler jobs run sellsnap-batch-manual --location "$REGION" --project "$PROJECT"
}

# Step 10. Email channel plus a log-match policy on failed tick attempts.
step10_alerting() {
  log "Step 10: Cloud Monitoring email channel and tick alert"
  local channel policy tmp
  channel="$(api GET "https://monitoring.googleapis.com/v3/projects/$PROJECT/notificationChannels" \
    | jq -r --arg e "$OWNER_EMAIL" '.notificationChannels[]? | select(.type == "email" and .labels.email_address == $e) | .name' \
    | head -n 1)"
  if [[ -n "$channel" ]]; then
    echo "email channel exists: $channel"
  else
    # [APPROVE] creates a notification channel that emails the owner (free)
    if confirm "Create a Cloud Monitoring email channel for $OWNER_EMAIL (free)"; then
      channel="$(jq -n --arg e "$OWNER_EMAIL" \
        '{type: "email", displayName: "SellSnap agent owner", labels: {email_address: $e}}' \
        | api POST "https://monitoring.googleapis.com/v3/projects/$PROJECT/notificationChannels" --data-binary @- \
        | jq -r '.name // empty')"
      [[ -n "$channel" ]] || die "the notification channel was not created"
    fi
  fi
  [[ -n "$channel" ]] || { echo "no channel, skipping the alert policy"; return 0; }

  policy="$(api GET "https://monitoring.googleapis.com/v3/projects/$PROJECT/alertPolicies" -G \
    --data-urlencode "filter=display_name=\"$ALERT_DISPLAY_NAME\"" | jq -r '.alertPolicies[0].name // empty')"
  if [[ -n "$policy" ]]; then
    echo "alert policy exists: $policy"
    return 0
  fi
  # [APPROVE] creates a log-match alert policy (Cloud Monitoring alert conditions can cost a few cents a month)
  confirm "Create the alert policy from server/deploy/tick-alert-policy.json: failed sellsnap-tick attempts email $OWNER_EMAIL at most once an hour (about \$0.10 a month at worst)" || return 0
  tmp="$(umask 077 && mktemp)"
  sed "s#\${CHANNEL}#$channel#" "$POLICY_FILE" >"$tmp"
  api POST "https://monitoring.googleapis.com/v3/projects/$PROJECT/alertPolicies" --data-binary "@$tmp" \
    | jq '{name, displayName, error}'
  rm -f "$tmp"
}

# The live Storage ruleset's source text.
live_storage_rules() {
  local ruleset
  ruleset="$(api GET "https://firebaserules.googleapis.com/v1/projects/$PROJECT/releases/firebase.storage/$BUCKET" | jq -r '.rulesetName')"
  api GET "https://firebaserules.googleapis.com/v1/$ruleset" | jq -r '.source.files[0].content'
}

# Step 11. The CLI binary on this Mac is broken ("bad CPU type"), so it runs through npx.
step11_storage_rules() {
  log "Step 11: Storage rules"
  if [[ "$(live_storage_rules)" == "$(cat "$ROOT/storage.rules")" ]]; then
    echo "live Storage rules already match storage.rules"
    return 0
  fi
  echo "dry run (changes nothing):"
  (cd "$ROOT" && npx -y firebase-tools deploy --only storage --project "$PROJECT" --dry-run)
  # [APPROVE] changes the live Storage rules of the app's bucket (free)
  confirm "Deploy storage.rules to $BUCKET: public read and no client write under social/, app uploads unchanged (free)" || return 0
  (cd "$ROOT" && npx -y firebase-tools deploy --only storage --project "$PROJECT")
}

# Step 12. Keeps only the two newest server images, so the source-deploy repository stays inside
# Artifact Registry's free 0.5 GB. Free; deletes old images only.
step12_image_cleanup() {
  log "Step 12: Artifact Registry cleanup policy"
  local current
  current="$(gcloud artifacts repositories list-cleanup-policies cloud-run-source-deploy \
    --project "$PROJECT" --location "$REGION" --format=json 2>/dev/null || echo '[]')"
  if jq -e '.[]? | select(.name == "keep-latest-2")' <<<"$current" >/dev/null; then
    echo "cleanup policy exists"
    return 0
  fi
  # [APPROVE] deletes all but the two newest images in cloud-run-source-deploy (free)
  confirm "Keep only the 2 newest images in cloud-run-source-deploy (free; old revisions lose their image)" || return 0
  local policy
  policy="$(mktemp)"
  jq -n '[{name: "keep-latest-2", action: {type: "Keep"}, mostRecentVersions: {keepCount: 2}},
          {name: "delete-older", action: {type: "Delete"}, condition: {tagState: "ANY"}}]' >"$policy"
  gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy \
    --project "$PROJECT" --location "$REGION" --policy="$policy" --no-dry-run --quiet
  rm -f "$policy"
}

main() {
  local tool step
  trap '[[ -z "$SECRET_TMP" ]] || rm -f "$SECRET_TMP"' EXIT
  for tool in gcloud curl jq openssl npx; do
    command -v "$tool" >/dev/null || die "$tool is not installed"
  done
  [[ "${OWNER_TELEGRAM_ID:-}" =~ ^[0-9]+$ ]] || die "set OWNER_TELEGRAM_ID to the owner's numeric Telegram user id"
  [[ "${OWNER_EMAIL:-}" == *@* ]] || die "set OWNER_EMAIL to the address that receives the tick alert"

  local steps=("$@")
  # Step 10 (Cloud Monitoring alert) is off by default: the project runs frugal, and the bot alerts on
  # failures itself. Run it explicitly when the project can afford monitoring.
  if (( ${#steps[@]} == 0 )); then steps=(1 2 3 4 5 6 7 8 9 11 12); fi

  derive_url
  for step in "${steps[@]}"; do
    case "$step" in
      1) step1_apis ;;
      2) step2_service_accounts ;;
      3) step3_webhook_secret ;;
      4) step4_firestore_ttl ;;
      5) step5_deploy ;;
      6) ;; # the URL is derived above, before any step needs it
      7) step7_log_exclusion ;;
      8) step8_telegram ;;
      9) step9_scheduler ;;
      10) step10_alerting ;;
      11) step11_storage_rules ;;
      12) step12_image_cleanup ;;
      *) die "unknown step $step (1-12)" ;;
    esac
  done
  log "Done. Smoke test: gcloud scheduler jobs run sellsnap-tick --location $REGION --project $PROJECT"
}

# Sourcing the file defines the functions without running anything.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
