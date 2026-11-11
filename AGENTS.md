# 응답 규칙
- 별도 요청이 없으면 한국어로 쉽고 짧게 답한다.

# 현재 세션 자동 기록
- 세션 `01a1c44e-6036-7f30-81ce-3f42ce6fbac7`의 로깅 프로그램·설정·기록은 루트의 `session-logging/`에 있다. 실제 설정은 `session-logging/config.json`이며, 다른 세션에 자동으로 확대하지 않는다.
- 세션 원본과 macOS 파일 이벤트를 독립 수집기가 기록한다. 로그를 응답으로 출력하거나 외부로 업로드하지 않는다.
- 이 세션에서 작업을 시작할 때 `python3 session-logging/logger.py --status`로 수집기가 동작하는지 확인한다. 사용법은 `session-logging/README.md`에 있다.
- 작업 폴더 밖의 일반 작업 파일을 수정해야 하면 먼저 `config.json`의 `watch_roots`에 필요한 최소 경로를 추가하고 수집기를 재시작한다. 수집기 자신의 `logs/`, `.state/`, `__pycache__/`는 자동 제외한다.
- 파일 이벤트만으로 AI가 변경했다고 단정하지 않는다. Codex 명령·FileChange 기록과 함께 확인한다.
