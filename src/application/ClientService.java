package application;

import com.fasterxml.jackson.databind.ObjectMapper;
import domain.model.*;

import java.lang.reflect.Type;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.*;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

public class ClientService {

    private StompSession stompSession;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 운행 중 여부
    private boolean driving = false;
    //손님 없으면 null로 보내기 위함
    private String currentCustomerLoginId = null;
    //콜 수락 시 저장
    private Long currentCallId = null;
    //위치 전송 플래그
    private boolean checkReceive = false;
    //큐 필드 추가 하나으 ㅣ쓰레드가 callqueue를 3초에 한번씩 조회 하나씩 다 검증 배차 됐나 안됐나 ㅋㅋ
    private final BlockingQueue<CallMessageResponse> callQueue = new LinkedBlockingQueue<>();
    private volatile boolean processingCall = false;
    //택시 상태 확인
    private volatile TaxiDriverStatus currentStatus = TaxiDriverStatus.AVAILABLE;

    //콜 처리 스레드 시작 됨?
    private boolean callConsumerStarted = false;

    //재연결 시도
    private String latestToken;


    public void connectWithToken(String jwtToken) {
        this.latestToken = jwtToken;

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", jwtToken);

        String url = "ws://localhost:8080/ws";

        stompClient.connectAsync(url, httpHeaders, stompHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                System.out.println("WebSocket connected: " + session.getSessionId());
                stompSession = session;

                //에러메세지 구독
                subscribeErrorMessages();

                //택시 상태 수신
                subscribeTaxiDriverStatus();

                //차 상태 설정 - 처음 켰을 때
                sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);

                //위치 전송 스케줄러 시작
                // startSendingLocation();

                //콜 요청 수신
                subscribeCallRequest();

                //콜 수락 결과 수신
                subscribeAcceptCallResult();

                //콜 거절 결과 수신
                subscribeRejectCallResult();

                //배차 되었는데 취소당함
                subscribeDispatchCanceled();

                //손님 정보
                subscribeRideStatus();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.out.println("WebSocket error: " + exception.getMessage());
                //재접속
                tryReconnect();
            }
        });

        System.out.println("Connecting to WebSocket server...");
    }

    //재접속 시도
    private void tryReconnect() {
        System.out.println("try reconnect");
        scheduler.schedule(() -> {
            try {
                checkReceive = false;
                callConsumerStarted = false;
                System.out.println("reconnecting now");
                connectWithToken(latestToken);
            } catch (Exception e) {
                System.out.println("reconnect fail : " + e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }

    //택시 상태 수신
    private void subscribeTaxiDriverStatus() {
        stompSession.subscribe("/user/queue/taxi-driver-status", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return UpdateTaxiDriverStatusResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof UpdateTaxiDriverStatusResponse response) {
                    TaxiDriverStatus status = response.getTaxiDriverStatus();
                    currentStatus = status;
                    if (status == TaxiDriverStatus.AVAILABLE) {
                        System.out.println("AVAILABLE");

                        //진심 개거슬리는부분 - start 함수에서 처리를 하는 게 좋을 거 같다 매번 검사를 하는 건 / 빼내자 실패 하면 종료할 수 있도록
                        //하나로 묶을 방법을 찾고싶음

                        if (!checkReceive) {
                            checkReceive = true;
                            startSendingLocation();
                        }


                        if (!callConsumerStarted) {
                            startCallQueueConsumer();
                            callConsumerStarted = true;
                        }

                    } else if (status == TaxiDriverStatus.RESERVED || status == TaxiDriverStatus.ON_DRIVING) {
                        System.out.println("RESERVED / ON_DRIVING");


                    } else if (status == TaxiDriverStatus.OFF_DUTY) {
                        System.out.println("OFF_DUTY");
                        //다시 checkReceive 키면 call 받
                        checkReceive = false;
                        // stopSendingLocation();
                    } else {
                        System.out.println("ERROR STATUS: " + status);

                    }
                }
            }
        });
    }


    //off - duty


    //콜 요청 수신
    private void subscribeCallRequest() {
        stompSession.subscribe("/user/queue/call", new StompFrameHandler() {

     //   stompSession.subscribe("/user/queue/call-request", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CallMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                //빈차 상태이냐?
                if (currentStatus != TaxiDriverStatus.AVAILABLE) {
                    System.out.println("Not ready");
                    return;
                }

                if (payload instanceof CallMessageResponse callRequest) {
                    System.out.println("Queue Call ID: " + callRequest.getCallId());
                    //콜 쌓음 (producer)
                    callQueue.offer(callRequest);
                }
            }
        });
    }

    //콜을 수락했을

    //콜 꺼내서 처리해야함 골 아픔
    private void startCallQueueConsumer() {
        new Thread(() -> {
            Random random = new Random();
            ExecutorService inputExecutor = Executors.newSingleThreadExecutor();
            while (true) {
                try {
                    //콜 큐에서 꺼내기(consumer)
                    //take
                    CallMessageResponse request = callQueue.take();



                    // 현재 콜 처리 시작
                    processingCall = true;

                    System.out.println("processing ID: " + request.getCallId());
                    System.out.println("Origin: " + request.getExpectedOrigin());
                    System.out.println("Dest: " + request.getExpectedDestination());

                    int decision = random.nextInt(10);
                    boolean accept = decision % 2 == 1;  // 홀수면 수락

                    System.out.println("random number : " + decision + " → " + (accept ? "accept" : "reject"));

                    //보통은 백엔드 개발자는 처리를 할 수 있다는 가정 하에 처리함

                    //자동거절
                    //client , server -> 기술적으로는 drop / reject 정책을 어떻게 할 지

                    //연결 실패 되거나 연결 되기 전인데 큐 스레드 돌게 될까봐
                    if(stompSession == null || !stompSession.isConnected()) {
                        System.out.println("seesion error");
                        processingCall = false;
                        continue;
                    }


                    if (accept) {
                        try {
                            CallAcceptRequest acceptRequest = new CallAcceptRequest();
                            currentCallId = request.getCallId();
                            acceptRequest.setCallId(request.getCallId());
                            stompSession.send("/app/taxi-driver/accept-call", acceptRequest);
                            System.out.println("Call accept");
                        } catch (Exception e) {
                            System.out.println("call requesting exception : " + e.getMessage());
                            processingCall = false;
                        }
                    } else {
                        try {
                            CallRejectRequest reject = new CallRejectRequest();
                            reject.setCallId(request.getCallId());
                            stompSession.send("/app/taxi-driver/reject-call", reject);
                            System.out.println("Call rejection");
                        } catch (Exception e) {
                            System.out.println("call reject exception : " + e.getMessage());
                        }
                        processingCall = false;
                    }

                } catch (InterruptedException e) {
                    System.out.println("Consumer thread closed");
                    break;
                }
            }
        }).start();
    }

    //콜 수락 결과 수신
    private void subscribeAcceptCallResult() {
        stompSession.subscribe("/user/queue/accept-call-result", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CallAcceptResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {

                if (payload instanceof CallAcceptResponse response) {

                    if (response.getCustomerLoginId() != null) {
                        currentCustomerLoginId = response.getCustomerLoginId();
                        driving = true;
                        //큐 비우기
                        callQueue.clear();
                        processingCall = false;

                        sendTaxiDriverStatus(TaxiDriverStatus.RESERVED);

                        //승차 요청
                        scheduler.schedule(() -> sendRideStart(currentCallId), 5, TimeUnit.SECONDS);

                        //하차 요청
                        scheduler.schedule(() -> sendRideFinish(currentCallId), 15, TimeUnit.SECONDS);


                    }
                } else {
                    //이미 누가 선점 해버림
                    System.out.println("accept fail : you lose");
                    processingCall = false;
                }
            }
        });
    }

    // 승차/하차 응답
    private void subscribeRideStatus() {
        stompSession.subscribe("/user/queue/ride-status", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                //DrivingInfoResponse, DrivingSummaryResponse
                return  Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof DrivingInfoResponse info && info.isDrivingStarted()) {
                    System.out.println("승차 완료됨");
                    currentStatus = TaxiDriverStatus.ON_DRIVING;

                } else if (payload instanceof DrivingSummaryResponse summary) {
                    if (summary.getDrivingStatus() == DrivingStatus.COMPLETE) {
                        System.out.println("하차 완료됨 (고객: " + summary.getCustomerLoginId() + ")");

                        // 내부 상태 초기화
                        currentCallId = null;
                        currentCustomerLoginId = null;
                        driving = false;

                        // 상태는 서버가 AVAILABLE로 자동 전환하므로 별도 필요 없음
                        currentStatus = TaxiDriverStatus.AVAILABLE;

                        //하차 후 off duty
                        scheduler.schedule(() -> {
                            sendTaxiDriverStatus(TaxiDriverStatus.OFF_DUTY);
                            currentStatus = TaxiDriverStatus.OFF_DUTY;
                        }, 10, TimeUnit.SECONDS);


                        //다시 빈 차 상태
                        scheduler.schedule(() -> {
                            sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);
                            currentStatus = TaxiDriverStatus.AVAILABLE;
                        }, 20, TimeUnit.SECONDS);


                    } else {
                        System.out.println("운행 종료 상태: " + summary.getDrivingStatus());
                    }

                } else {
                    System.out.println("ride-status 알 수 없는 응답: " + payload);
                }
            }
        });
    }

    //승차 요청
    public void sendRideStart(Long callId) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.out.println("세션 연결 안됨");
            return;
        }

        CallIdRequest request = new CallIdRequest();
        request.setCallId(callId);

        stompSession.send("/app/taxi-driver/ride-start", request);
        System.out.println("sendRide callId = " + callId);
    }

    //하차 요청
    public void sendRideFinish(Long callId) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.out.println("세션 연결 안됨");
            return;
        }

        CallIdRequest request = new CallIdRequest();
        request.setCallId(callId);

        stompSession.send("/app/taxi-driver/ride-finish", request);
        System.out.println("sendPickup callId = " + callId);
    }



    //콜 거절 결과 수신
    private void subscribeRejectCallResult() {
        stompSession.subscribe("/user/queue/reject-call-result", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CallRejectResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof CallRejectResponse response) {
                        System.out.println("Call reject");
                        sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);
                }
            }
        });
    }

    //배차 했는데 취소당함
    private void subscribeDispatchCanceled() {
        stompSession.subscribe("/user/queue/dispatch-canceled", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ErrorResponse response) {
                    System.out.println("matching cancel : " + response.getMessage());


                    currentCustomerLoginId = null;
                    driving = false;
                    processingCall = false;

                    sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);
                }
            }
        });
    }


    //error 구독
