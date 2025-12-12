# DB Sync Sample Module

독립 실행 가능한 DB 동기화 모듈 예제입니다.

## 📋 개요

이 모듈은 DB Sync Manager와 API로 통신하며, DB 동기화 작업을 수행하고 단계별 진행 상황을 실시간으로 보고합니다.

## 🏗️ 아키텍처

```
┌─────────────────────────┐         HTTP API          ┌─────────────────────────┐
│  DB Sync Manager        │ ◄──────────────────────► │  Sample Module          │
│  (관리 시스템)           │                           │  (독립 프로젝트)         │
│                         │                           │                         │
│  - 스케줄 관리           │  1. 실행 요청 (POST)      │  - 실행 엔드포인트       │
│  - 모듈 호출             │  ──────────────────►     │  - DB Sync 로직         │
│  - 상태 수신 API         │                           │  - 상태 보고 클라이언트  │
│  - 모니터링 대시보드      │  2. 진행 상황 보고 (POST) │                         │
│                         │  ◄──────────────────     │                         │
│                         │  3. 완료 보고 (POST)      │                         │
│                         │  ◄──────────────────     │                         │
└─────────────────────────┘                           └─────────────────────────┘
```

## 🚀 실행 방법

### 1. 빌드

```bash
./gradlew build
```

### 2. 실행

```bash
./gradlew bootRun
```

또는

```bash
java -jar build/libs/dbsync-sample-module-1.0.0.jar
```

### 3. 실행 확인

모듈이 시작되면 포트 8090에서 대기합니다:
- 실행 엔드포인트: http://localhost:8090/api/module/execute
- 헬스체크: http://localhost:8090/actuator/health
- 상태 조회: http://localhost:8090/api/module/status

## 📡 API 명세

### 실행 요청 (관리 시스템 → 모듈)

**POST** `/api/module/execute`

```json
{
  "execId": 123,
  "moduleId": "sampleDbSync",
  "configJson": "{...}",
  "callbackUrl": "http://localhost:8085/syncmanager/api/module-callback"
}
```

### 진행 상황 보고 (모듈 → 관리 시스템)

**POST** `{callbackUrl}/execution/progress`

```json
{
  "execId": 123,
  "moduleId": "sampleDbSync",
  "currentStep": "데이터 조회",
  "progressPercent": 50,
  "processedCount": 500,
  "totalCount": 1000,
  "message": "처리 중...",
  "logLevel": "INFO"
}
```

### 실행 완료 보고 (모듈 → 관리 시스템)

**POST** `{callbackUrl}/execution/complete`

```json
{
  "execId": 123,
  "moduleId": "sampleDbSync",
  "success": true,
  "processedCount": 1000,
  "errorCount": 0,
  "resultMessage": "동기화 완료",
  "errorMessage": null,
  "executionTimeMs": 15000
}
```

## 🔄 동기화 프로세스

모듈은 다음 단계로 DB 동기화를 수행합니다:

1. **데이터 조회** (10-20%): Source DB에서 데이터 조회
2. **데이터 검증** (30-40%): 유효성 검증 및 필터링
3. **데이터 변환** (50-60%): Target 스키마로 변환
4. **데이터 저장** (70-90%): Target DB에 배치 저장
5. **최종 검증** (95-100%): 동기화 결과 검증

각 단계마다 관리 시스템에 진행 상황을 보고하며, 오류 발생 시 즉시 보고합니다.

## ⚙️ 설정

`src/main/resources/application.properties`:

```properties
# 모듈 정보
module.id=sampleDbSync
module.version=1.0.0

# 관리 시스템 콜백 URL
manager.callback.base-url=http://localhost:8085/syncmanager/api/module-callback

# Source DB
spring.datasource.source.url=jdbc:h2:mem:sourcedb
spring.datasource.source.username=sa
spring.datasource.source.password=

# Target DB
spring.datasource.target.url=jdbc:h2:mem:targetdb
spring.datasource.target.username=sa
spring.datasource.target.password=
```

## 🧪 테스트

### 수동 실행 테스트

```bash
curl -X POST http://localhost:8090/api/module/execute \
  -H "Content-Type: application/json" \
  -d '{
    "moduleId": "sampleDbSync",
    "configJson": "{}"
  }'
```

### 관리 시스템에서 실행

1. 관리 시스템 실행: `cd ../dbsync-manager && ./gradlew bootRun`
2. DB에 모듈 등록:

```sql
INSERT INTO SYNC_MODULE_TB (
  MODULE_ID, MODULE_NAME, MODULE_TYPE, MODULE_URL,
  USE_YN, SCHEDULE_CRON
) VALUES (
  'sampleDbSync',
  'Sample DB Sync Module',
  'EXTERNAL',
  'http://localhost:8090/api/module/execute',
  'Y',
  '0 0 2 * * ?' -- 매일 새벽 2시
);
```

3. 모니터링 대시보드: http://localhost:8085/syncmanager/monitoring/dashboard

## 📊 모니터링

관리 시스템의 모니터링 대시보드에서 다음을 확인할 수 있습니다:

- 실행 이력
- 실시간 진행 상황
- 단계별 로그
- 에러 메시지
- 실행 통계

## 🔌 확장 방법

### 실제 DB 연결

`application.properties`에서 H2 대신 실제 DB로 변경:

```properties
spring.datasource.source.url=jdbc:oracle:thin:@localhost:1521:SOURCEDB
spring.datasource.source.driver-class-name=oracle.jdbc.OracleDriver
spring.datasource.source.username=your_user
spring.datasource.source.password=your_password
```

### DB Sync 로직 커스터마이징

`DbSyncService.java`의 각 메서드를 실제 비즈니스 로직으로 교체:

- `fetchSourceData()`: 실제 SELECT 쿼리
- `validateData()`: 비즈니스 룰 검증
- `transformData()`: 데이터 변환 로직
- `saveDataBatch()`: JDBC Batch Insert

## 📝 주의사항

- 모듈은 비동기로 실행되므로 요청 즉시 응답을 반환합니다
- 실제 결과는 콜백 API를 통해 관리 시스템에 전달됩니다
- 네트워크 장애 시 재시도 로직을 추가해야 합니다
- 프로덕션 환경에서는 보안(인증/암호화)을 추가해야 합니다

## 📚 의존성

- Spring Boot 2.7.14
- Spring Web (REST API)
- Spring JDBC (DB 접근)
- Lombok (코드 간소화)
- H2 Database (개발/테스트용)
