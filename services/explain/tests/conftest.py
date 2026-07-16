"""테스트 환경 격리.

app.dependencies 의 Settings 는 런타임엔 .env 를 읽지만, 테스트에선 읽지 않아야
한다 — 개발자 로컬 .env(gemini 키 등)가 테스트 결과를 바꾸면 "내 머신에선 통과"가
생긴다. app import 보다 먼저 신호를 세워, Settings 클래스가 평가되는 시점에
.env 로딩이 이미 꺼져 있게 한다.

키를 하나씩 덮어쓰는 대신 로딩 자체를 끊는 이유: 설정 키가 늘어도 따라 고칠 필요가
없고, 테스트가 "코드 기본값 + 그 테스트가 명시한 값"만 보게 되기 때문.
"""

import os

os.environ.setdefault("PYTEST_CURRENT_TEST", "1")
