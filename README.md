# BE-VITA

Kubernetes 클러스터 모니터링 및 관리 백엔드 애플리케이션

## 개요

BE-VITA는 Kubernetes 클러스터의 실시간 모니터링, 로그 분석, 그리고 클러스터 관리를 위한 종합적인 백엔드 시스템입니다. Google Gemini AI를 활용한 로그 분석과 Prometheus/Loki를 통한 메트릭 수집을 제공합니다.


## 기술 스택

- **Java 21**
- **Spring Boot 3.5.3**
- **Spring WebFlux** (비동기 처리)
- **Gradle** 빌드 시스템
- **Fabric8 Kubernetes Client** (K8s API 연동)
- **Docker** 컨테이너화

## 설치 및 실행

### 1. 사전 요구사항

- Java 21 이상
- Gradle 8.0 이상
- Kubernetes 클러스터 접근 권한
- Loki 서버
- Prometheus 서버
- Google Gemini API 키

### 2. 빌드

```bash
# 프로젝트 클론
git clone https://github.com/OpsNow-Project/BE-VITA.git
cd BE-VITA

# Gradle 빌드
./gradlew build
```

### 3. 환경변수 설정

애플리케이션 실행 전 다음 환경변수를 설정해야 합니다:

```bash
# Loki 서버 URL
export LOKI_BASE_URL=http://your-loki-server:3100

# Prometheus 서버 URL
export PROMETHEUS_BASE_URL=http://your-prometheus-server:9090

# Google Gemini API 키
export GEMINI_API_KEY=your-gemini-api-key

# Kubernetes 설정 파일 경로 (선택사항)
export KUBECONFIG=/path/to/your/kubeconfig
```

### 4. 실행

#### 로컬 실행
```bash
./gradlew bootRun
```

#### Docker 실행
```bash
# Docker 이미지 빌드
docker build -t vita .

# Docker 컨테이너 실행
docker run -p 8080:8080 \
  -e LOKI_BASE_URL=http://your-loki-server:3100 \
  -e PROMETHEUS_BASE_URL=http://your-prometheus-server:9090 \
  -e GEMINI_API_KEY=your-gemini-api-key \
  -e KUBECONFIG=/path/to/your/kubeconfig \
  vita
```
