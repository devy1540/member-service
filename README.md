# Member & Group Services

멤버와 그룹 조회를 제공하는 Kotlin·Spring Boot 서비스입니다. Gradle 멀티모듈로 구성하며, 두 서버는 독립 프로세스와 별도 H2 데이터베이스로 실행됩니다.

## 실행

**JDK 25**가 필요합니다. Kotlin 2.4.21, Spring Boot 4.1.2, Gradle Wrapper 9.7.0을 사용하며 외부 DB 설치는 필요하지 않습니다.

```sh
# 두 서비스 빌드 및 테스트
./gradlew clean build
```

빌드 후 서로 다른 터미널에서 실행합니다.

```sh
# 터미널 1
./gradlew :group-service:bootRun

# 터미널 2
./gradlew :member-service:bootRun
```

| 서비스 | 포트 | Swagger | H2 콘솔 |
| --- | --- | --- | --- |
| 멤버 | 8080 | [API 문서](http://localhost:8080/swagger-ui.html) | [DB 콘솔](http://localhost:8080/h2-console/) |
| 그룹 | 8081 | [API 문서](http://localhost:8081/swagger-ui.html) | [DB 콘솔](http://localhost:8081/h2-console/) |

각 서버의 `/actuator/health`에서 상태를 확인할 수 있습니다. OpenAPI 3.1 명세는 `/v3/api-docs`와 `/v3/api-docs.yaml`로 제공합니다. IDE에서는 루트 Gradle 프로젝트를 불러온 뒤 각 모듈의 Application 클래스에 있는 `main`을 실행합니다.

## API

| 서비스 | 메서드·경로 | 입력 |
| --- | --- | --- |
| 멤버 | `GET /api/members` | Query: `page`, `size`, `sort` |
| 멤버 | `GET /api/member/{memberId}` | Path: `memberId` |
| 그룹 | `GET /api/groups/summary` | Query: `memberIds` |
| 그룹 | `GET /api/group` | Query: `memberId` |

```sh
curl 'http://localhost:8080/api/members?page=0&size=2&sort=name,asc'
curl 'http://localhost:8080/api/member/ax1qwsd12s'
curl 'http://localhost:8081/api/groups/summary?memberIds=ax1qwsd12s,ca12fdfdv1'
curl 'http://localhost:8081/api/group?memberId=ax1qwsd12s'
```

- `page`는 필수이며 0부터 시작합니다. `size`는 기본 20, 허용 범위는 1~100입니다.
- 정렬은 `name,asc` 또는 `name,desc`만 허용합니다. 생략하면 DB 반환 순서를 사용합니다. 이름 정렬을 지정했을 때 같은 이름은 ID 오름차순으로 정렬합니다.
- 멤버 ID는 영문·숫자 1~32자입니다. `memberIds`는 쉼표로 구분한 ID를 1~100개 받습니다.
- 그룹 요약은 요청한 멤버가 속한 그룹의 **전체 멤버**를 반환합니다. `totalMemberCount`는 `members` 배열 길이와 같습니다.
- 미가입 멤버는 목록의 `group`·`permission`, 상세의 `group`이 `null`입니다. 그룹 단건 조회도 미가입이면 `200`과 JSON `null`을 반환합니다.
- 날짜는 UTC `yyyy-MM-dd HH:mm`입니다. 멤버 날짜는 생성 시각, 그룹 응답의 날짜는 해당 멤버의 그룹 가입 시각입니다.
- 멤버 API는 잘못된 입력에 `400`, 없는 멤버에 `404`, 그룹 서버 오류·시간 초과·잘못된 응답에 `502`를 반환합니다.

응답 예시: [멤버 목록](docs/examples/member-list.response.json) · [멤버 상세](docs/examples/member-detail.response.json) · [미가입 멤버](docs/examples/member-detail-without-group.response.json) · [그룹 요약](docs/examples/groups-summary.response.json) · [그룹 상세](docs/examples/group-detail.response.json)

## 설계

- **서비스 분리:** 모듈 간 직접 의존성이나 DB 공유 없이 HTTP API로 연동합니다. 그룹 서비스는 멤버 서비스가 호출하는 내부 조회 서비스입니다.
- **CQRS:** `application/query`와 `application/command`에 모델·핸들러·저장소 인터페이스를 분리합니다. 각 서비스 안에서는 같은 DB를 사용하며, 현재 Command는 샘플 데이터 초기화입니다.
- **조회 흐름:** 멤버 DB에서 정렬·페이징 후 트랜잭션을 종료하고, 해당 페이지의 ID를 그룹 서버에 한 번에 전달합니다. 응답에서 현재 페이지의 멤버만 연결해 순서를 유지합니다.
- **구현 분리:** 컨트롤러는 입력 검증과 응답 DTO 변환, 핸들러는 조회 조합, `infrastructure`는 Exposed 저장소와 HTTP 호출을 담당합니다. 저장소·통신 구현을 바꿔도 조회 로직에 미치는 영향을 줄입니다.

## 데이터와 설정

| 서비스 | 테이블 | H2 콘솔 JDBC URL |
| --- | --- | --- |
| 멤버 | `member` | `jdbc:h2:mem:memberdb;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE` |
| 그룹 | `group`, `group_member` | `jdbc:h2:mem:groupdb;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE` |

H2 콘솔은 Driver `org.h2.Driver`, 사용자 `sa`, 빈 비밀번호로 접속합니다. 테이블명은 SQL에서 `"member"`, `"group"`, `"group_member"`처럼 큰따옴표로 감쌉니다.

Exposed가 시작 시 테이블을 만들고 초기화 Command가 샘플 데이터 **멤버 7명·그룹 3개·가입 관계 4개**를 생성합니다. 메모리 DB이므로 종료하면 데이터가 사라지고, 재시작하면 초기 상태로 돌아갑니다. `ev4nmn33ea`는 미가입 멤버 조회에 사용할 수 있습니다.

`group_member.member_id`의 UNIQUE 제약으로 한 멤버가 최대 한 그룹에만 속하도록 제한합니다. `group_id`는 그룹 테이블의 외래 키입니다. 과금 타입은 `Free`·`Advanced`·`Premium`, 권한은 `Viewer`·`Editor`·`Admin`이며 enum과 DB CHECK 제약으로 검증합니다.

설정은 각 모듈의 `src/main/resources/application.yaml`에서 관리합니다.

| 환경 변수 | 용도·기본값 |
| --- | --- |
| `SERVER_PORT` | 서비스 포트 변경. 기본 멤버 8080, 그룹 8081 |
| `GROUP_SERVICE_BASE_URL` | 멤버 서버가 호출할 그룹 주소. 기본 `http://localhost:8081` |
| `APP_SAMPLE_DATA_ENABLED` | 샘플 데이터 자동 적재. 기본 `true` |
| `LOG_DIR` | 애플리케이션 로그 폴더. 기본 `logs` |

그룹 서버의 포트를 바꾸면 멤버 서버의 `GROUP_SERVICE_BASE_URL`도 맞춰야 합니다. HTTP 연결 제한 시간은 2초, 읽기 제한 시간은 3초이며 YAML에서 조정할 수 있습니다.

## 테스트와 로그

빌드 시 정렬·페이징·응답 형식·입력 검증·그룹 서버 실패·DB 제약·트랜잭션 롤백·초기 데이터 생명주기를 테스트합니다. 보고서는 각 모듈의 `build/reports/tests/test/index.html`에 생성됩니다.

애플리케이션 로그는 콘솔에 텍스트, `logs/{서비스명}.jsonl`에 ECS JSON Lines로 기록합니다. `X-Request-Id`로 두 서버의 HTTP·CQRS 로그를 연결하며 처리 시간·결과 건수·실패 원인을 남깁니다. 파일은 10 MB 단위로 회전하고 최대 7일·100 MB까지 보관합니다. [로그 예시](docs/examples/cqrs-log-events.json)

## AI 활용 기록

AI 도구는 **Codex**를 사용했습니다. 독립 Python 수집기가 지정 개발 세션의 프롬프트·응답·명령·도구 실행과 macOS 파일 변경 이벤트를 자동 기록합니다. [수집 프로그램·설정 안내](session-logging/README.md)와 [제출 기록](ai-logs/README.md)을 저장소에 포함합니다.

```sh
python3 session-logging/export_submission.py
python3 session-logging/export_submission.py --check
```
