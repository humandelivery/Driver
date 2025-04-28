package application;

import domain.model.TaxiDriverStatus;
import domain.model.UpdateLocationRequest;
import domain.model.UpdateStatus;


import java.lang.reflect.Type;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import domain.model.UpdateTaxiDriverStatusResponse;
import org.springframework.http.ResponseEntity;
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

    public void connectWithToken(String jwtToken) {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // JSON <-> 객체 간 변환을 자동으로 해준다.
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

                stompSession.subscribe("/user/queue/taxi-driver-status", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return UpdateTaxiDriverStatusResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {

                        if (payload instanceof UpdateTaxiDriverStatusResponse) {
                            UpdateTaxiDriverStatusResponse response = (UpdateTaxiDriverStatusResponse)payload;
                            System.out.println("response.getTaxiDriverStatus() = " + response.getTaxiDriverStatus());
                            System.out.println("response.getRequestStatus() = " + response.getRequestStatus());
                        }

                    }
                });

                //상태 변경
                UpdateStatus request = new UpdateStatus();

                request.setStatus(TaxiDriverStatus.AVAILABLE);
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
                    UpdateLocationRequest location = new UpdateLocationRequest();
                    location.setLatitude(37.56);
                    location.setLongitude(126.97);

                    stompSession.send("/app/taxi-driver/location", location);
                    System.out.println("location push: (" + location.getLatitude() + ", " + location.getLongitude() + ")");
                }
            } catch (Exception e) {
                System.out.println("Failed to send location: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

}