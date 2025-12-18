# Jasypt DB 암호화 설정 가이드

이 문서는 Jasypt를 사용한 DB 정보 암호화 및 환경변수 설정 방법을 설명합니다.

## 1. 비밀번호 암호화하기

### 방법 1: 빌드 후 유틸리티 클래스 사용

```bash
# 프로젝트 빌드
./gradlew build -x test

# 암호화 실행 (Linux/Mac)
java -cp "build/libs/*:build/classes/java/main" \
  com.gims.module.dbsync.util.JasyptEncryptorUtil encrypt [암호화키] [평문비밀번호]

# 암호화 실행 (Windows)
java -cp "build\libs\*;build\classes\java\main" ^
  com.gims.module.dbsync.util.JasyptEncryptorUtil encrypt [암호화키] [평문비밀번호]
```

**예시:**
```bash
# 암호화키: mySecretKey123, 비밀번호: 1111
java -cp "build/libs/*:build/classes/java/main" \
  com.gims.module.dbsync.util.JasyptEncryptorUtil encrypt mySecretKey123 1111

# 출력 예시:
# ===========================================
# 원본: 1111
# 암호화된 값: abc123XYZ...==
# properties 설정값: ENC(abc123XYZ...==)
# ===========================================
```

### 방법 2: Jasypt CLI 다운로드 사용

```bash
# Jasypt CLI 다운로드
wget https://github.com/jasypt/jasypt/releases/download/jasypt-1.9.3/jasypt-1.9.3-dist.zip
unzip jasypt-1.9.3-dist.zip

# 암호화
cd jasypt-1.9.3/bin
./encrypt.sh input="1111" password="mySecretKey123" algorithm="PBEWITHHMACSHA512ANDAES_256" ivGeneratorClassName="org.jasypt.iv.RandomIvGenerator"
```

## 2. application.properties 설정

암호화된 값을 `ENC()`로 감싸서 설정합니다:

```properties
spring.datasource.source.username=ENC(암호화된값)
spring.datasource.source.password=ENC(암호화된값)
spring.datasource.target.username=ENC(암호화된값)
spring.datasource.target.password=ENC(암호화된값)
```

## 3. 서버 환경변수 설정

### Linux/Mac

```bash
# 방법 1: 환경변수 직접 설정
export JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123

# 방법 2: /etc/environment에 추가 (영구 설정)
echo 'JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123' | sudo tee -a /etc/environment

# 방법 3: systemd 서비스 파일에 추가
# /etc/systemd/system/myapp.service
[Service]
Environment="JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123"
ExecStart=/usr/bin/java -jar /path/to/app.jar
```

### Windows

```cmd
# 방법 1: 현재 세션에만 설정
set JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123

# 방법 2: 영구 설정 (시스템 환경변수)
setx JASYPT_ENCRYPTOR_PASSWORD "mySecretKey123" /M

# 또는 시스템 속성 > 환경 변수에서 직접 추가
```

### Docker

```dockerfile
# Dockerfile
ENV JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123

# 또는 docker run 시
docker run -e JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123 myapp
```

```yaml
# docker-compose.yml
services:
  app:
    environment:
      - JASYPT_ENCRYPTOR_PASSWORD=mySecretKey123
    # 또는 .env 파일 사용
    env_file:
      - .env
```

### Kubernetes

```yaml
# Secret 생성
apiVersion: v1
kind: Secret
metadata:
  name: jasypt-secret
type: Opaque
data:
  JASYPT_ENCRYPTOR_PASSWORD: bXlTZWNyZXRLZXkxMjM=  # base64 encoded

---
# Deployment에서 사용
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: app
          env:
            - name: JASYPT_ENCRYPTOR_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: jasypt-secret
                  key: JASYPT_ENCRYPTOR_PASSWORD
```

## 4. 애플리케이션 실행

### 운영 환경 (암호화 사용)

```bash
# 환경변수가 설정된 상태에서 실행
java -jar app.jar

# 또는 시스템 프로퍼티로 전달
java -Djasypt.encryptor.password=mySecretKey123 -jar app.jar
```

### 로컬 개발 환경 (암호화 없이)

```bash
# local 프로파일 사용 시 암호화 없이 평문 사용 가능
java -jar app.jar --spring.profiles.active=local

# Gradle 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 5. 보안 권장사항

1. **암호화 키 관리**
   - 암호화 키를 소스코드나 설정 파일에 절대 포함하지 마세요
   - 환경변수 또는 비밀 관리 도구(Vault, AWS Secrets Manager 등) 사용

2. **암호화된 값 관리**
   - 같은 평문이라도 암호화할 때마다 다른 결과가 생성됩니다 (Random Salt)
   - 암호화된 값은 버전 관리에 커밋해도 안전합니다

3. **로컬 개발**
   - `application-local.properties`는 `.gitignore`에 포함되어 있습니다
   - 로컬에서는 `--spring.profiles.active=local`로 실행

4. **키 교체**
   - 암호화 키를 변경할 경우, 모든 암호화된 값을 새 키로 다시 암호화해야 합니다

## 6. 트러블슈팅

### 오류: "JASYPT_ENCRYPTOR_PASSWORD 환경변수가 설정되지 않았습니다"
- 환경변수가 올바르게 설정되었는지 확인
- `echo $JASYPT_ENCRYPTOR_PASSWORD` (Linux) 또는 `echo %JASYPT_ENCRYPTOR_PASSWORD%` (Windows)

### 오류: "EncryptionOperationNotPossibleException"
- 암호화 키가 암호화할 때 사용한 키와 동일한지 확인
- 알고리즘 설정이 동일한지 확인

### 오류: "NoSuchAlgorithmException: PBEWITHHMACSHA512ANDAES_256"
- JDK 8u161 이상 또는 JCE Unlimited Strength 설치 필요
