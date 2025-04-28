package application;

import domain.model.UpdateStatus;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientService {
    private StompSession stompSession;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
                System.out.println("WebSocket success : " + session.getSessionId());
                stompSession = session;
                //상태보내느 곳 구독
                stompSession.subscribe("/user/queue/taxi-driver-status", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return String.class;

                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        System.out.println("received message : " + payload);
                        //진규님이 주시는 message로 바꾸기
                        if("OK".equalsIgnoreCase(payload.toString().trim())) {
                            startSendingLocation();
                        }
                    }
                });

                //상태 변경
                UpdateStatus request = new UpdateStatus();
                request.setStatus("운행중");

                stompSession.send("/app/taxi-driver/update-status", request);
                System.out.println("Sent taxi driver status update: driving~");

                //콜 요청 or 거절


                //요청 할 경우 택시 기사 정보 보내야함

                //운행 상태도 보내야함

                //하차 후 운행 종료 메세지 보내야함

                //기사가 쉴 때, 운행 종료했을 때 요청도 보내야함

            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.out.println("WebSocket connecting error : " + exception.getMessage());
            }
        });

        System.out.println("WebSocket connection initiated.");
    }

    private void startSendingLocation() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (stompSession != null && stompSession.isConnected()) {
                    stompSession.send("/app/taxi-driver/location", "location (37.56, 126.97)");
                    System.out.println("location push");
                }
            } catch (Exception e) {
                System.out.println("Failed to send location: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
}
