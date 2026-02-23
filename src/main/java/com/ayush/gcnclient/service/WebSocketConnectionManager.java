package com.ayush.gcnclient.service;

import com.ayush.gcnclient.alert.Alert;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@SuppressWarnings("NullableProblems")
public class WebSocketConnectionManager {

    @Value("${gcn-server.url}")
    private String url;
    @Value("${gcn-server.receive-timeout-seconds}")
    private int RECONNECT_DELAY_SECONDS;
    @Value("${gcn-server.personal-key}")
    private String personalKey;

    private WebSocketStompClient stompClient;
    private ScheduledFuture<?> reconnectTask;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentTaskScheduler scheduler = new ConcurrentTaskScheduler(executorService);
    private final TaskScheduler loggingScheduler = new LoggingTaskScheduler(scheduler);
    private final List<AlertHandler> alertHandlers = new CopyOnWriteArrayList<>();

    public WebSocketConnectionManager(Collection<AlertHandler> alertHandlers) {
        this.alertHandlers.addAll(alertHandlers);
    }

    @PostConstruct
    public void connect() {
        if (stompClient == null) {
            stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new JacksonJsonMessageConverter());
            stompClient.setTaskScheduler(loggingScheduler);
            stompClient.setDefaultHeartbeat(new long[]{30000, 30000});
        }
        log.info("Connecting to WebSocket server at {}...", url);
        stompClient.connectAsync(url, new StompSessionHandler());
    }

    private class StompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders headers) {
            log.info("Connected to WebSocket Server!");
            subscribeToMeetings(session);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte [] payload, Throwable e) {
            log.error("Client Error: {}", e.getMessage(), e);
            scheduleReconnect();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable e) {
            log.error("Transport error: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    private void subscribeToMeetings(StompSession session) {
        session.subscribe("/topic/" + personalKey, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Alert.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                for (AlertHandler handler : alertHandlers) {
                    handler.handleAlert((Alert) payload);
                }
            }
        });
        log.info("Subscribed to /topic/meetings");
    }

    private void scheduleReconnect() {
        if (reconnectTask == null || reconnectTask.isDone()) {
            log.info("Scheduling reconnect in {} ms...", RECONNECT_DELAY_SECONDS);
            reconnectTask = scheduler.schedule(this::connect, Instant.now().plusSeconds(RECONNECT_DELAY_SECONDS));
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        executorService.shutdownNow();
    }

    // --- Heartbeat Logging TaskScheduler ---
    private static class LoggingTaskScheduler implements TaskScheduler {
        private final TaskScheduler delegate;
        LoggingTaskScheduler(TaskScheduler delegate) {
            this.delegate = delegate;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            log.info("[Heartbeat] Scheduled heartbeat send at {}", startTime);
            return delegate.schedule(task, startTime);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            log.info("[Heartbeat] Scheduled with Trigger: {}", trigger);
            return delegate.schedule(task, trigger);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            log.info("[Heartbeat] Scheduled at fixed rate from {} every {}", startTime, period);
            return delegate.scheduleAtFixedRate(task, startTime, period);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            log.info("[Heartbeat] Scheduled at fixed rate every {}", period);
            return delegate.scheduleAtFixedRate(task, period);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            log.info("[Heartbeat] Scheduled with fixed delay from {} every {}", startTime, delay);
            return delegate.scheduleWithFixedDelay(task, startTime, delay);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            log.info("[Heartbeat] Scheduled with fixed delay every {}", delay);
            return delegate.scheduleWithFixedDelay(task, delay);
        }
    }
}
