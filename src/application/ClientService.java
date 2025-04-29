package application;

import domain.model.*;

import java.lang.reflect.Type;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private boolean readyToReceiveCall = false; // 빈차 여부
    // 운행 중 여부
    private boolean driving = false;
    //손님 없으면 null로 보내기 위함
    private String currentCustomerLoginId = null;

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

                //택시 상태 수신
                subscribeTaxiDriverStatus();


                //차 상태 설정
                sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);

                //위치 전송 스케줄러 시작
                startSendingLocation();

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
                    if (response.getTaxiDriverStatus() == TaxiDriverStatus.AVAILABLE) {
                        System.out.println("Taxi ready");
                        readyToReceiveCall = true;
                    } else {
                        System.out.println("no call");
                        readyToReceiveCall = false;
                    }

                }
            }
        });
    }


    //콜 요청 수신
    private void subscribeCallRequest() {
        stompSession.subscribe("/user/queue/call-request", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CallMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (!readyToReceiveCall) {
                    System.out.println("Not ready");
                    return;
                }

                if (payload instanceof CallMessageResponse callRequest) {
                    System.out.println("Call ID: " + callRequest.getCallId());
                    System.out.println("Origin: (" + callRequest.getExpectedOrigin().getLatitude() + ", " + callRequest.getExpectedOrigin().getLongitude() + ")");
                    System.out.println("Dest : (" + callRequest.getExpectedDestination().getLatitude() + ", " + callRequest.getExpectedDestination().getLongitude() + ")");

                    Scanner scanner = new Scanner(System.in);
                    System.out.print("Accept call (yes/no):  yes - 1 ");
                    String input = scanner.nextLine().trim().toLowerCase();

                    if ("1".equals(input)) {
                        CallAcceptRequest acceptRequest = new CallAcceptRequest();
                        acceptRequest.setCallId(callRequest.getCallId());
                        stompSession.send("/api/taxi-driver/accept-call", acceptRequest);
                        System.out.println("Send call accept");
                    } else {
                        CallRejectRequest rejectRequest = new CallRejectRequest();
                        rejectRequest.setCallId(callRequest.getCallId());
                        stompSession.send("/api/taxi-driver/reject-call", rejectRequest);
                        System.out.println("Send call reject");
                    }
                }
            }
        });
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
                        System.out.println("Call accept success");
                        System.out.println("Customer id : " + matching.getCustomerLoginId());
                        System.out.println("customer origin : " + matching.getExpectedOrigin());
                        System.out.println("customer des : " + matching.getExpectedDestination());
                        currentCustomerLoginId = matching.getCustomerLoginId();
                        driving = true;

                        //예약중!!!!!
                        sendTaxiDriverStatus(TaxiDriverStatus.RESERVED);
                        System.out.println("reserved");
                        readyToReceiveCall = false;

                        //출발
                        scheduler.schedule(() -> {
                            sendTaxiDriverStatus(TaxiDriverStatus.ON_DRIVING);
                            System.out.println("on driving");
                        }, 5, TimeUnit.SECONDS);

                        //손님 내려줌
                        scheduler.schedule(() -> {
                            currentCustomerLoginId = null;
                            driving = false;
                            sendTaxiDriverStatus(TaxiDriverStatus.AVAILABLE);
                            System.out.println("available");
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
                if (stompSession != null && stompSession.isConnected()) {
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
