# AI Autonomous Debugging System

Autonomous Android debugging loop for this repo. When Firebase Test Lab fails,
a worker drives OpenCode with a rotating pool of **FREE** models to find the
root cause, make the smallest safe fix, verify it with a local Gradle build,
push it to an isolated `feature/ai-fix-*` branch, and open a GitHub PR once CI
passes. Never touches `main`.

```
GitHub Actions ──build APK──▶ Firebase Test Lab ──failure──▶ ai-debug worker
                                                                 │
    PR on pass ◀── ai-fix-verify workflow ◀── push feature/ai-fix-*
                                                                 │
                                                        FREE model router
                                                             │  │  │
                                                         LongCat  Nemotron  DeepSeek ...
```

## Components

| Path | Purpose |
|------|---------|
| `.github/workflows/android-debug-test.yml` | Build debug APK + run Firebase Test Lab on push to main/dev or manual dispatch. On failure, notifies the worker (webhook) and/or leaves artifacts for a polling worker. |
| `.github/workflows/ai-fix-verify.yml` | Runs on every push to `feature/ai-fix-*`. Builds + Firebase-tests the fix branch; opens/marks-ready a PR on PASS. |
| `scripts/parse_firebase_results.py` | Turns raw `gcloud` output + logcat/GCS artifacts into `ci-results/summary.json` + `summary.md`. |
| `tools/ai-debug/ai_debug/` | Python package: worker, orchestrator, router, health, driver, safety, job state. |
| `tools/ai-debug/config/free_models.json` | **The only place models are defined** — add/remove/reorder free models here. |
| `tools/ai-debug/deploy-remote.sh` | Deploy the worker to the Android build box (SSH) and run it as a polling worker. |
| `tools/ai-debug/Dockerfile` | Render/Railway image (webhook receiver + OpenCode + Android SDK/NDK). |

## FREE models only

`config/free_models.json` lists the models the system may use. All are free via
the OpenCode provider. The router **never** silently picks a paid model — if
no free model is available the job stops safely and reports:

```
All configured free AI models are currently unavailable.
```

Verify current availability with `opencode models`.

## Task-based routing

The router classifies each failure (`GRADLE_BUILD`, `KOTLIN`, `C_CPP`, `FFMPEG`,
`CRASH_ANALYSIS`, `LOGCAT_ANALYSIS`, `SIMPLE_BUG_FIX`, ...) then picks the
highest-priority healthy free model for that task, e.g.:

- Large repo / multi-file reasoning → LongCat
- Crash / logcat / reasoning → Nemotron
- Kotlin / UI / Gradle / CI → DeepSeek
- C++ / FFmpeg / MediaCodec → Mimo
- Simple one-file fix → Big-Pickle / Ling / Laguna

## Model fallback + health

Every model call has a timeout, per-model retry, exponential backoff + jitter,
and a fallback chain. Failure kinds (timeout, hang, rate limit, error, empty)
are recorded per model. A model that fails repeatedly is marked
`UNAVAILABLE` and cooled down; after the cooldown a lightweight health check
re-admits it on success. The health state persists across worker restarts
(`health.json` under `AI_DEBUG_DATA_DIR`).

```
LongCat   -> timeout -> retry -> timeout -> mark unhealthy
Nemotron  -> HTTP 429 -> switch
DeepSeek  -> network fail -> switch
Big-Pickle-> success -> continue
```

The task, repository state, previous analysis, logs and attempt count are
preserved — a model failure never restarts the whole debugging process.

## Branch safety

- The AI may only work on `feature/ai-fix-*` / `fix/ai-fix-*` branches.
- `assert_safe_branch` structurally blocks `main`, `master`, `production`,
  `release/*`.
- After 3 failed attempts a GitHub issue is opened; the fix branch is left
  intact with the AI's notes under `.ai-debug/`.
- The PR is never auto-merged.

## Safety checks

Before any AI change is committed, `safety.py` validates the diff:

- No secrets / keystores / `local.properties` / CI workflow files touched.
- No tests deleted, disabled (`@Ignore`), or commented out.
- No lint/assertions weakened.
- Violations → the change is discarded and the attempt recorded as failed.

## Job persistence / recovery

Every step is persisted:

- `debug-job-*.json` per job under `AI_DEBUG_DATA_DIR/jobs/` (resumable across
  worker restarts).
- `.ai-debug/state.json` on the fix branch tracks attempt count + history, so a
  Firebase failure on a pushed fix branch resumes counting — never resets to 0.
- Infrastructure failures (clone error, push failure, model pool exhausted) are
  recorded and reported as such — the AI does not invent fake code fixes.

## Deployment: Android build box as worker (polling mode)

GitHub Actions cannot reach a LAN IP, so the worker runs on the build box and
**polls** GitHub for failed runs:

```bash
# 1. on the box, set env
export GITHUB_TOKEN=ghp_...   # PAT with repo scope
export GITHUB_REPO=owner/repo
export AI_DEBUG_DATA_DIR=~/.ai-debug-data

# 2. deploy + start (from this repo)
./tools/ai-debug/deploy-remote.sh u0_a258@192.168.49.1 8022
ssh -p 8022 u0_a258@192.168.49.1 'nohup ~/ai-debug/start.sh > ~/ai-debug/worker.log 2>&1 &'
```

The box needs `opencode`, `gh`, Python 3, git, and the repo's Gradle toolchain.

## Deployment: Render/Railway (webhook mode)

If the worker has a public URL, the webhook path works:

1. `gh secret set RENDER_WEBHOOK_URL RENDER_WEBHOOK_SECRET FIREBASE_SERVICE_ACCOUNT FIREBASE_PROJECT_ID`
2. Deploy `tools/ai-debug/` with the `Dockerfile`.
3. The workflow signs the payload with HMAC-SHA256; the worker verifies it.

## Manual / CLI

```bash
python3 -m ai_debug poll --repo owner/repo --interval 300
python3 -m ai_debug handle --repo owner/repo --run-id 1234 --summary-file ci-results/summary.json
python3 -m ai_debug health
python3 -m ai_debug webhook --port 8080
```

## Tests

```bash
cd tools/ai-debug && python3 -m unittest discover -s tests -v
```
