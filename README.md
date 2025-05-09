# 🎫 Taxi Driver Client

**WebSocket 기반 실시간 택시 기사 클라이언트 애플리케이션**

 **순수 Java**로 구현되었으며, WebSocket + STOMP 프로토콜 기반으로 서버와 통신하고, JWT 기반 인증을 수행합니다. 실시간 콜 수신, 자동 승차/하차, 운행 상태 보고 등 주요 기능을 제공합니다.

---

## 👨‍👩‍👧‍👦 개발 이유

* 목적: 서버와의 통신, 비동기 처리, 실시간 통신

---

## 🧰 기술 스택

###  (Client)

* Java 21
* RestTeamplate
* WebSocket / STOMP

### 🔧 인증 & 통신

* RestTemplate 기반 로그인 요청
* WebSocket 연결 시 헤더로 토큰 포함

---

## 🗺️ 시스템 구조 요약

```text
[ RestTemplate 로그인 ] → [ JWT 발급 ]
        ↓
[ WebSocket 연결 (STOMP + JWT) ]
        ↓
[ 콜 수신 구독 /user/queue/call-request ]
        ↓
[ 수락 요청 전송 /app/taxi-driver/accept-call ]
        ↓
[ 승차 시작 /app/taxi-driver/ride-start → 5초 후 ]
        ↓
[ 하차 완료 /app/taxi-driver/ride-finish → 15초 후 ]
        ↓
[ 운행 응답 수신 /user/queue/ride-status ]
```

---

## 🧪 사용 예시

```bash
# 실행
java -jar 
```
* 로그인 정보 및 서버 주소 입력
* 자동으로 로그인 후 WebSocket 연결
* 수신된 콜을 자동 수락하고, 일정 시간 간격으로 승차/하차 요청 전송
* 운행 종료 후 상태 자동 전환: `OFF_DUTY → AVAILABLE`

---

## 📄 폴더 구조 요약

```
📁 application
├─ ClientService.java       # WebSocket 연결 및 메시지 처리
├─ TaxiDriverRunner.java    # main() 실행 클래스
├─ RestTemplateToken.java   # JWT 발급 처리

📁 domain.model
├─ CallAcceptRequest.java
├─ DrivingInfoResponse.java
├─ DrivingSummaryResponse.java
└─ TaxiDriverStatus.java    # Enum (AVAILABLE, RESERVED, DRIVING, OFF_DUTY)
```

---

## 🪛 테스트 및 트러블슈팅

* WebSocket 연결 실패 → 다시 재연결 시도
* 수락 실패 → 이미 다른 기사에게 선점된 콜일 수 있음
* 메시지 수신 누락 → `/user/queue/*` 구독 경로와 메시지 전송 경로 확인 필요

---

## 🧠 배운 점

* STOMP 프로토콜을 통한 실시간 통신 흐름 이해
* 순수 Java 기반 WebSocket 클라이언트 구성 경험
* 비동기 흐름에서 상태 전이 타이밍 조절의 중요성 체득
* 멀티 스레드 순서화

---

## 🗂️ 향후 개선점

* 콜 수락/거절 UI와 연동된 시뮬레이션 도구 추가
* 메시지 누락 대비 재전송 로직 도입
* 상태 로그 저장 및 시각화

---
