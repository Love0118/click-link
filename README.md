# ClickLink

Paper 채팅 URL을 클릭 가능한 링크로 표시하고, 사이트 제목을 가져올 수 있으면 [제목]으로 보여주는 플러그인입니다. KAKC 1.2와 함께 사용할 때 URL만 한글 변환에서 제외합니다.

기본 스타일은 &6[&a링크내용&6]&f입니다. 대괄호는 금색, 제목 또는 URL은 초록색이며 볼드·밑줄 없이 표시합니다. 링크 뒤 텍스트는 흰색으로 표시합니다. 기존 설정 파일이 있다면 link.color를 #55FF55로 바꾸고 /clicklink reload를 실행하세요.

입력 예:

    https://www.youtube.com/watch?v=pM8_wnJ7JsE dkssudgktpdy

제목 조회 성공 + KAKC 한글 변환 모드의 출력 예:

    [Overdose (なとり) / 아오쿠모 린 (Aokumo Rin) Cover] 안녕하세요

제목에 마우스를 올리면 원본 URL이 표시되며, 클릭하면 Minecraft의 링크 열기 동작을 실행합니다. 클라이언트의 링크 허용·확인 설정은 그대로 적용됩니다.

## 설치

1. 빌드 결과인 target/ClickLink.jar를 Paper 서버의 plugins 폴더에 넣습니다.
2. KAKC를 사용한다면 기존 kakc.jar도 그대로 유지합니다.
3. 서버를 재시작합니다. 설정 파일은 plugins/ClickLink/config.yml에 생성됩니다.
4. 설정 변경 후 /clicklink reload를 실행합니다. 권한은 clicklink.admin이며 기본값은 OP입니다.

## 호환성

- 대상: Paper 1.21.8–26.3. 기준 API는 1.21.8이며, Java 21 바이트코드로 빌드합니다. 실제 서버가 요구하는 Java 버전을 사용하세요.
- Paper 1.21.8-R0.1-SNAPSHOT 및 26.3.build.28-alpha API에서 컴파일·자동 테스트를 확인했습니다. 전체 버전의 실제 서버 접속 테스트를 의미하지 않습니다.
- 서버 버전을 확인해서 사용을 거부하는 코드는 없으며 상한 제한도 없습니다. plugin.yml의 api-version은 1.21입니다. 이보다 오래된 서버는 Paper 자체의 API 호환성 검사로 로딩이 거부될 수 있습니다.
- NMS나 CraftBukkit 내부 클래스에 의존하지 않습니다.
- KAKC 연동은 제공된 KAKC 1.2의 me.desktop.KAKC.Main 구현을 대상으로 합니다. 다른 구현은 경고 후 연동을 생략합니다.

## 처리 방식과 제한

- HTTP/HTTPS URL을 지원합니다. 여러 링크, 쿼리 문자열, 괄호와 문장 끝 구두점을 처리합니다.
- YouTube는 oEmbed를 우선 조회하고, 일반 페이지는 og:title → twitter:title → HTML title 순서로 조회합니다. API 키가 필요하지 않습니다.
- 비동기 채팅에서 기본 최대 1.5초 동안 제목을 기다립니다. 시간 초과·접속 실패·미지원 페이지는 원래 URL을 클릭 가능하게 표시합니다. 이미 전송된 메시지는 나중에 수정하지 않습니다.
- 제목 조회는 별도 작업 스레드에서 수행하고 결과를 기본 60분, 최대 512개 캐시합니다. 동기 채팅에서는 네트워크 조회를 기다리지 않습니다.
- 로그인·봇 차단·JavaScript 실행이 필요한 사이트는 제목을 얻지 못하거나 사이트가 제공하는 일반 제목을 표시할 수 있습니다.
- 요청·응답 크기, 동시 요청, 대기열, 리디렉션 횟수에 제한이 있습니다. 로컬·사설 주소 및 80/443 이외 포트는 미리보기에서 제외합니다. 공개 URL만 허용하더라도 외부 네트워크 정책은 서버 관리자가 별도로 관리해야 합니다.
- KAKC 핸들러를 호출하는 동안만 URL을 임시 치환한 뒤 즉시 복원합니다. 사용자 변환 모드와 한/영 전환키는 KAKC가 그대로 처리하며, 명령어 처리는 변경하지 않습니다. KAKC가 없는 경우 영문 자판 텍스트를 한글로 바꾸지 않습니다.
- 기존 채팅 렌더러 결과에 링크를 추가하므로 접두사·표시 이름·수신자 및 취소 상태를 유지합니다. 이후 다른 플러그인이 렌더러를 완전히 교체하면 링크가 제거될 수 있습니다.

## 설정

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| enabled | true | 플러그인 기능 사용 |
| preview.enabled | true | 웹페이지 제목 조회 |
| preview.wait-millis | 1500 | 비동기 채팅의 최대 제목 대기 시간 |
| preview.timeout-millis | 3000 | 개별 HTTP 요청 제한 시간 |
| preview.cache-minutes | 60 | 캐시 유지 시간 |
| preview.max-cache-entries | 512 | 캐시 최대 항목 수 |
| link.color | #55FF55 | 대괄호 안 링크 내용 색상 |
| link.max-links | 5 | 메시지당 링크 표시·조회 상한 |
| kakc.protect-urls | true | KAKC URL 보호 |

## 빌드 및 검증

    mvn clean package

공급받은 KAKC 바이너리로 연동 테스트까지 실행:

    mvn clean package "-Dkakc.jar=E:/mc luck defense/plugins/kakc.jar"

KAKC 바이너리는 저장소나 배포 JAR에 포함하지 않습니다. 경로를 지정하지 않으면 해당 연동 테스트만 건너뜁니다.

다른 Paper API로 호환성을 확인할 때:

    mvn clean test -Dpaper.version=26.3.build.28-alpha

배포 JAR은 기본 Paper 1.21.8 API로 다시 빌드하세요.
