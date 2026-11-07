# 협업 전략

## Issue

기능 단위로 Issue를 생성합니다. Issue 생성 버튼을 누르면 템플릿(Feature, Bug, Refactor)이 등록되어 있으니 활용해 주세요.

## Git branch

**feat → develop → main**

develop 아래로 feat 브랜치를 만들어서 작업해 주세요.

| Branch | Description |
| --- | --- |
| main | 모든 오류가 수정되어 배포 가능 (최상단) |
| develop | 다음 배포를 위해 준비함 |
| feat | 기능 개발 |

브랜치 이름은 `feat/#이슈번호` 형식을 사용합니다.

```
feat/#1
```

## Commit Message

커밋 메시지에는 `Tag` + `구현한 기능` + `(#이슈번호)`를 사용해 주세요.

```
feat: 회원가입 기능 구현 (#1)
```

| Tag | Description |
| --- | --- |
| feat: | 새 기능 추가 구현했을 때 |
| fix: | 버그 수정했을 때 |
| docs: | 문서 관련 수정했을 때 |
| refactor: | 코드 리펙토링했을 때 |
| build: | 빌드 관련 파일 수정했을 때 |
| ci: | CI 설정 관련 파일 수정했을 때 |
| chore: | 그 외 자잘한 수정을 했을 때 |
| remove | 파일 삭제만 했을 때 |

## Pull Request

제목은 `[Tag] 작업 내용` 형식으로 작성합니다.

```
[Feature] 사용자 회원가입 기능 구현
```

| Tag | Description |
| --- | --- |
| Feature | 새 기능을 추가했을 때 |
| Bug | 버그를 수정했을 때 |
| Refactor | 리펙토링 했을 때 |

본문은 PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)을 따릅니다.

## Label

이슈와 PR 오른쪽 **Labels**에서 작업 종류에 맞는 라벨을 붙여 주세요. 라벨 이름은 커밋 태그와 같습니다.

| Label | Description |
| --- | --- |
| feat | 새 기능 추가 |
| fix | 버그 수정 |
| refactor | 코드 리팩토링 |
| docs | 문서 수정 |
| build | 빌드 관련 파일 수정 |
| ci | CI 설정 수정 |
| chore | 그 외 자잘한 수정 |
| remove | 파일 삭제 |

이슈 템플릿으로 만든 이슈는 라벨이 자동으로 붙습니다 (Feature → `feat`, Bug → `fix`, Refactor → `refactor`). PR은 템플릿으로 라벨을 붙일 수 없어서 직접 선택합니다.

## Swagger 태그

컨트롤러에 `@Tag`를 달아 Swagger에서 API를 명세서 도메인별로 묶습니다. 태그 이름은 API 명세서의 **도메인** 열과 똑같이 씁니다. 같은 도메인의 컨트롤러가 여러 개면 같은 이름과 설명을 씁니다.

```java
@Tag(name = "지원 관리", description = "지원 항목 사전, 공통 지원 그룹, 개별 케어")
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
```

| 태그 이름 | 명세서 도메인 |
| --- | --- |
| 인증과 기관 | 기관 가입, 로그인, 토큰, 내 기관 |
| 지원 관리 | 지원 항목, 그룹, 개별 케어 |
| 프로그램 | 프로그램 탐색, 상세, 판정, 장소 |
| 신청 | 바로 신청, 내 신청, 문의 |
| 행사 관리 | 행사 등록, 사진, 신청 기관 관리 |
| 후기 | 후기 쓰기, 목록, 답글 |
| 알림 | 알림 목록, 실시간 알림 |
| 지도와 통계 | 사각지대, 지도 표시점 |

## 공통 응답 포맷

### 성공 시

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

### 실패 시

```json
{
  "success": false,
  "data": null,
  "error": { "code": "PRODUCT_NOT_FOUND", "message": "해당 제품을 찾을 수 없습니다." }
}
```

### 공통 에러 코드

| HTTP Status | code | 설명 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청 파라미터 오류 |
| 401 | `UNAUTHORIZED` | 인증 실패/토큰 만료 |
| 403 | `FORBIDDEN` | 타인의 리소스 접근 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 422 | `OCR_EXTRACTION_FAILED` | 성분표 인식 실패 |
| 429 | `TOO_MANY_REQUESTS` | 요청 한도 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

도메인별 에러 코드는 `ErrorCode` enum에 `대상_상황` 형식(예: `USER_NOT_FOUND`, `EMAIL_DUPLICATION`)으로 추가합니다. enum 상수 이름이 그대로 응답의 `code`가 됩니다.
