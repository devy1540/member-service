# 현재 세션 자동 로깅

적용 세션: `01a1c44e-6036-7f30-81ce-3f42ce6fbac7`

설정일: 2026-11-10 (한국 시간)

현재 세션의 Codex 원본 기록을 처음부터 수집하고 이후 기록을 0.5초 간격으로 읽는다. 같은 세션을 재개하면서 추가로 생성된 원본 파일도 10초 간격으로 발견해 파일별 읽기 위치를 관리한다. 원본은 각 파일의 이벤트를 보존하고, 항목별 로그는 동일한 항목 ID의 재등장을 중복 기록하지 않는다. 작업 폴더의 파일 생성·수정·삭제·이름 변경은 macOS FSEvents로 수집한다. macOS LaunchAgent가 현재 로그인 환경에서 실행하며, 비정상 종료 후 재실행하고 다음 로그인 때도 같은 세션의 기록을 이어 간다. 별도 채팅은 자동 수집하지 않는다.

## 저장 위치

- 실제 설정: `/Users/hjyoon/Develop/workspace/member-service/session-logging/config.json`
- 수집기: 같은 폴더의 `logger.py`
- 실행 상태: 같은 폴더의 `.state/status.json`
- 로그: 같은 폴더의 `logs/`
- 로그인 실행 설정 원본: 같은 폴더의 `launch-agent.plist`
- macOS에 등록한 로그인 실행 설정: `/Users/hjyoon/Library/LaunchAgents/dev.local.codex-session-audit.01a1c44e-6036-7f30-81ce-3f42ce6fbac7.plist`

| 파일 | 내용 |
| --- | --- |
| `prompts.jsonl` | 사용자가 입력한 프롬프트, 시간, 턴 ID |
| `responses.jsonl` | 화면에 보이는 AI 중간 응답과 최종 응답 |
| `commands.jsonl` | 실행 명령, 작업 폴더, 종료 코드, 소요 시간, Codex에 기록된 출력 |
| `tools.jsonl` | 명령 외 도구 호출 및 결과, 코드 실행 입력 |
| `files.jsonl` | 생성·수정·삭제·이름 변경 이벤트, 전후 상태, 지원되는 텍스트 diff |
| `raw-session.jsonl` | Codex 원본 이벤트 전체. 기본 지침 등 대화 외 메타데이터도 포함 |
| `snapshots/` | 관측한 2 MiB 이하 일반 파일의 내용, SHA-256으로 중복 제거 |
| `audit.sqlite3` | 재시작 시 누락·중복을 방지하는 원본 데이터베이스 |
| `collector.jsonl` | 수집기 시작·종료·오류·재스캔 기록 |

로그는 로컬에만 저장하고 별도 전송은 하지 않는다. 로그 폴더 권한은 0700, 수집기가 만드는 파일은 0600이다. 프롬프트·명령·파일에 입력된 민감한 내용도 기록되므로 로그 공유 시 직접 확인한다. 이 설정은 기존 전역 훅이나 다른 서비스의 수집 동작을 변경하지 않는다.

### 제출용 내보내기

`python3 session-logging/export_submission.py`는 모든 종류의 전체 이벤트를 루트 `ai-logs/`에 내보낸다. 로컬 계정·이메일·인증정보 형식을 마스킹하며, 수집 로그에 이미 적용된 회사명 마스킹도 유지한다. 실제 Codex 원본 대화는 수정하지 않는다. 제출본은 무편집 원본이 아닌 마스킹된 전체 기록이다.

`python3 session-logging/export_submission.py --check`로 JSON 구문, 이벤트 수, SHA-256을 검증한다. 수집기는 계속 운영용 로그를 기록하고, 제출본 갱신은 위 명령을 실행할 때만 이루어진다. 운영용 DB·스냅샷 캐시는 복제하지 않으며 파일 이벤트와 diff는 전부 내보낸다. 제출본을 다시 수집하지 않도록 `ai-logs/`를 `exclude_paths`에 추가했다. 자세한 구성은 [제출 기록 안내](../ai-logs/README.md)를 참고한다.

## 상태 확인

```sh
python3 /Users/hjyoon/Develop/workspace/member-service/session-logging/logger.py --status
```

