package com.ayush.gcnclient.service;

import com.ayush.gcnclient.alert.AlertTask;
import com.ayush.gcnclient.alert.AlertType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class Client {

    @Value("${gcn-server.url}")
    private String URL;
    private String cachedIconPath;

    @PostConstruct
    public void connect() {
        blastMacAlert("GCN Client Started", AlertType.STARTING_NOW);
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompSessionHandler sessionHandler = new MyStompSessionHandler();
        stompClient.connectAsync(URL, sessionHandler);
    }

    private class MyStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, @NonNull StompHeaders connectedHeaders) {
            log.info("Connected to WebSocket Server!");

            // Subscribe to the topic you defined in the server
            session.subscribe("/topic/meetings", new StompFrameHandler() {

                @Override
                @NullMarked
                public Type getPayloadType(StompHeaders headers) {
                    return AlertTask.class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                    AlertTask alert = (AlertTask) payload;
                    triggerLocalAction(alert);
                }
            });
        }

        @Override
        public void handleException(@NonNull StompSession s, StompCommand c, @NonNull StompHeaders h, byte @NonNull [] p, @NonNull Throwable e) {
            log.error("Client Error: {}", e.getMessage(), e);
        }
    }

    private void triggerLocalAction(AlertTask alert) {
        log.info("ALERT: {} - in {}", alert.getTitle(), alert.getType().message());
        blastMacAlert(alert.getTitle(), alert.getType());
    }

    private void blastMacAlert(String title, AlertType type) {
        String iconPath = resolveAppIconPath();
        String script = getScript(title, type, iconPath);

        try {
            Process b = new ProcessBuilder("osascript", "-e", script).start();

            // Listen for the button click
            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(b.getInputStream()));
                    String response = reader.readLine();
                    if (response != null && response.contains("Open Calendar")) {
                        String[] command = {"open", "https://calendar.google.com"};
                        Runtime.getRuntime().exec(command);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }).start();

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static @NonNull String getScript(String title, AlertType type, String iconPath) {
        String message = title + " - " + type.message() + "\n\n\n\n\n\n\n";
        String script;
        if (iconPath != null) {
            script = String.format(
                """
                set x to output volume of (get volume settings)
                set volume output volume 80
                do shell script "afplay /System/Library/Sounds/Glass.aiff"
                set volume output volume x
                tell app "System Events" to display dialog "%s" \
                    with title "Meeting Alert" \
                    buttons {"Dismiss", "Open Calendar"} \
                    default button "Open Calendar" \
                    with icon POSIX file "%s"
                """,
                message, iconPath
            );
        } else {
            script = String.format(
                """
                set x to output volume of (get volume settings)
                set volume output volume 80
                do shell script "afplay /System/Library/Sounds/Glass.aiff"
                set volume output volume x
                tell app "System Events" to display dialog "%s" \
                    with title "Meeting Alert" \
                    buttons {"Dismiss", "Open Calendar"} \
                    default button "Open Calendar" \
                    with icon caution
                """,
                message
            );
        }
        return script;
    }

    private String resolveAppIconPath() {
        if (cachedIconPath != null) {
            return cachedIconPath;
        }
        URL resourceUrl = getClass().getClassLoader().getResource("app.icns");
        if (resourceUrl == null) {
            return null;
        }
        try {
            if ("file".equals(resourceUrl.getProtocol())) {
                cachedIconPath = Path.of(resourceUrl.toURI()).toString();
                return cachedIconPath;
            }
            Path tempIcon = Files.createTempFile("gcn-app-icon-", ".icns");
            try (InputStream in = resourceUrl.openStream()) {
                Files.copy(in, tempIcon, StandardCopyOption.REPLACE_EXISTING);
            }
            cachedIconPath = tempIcon.toString();
            return cachedIconPath;
        } catch (Exception e) {
            log.warn("Unable to load app.icns from resources; falling back to caution icon.", e);
            return null;
        }
    }
}
