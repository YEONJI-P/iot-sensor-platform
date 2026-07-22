.PHONY: demo demo-down

# 독립 풀 데모를 한 번에 기동한다 (컨테이너 + 시드 + 합성 스트림).
demo:
	@bash scripts/demo.sh

# 데모 스택을 내린다 (postgres 볼륨은 유지 — 완전 삭제는 'docker compose --profile live down -v').
demo-down:
	@docker compose --profile live down