`status: running`, `process_alive: true`, `filesystem_watcher_alive: true`, `last_error: null`을 확인한다. 하트비트가 15초 이상 오래되면 상태 명령은 실패 코드로 종료한다. 파일 이벤트 시간은 실제 시스템 호출 시각이 아닌 관측 시각이며 로그 시간은 UTC ISO 8601이다.

## 재시작 및 중지

```sh
# 설정을 바꾼 뒤 재시작. 로그는 유지한다.
launchctl kickstart -k gui/$(id -u)/dev.local.codex-session-audit.01a1c44e-6036-7f30-81ce-3f42ce6fbac7

# 현재 실행 중지. 이 상태로는 다음 로그인 시 다시 실행된다.
launchctl bootout gui/$(id -u)/dev.local.codex-session-audit.01a1c44e-6036-7f30-81ce-3f42ce6fbac7

# 중지한 수집기를 다시 등록하고 시작한다.
launchctl bootstrap gui/$(id -u) /Users/hjyoon/Library/LaunchAgents/dev.local.codex-session-audit.01a1c44e-6036-7f30-81ce-3f42ce6fbac7.plist
```

계속 중지하려면 실제 `config.json`의 `enabled`를 `false`로 저장한 뒤 `bootout`한다. 다시 켤 때는 `true`로 바꾸고 `bootstrap`한다. JSONL은 SQLite로부터 생성되므로 직접 수정하지 않는다. 로그 보관 기한이나 자동 삭제는 설정하지 않았다.

## 기록 범위와 한계

- 대화·명령·도구 기록은 이 세션의 기존 원본부터 수집한다. 파일 내용의 전후 상태는 수집기 설치 시점부터 확보한다.
- 현재 파일 감시 범위는 `/Users/hjyoon/Develop/workspace/member-service`이며 숨김 파일도 포함한다. 수집기 자신의 `session-logging/logs/`, `session-logging/.state/`, `session-logging/__pycache__/`와 제출본 `ai-logs/`는 제외한다. 심볼릭 링크의 외부 대상은 따라가지 않는다.
- 작업 폴더 밖의 Codex `FileChange` 기록도 원본에 있으면 수집한다. 외부 경로를 셸 등으로 수정할 때는 **수정 전에** 실제 설정의 `watch_roots`에 해당 폴더를 추가하고 재시작해야 한다. 로그와 실행 상태 폴더는 감시 범위 안에 있어도 자동 제외한다. 다른 제외 경로는 `exclude_paths`로 설정한다.
- 다른 프로그램이나 사람이 감시 폴더를 수정한 이벤트도 포함된다. 파일 이벤트의 `actor`는 `unknown`, Codex가 제공한 `FileChange`만 `codex`이다.
- macOS는 짧은 시간의 이벤트를 합칠 수 있다. 빠르게 생성·삭제한 파일은 이벤트 플래그만 남을 수 있으며, 매번의 중간 내용 복원을 보장하지 않는다. 프로세스가 꺼져 있는 동안의 내용 변경도 중간 버전까지 복원할 수 없다.
- 내용 스냅샷은 일반 파일 최대 2 MiB까지 저장한다. 그보다 큰 파일은 경로·크기·시간 등 메타데이터를 남긴다. 바이너리는 내용 스냅샷을 저장할 수 있지만 텍스트 diff는 생성하지 않는다.
- 명령 출력은 Codex 원본에 기록된 범위만 보존한다. Codex가 자른 출력은 복원할 수 없다. 원격 서버 파일이나 웹 서비스 변경은 해당 도구 기록에 남는 범위만 수집한다.
- Codex 원본 형식은 고정된 공개 API가 아니다. 앱 업데이트 후 정상 추출 여부를 확인해야 한다. 관련 공식 안내: https://learn.chatgpt.com/docs/hooks#common-input-fields

## 프로젝트 루트 배치

로깅 기능과 기존 기록은 모두 프로젝트 루트의 `session-logging/` 안에 있다. `config.json`이 실제 설정이며 `logger.py`가 실행 프로그램이다. `logs/`와 `.state/`는 Git에서 제외한다. macOS 자동 시작을 위한 plist 복사본만 사용자 `Library/LaunchAgents`에 등록한다.

이전 로그에 저장된 스냅샷 경로가 계속 열리도록 `~/.codex/session-audit/01a1c44e-6036-7f30-81ce-3f42ce6fbac7`에는 새 폴더를 가리키는 호환 심볼릭 링크만 남겼다. 기존 로그를 중복 보관하지 않는다.
