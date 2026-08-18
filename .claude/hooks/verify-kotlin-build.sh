#!/bin/bash
# Claude Code Stop hook: block finishing the turn when changed Kotlin code doesn't compile.
# Uses the project's documented compile-check (CLAUDE.md): ./gradlew :composeApp:compileAndroidMain

payload=$(cat)

# Already continuing because of this hook — don't loop.
printf '%s' "$payload" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && exit 0

cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}" || exit 0

# Only verify when Kotlin sources changed in the working tree.
git status --porcelain -- '*.kt' '*.kts' 2>/dev/null | grep -q . || exit 0

out=$(./gradlew :composeApp:compileAndroidMain -q 2>&1) && exit 0

{
  echo "Build verification failed (./gradlew :composeApp:compileAndroidMain). Fix the errors before finishing:"
  printf '%s\n' "$out" | tail -60
} >&2
exit 2
