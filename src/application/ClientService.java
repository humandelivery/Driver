package application;

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
    //순서 보장을 위한 수신 했는지에 대한 flag
    private boolean checkReceive = false;
    //큐 필드 추가 하나으 ㅣ쓰레드가 callqueue를 3초에 한번씩 조회 하나씩 다 검증 배차 됐나 안됐나 ㅋㅋ
    private final BlockingQueue<CallMessageResponse> callQueue = new LinkedBlockingQueue<>();
    private volatile boolean processingCall = false;
    //택시 상태 확인
    private volatile TaxiDriverStatus currentStatus = TaxiDriverStatus.AVAILABLE;

    //콜 처리 스레드 시작 됨?
    private boolean callConsumerStarted = false;




    public void connectWithToken(String jwtToken) {
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

                //차 상태 설정
                sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);

                //위치 전송 스케줄러 시작
                // startSendingLocation();

                //콜 요청 수신
                subscribeCallRequest();

                //콜 수락 결과 수신
                subscribeAcceptCallResult();

                //콜 거절 결과 수신
                subscribeRejectCallResult();


            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.out.println("WebSocket error: " + exception.getMessage());
            }
        });

        System.out.println("Connecting to WebSocket server...");
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

                        //진심 개거슬리는부분
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

                        checkReceive = false;
                        // stopSendingLocation();
                    } else {
                        System.out.println("ERROR STATUS: " + status);

                    }
                }
            }
        });
    }


    //off- duty
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

                    System.out.println("랜덤 처리 번호: " + decision + " → " + (accept ? "수락" : "거절"));

                    Thread.sleep(1000); // 약간의 대기 (너무 빠르면 서버 감당 힘들 수도)

                    if (accept) {
                        CallAcceptRequest acceptRequest = new CallAcceptRequest();
                        acceptRequest.setCallId(request.getCallId());
                        stompSession.send("/api/taxi-driver/accept-call", acceptRequest);
                        System.out.println("Call accept");
                    } else {
                        CallRejectRequest reject = new CallRejectRequest();
                        reject.setCallId(request.getCallId());
                        stompSession.send("/api/taxi-driver/reject-call", reject);
                        System.out.println("Call rejection");

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
                    MatchingResponse matching = response.getMatchingResponse();
                    if (matching != null) {
                        currentCustomerLoginId = matching.getCustomerLoginId();
                        driving = true;
                        callQueue.clear();
                        processingCall = false;

                        //예약 중!!!!!!
                        sendTaxiDriverStatus(TaxiDriverStatus.RESERVED);

                        //손님 탔음
                        scheduler.schedule(() -> {
                            System.out.println("on Driving~");
                            sendTaxiDriverStatus(TaxiDriverStatus.ON_DRIVING);
                        }, 5, TimeUnit.SECONDS);

                        //손님 내림
                        scheduler.schedule(() -> {
                            System.out.println("pickup success");
                            currentCustomerLoginId = null;
                            driving = false;
                            sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);
                        }, 10, TimeUnit.SECONDS);
                    }
                }
            }
        });
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
                System.out.println("Error Message : " + payload);
            }
        });
    }


    //차 상태 바꾸기 - 구독 아님
    public void sendTaxiDriverStatus(TaxiDriverStatus status) {
        UpdateStatus request = new UpdateStatus();
        request.setStatus(status);
        stompSession.send("/app/taxi-driver/update-status", request);
        System.out.println("Send taxi status " + status);
    }


    //위치 전송 스케줄러 시작
    private void startSendingLocation() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                //일단은 응답 받으면 checkReceive가 true니깐 그때 받을 수 있게 해둠
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





}
