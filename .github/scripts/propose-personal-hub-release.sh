#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <personal-hub-checkout>" >&2
  exit 2
fi

hub_checkout=$1
: "${SOURCE_SHA:?SOURCE_SHA is required}"
: "${HUB_REPOSITORY:?HUB_REPOSITORY is required}"
: "${GH_TOKEN:?GH_TOKEN is required}"

if [[ ! $SOURCE_SHA =~ ^[0-9a-f]{40}$ ]]; then
  echo "SOURCE_SHA must be a lowercase 40-character Git SHA" >&2
  exit 2
fi

python3 "$hub_checkout/infra/ops/update_sensor_monitor_pin.py" \
  --repo-root "$hub_checkout" \
  --revision "$SOURCE_SHA"

cd "$hub_checkout"

if git diff --quiet; then
  echo "personal-hub already pins Sensor Monitor $SOURCE_SHA"
  exit 0
fi

branch="automation/sensor-monitor-$SOURCE_SHA"
existing_pr=$(gh pr list \
  --repo "$HUB_REPOSITORY" \
  --state all \
  --head "$branch" \
  --json url \
  --jq '.[0].url // empty')
if [[ -n $existing_pr ]]; then
  echo "Sensor release PR already exists: $existing_pr"
  exit 0
fi

if git ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1; then
  echo "Remote branch already exists without a matching PR: $branch" >&2
  exit 1
fi

git switch -c "$branch"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add infra/docker-compose.yml infra/README.md
git diff --cached --check
git commit -m "chore(deploy): Sensor 이미지 SHA 갱신"
git push --set-upstream origin "$branch"

short_sha=${SOURCE_SHA:0:7}
gh pr create \
  --repo "$HUB_REPOSITORY" \
  --base main \
  --head "$branch" \
  --title "chore(deploy): Sensor $short_sha 이미지 갱신" \
  --body-file - <<EOF
Sensor Monitor main CI와 세 이미지 발행이 성공한 뒤 자동으로 생성된 배포 제안입니다.

- source SHA: \`$SOURCE_SHA\`
- workflow run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID
- 변경 범위: backend·explain·simulator 이미지 pin과 personal-hub 배포 문서

병합 전 확인:

- [ ] 새 필수 환경변수 유무
- [ ] Flyway migration과 이전 이미지 rollback 호환성
- [ ] GHCR 세 이미지 발행 성공

이 PR은 자동 병합되지 않습니다.
EOF