//    user/queue/errors
    private void subscribeErrorMessages() {
        stompSession.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ErrorResponse response) {
                    System.out.println("Error Message: " + response.getMessage());
                }
            }
        });
    }

    //배차되었는데


    //차 상태 바꾸기 - 구독 아님
    public void sendTaxiDriverStatus(TaxiDriverStatus status) {
        if(stompSession == null || !stompSession.isConnected()) {
            System.out.println("session connecting failed");
            return;
        }
        try {
            UpdateStatus request = new UpdateStatus();
            request.setStatus(status);
            stompSession.send("/app/taxi-driver/update-status", request);
            System.out.println("Send taxi status " + status);
        } catch (Exception e) {
            System.out.println("status sending exception" + e.getMessage());
        }

    }


    //위치 전송 스케줄러 시작
    private void startSendingLocation() {
        scheduler.scheduleAtFixedRate(() -> {
            try {

                if (stompSession != null && stompSession.isConnected()) {

                    if(currentStatus == TaxiDriverStatus.OFF_DUTY) {
                        System.out.println("OFF DUTY");
                        return;
                    }

                    UpdateLocationRequest locationRequest = new UpdateLocationRequest();
                    locationRequest.setCustomerLoginId(currentCustomerLoginId);

                    Location location = new Location();
                    location.setLatitude(37.56);
                    location.setLongitude(126.97);

                    locationRequest.setLocation(location);

                    stompSession.send("/app/taxi-driver/update-location", locationRequest);
                    System.out.println("location push : (" + location.getLatitude() + ", " + location.getLongitude() + ") " + currentCustomerLoginId);
                }
            } catch (Exception e) {
                System.out.println("Fail send location: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }


    //에러


}
