package com.ayush.gcnclient.handlers;

import com.ayush.gcnclient.alert.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class MacNotificationHandler implements AlertHandler {
    private String cachedIconPath;

    @Override
    public void handleAlert(Alert alert) {
        createMacAlert(alert);
    }

    private void createMacAlert(Alert alert) {
        String iconPath = resolveAppIconPath();
        String script = getScript(alert, iconPath);
        try {
            new ProcessBuilder("osascript", "-e", script).start();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static String getScript(Alert alert, String iconPath) {
        String message = alert.getTitle() + " - " + alert.getType().message() + "\n\n\n\n\n\n\n";
        String iconLine = (iconPath != null) ? String.format("with icon POSIX file \"%s\"", iconPath) : "with icon caution";

        return String.format("""
            set x to output volume of (get volume settings)
            try
                set volume output volume 80
                do shell script "afplay /System/Library/Sounds/Glass.aiff"
            end try
            set volume output volume x
            
            tell application "System Events"
                set frontmost of process "Finder" to true
            end tell
            
            tell application "Finder"
                activate
                set myDialog to display dialog "%s" ¬
                    with title "Meeting Alert" ¬
                    buttons {"Dismiss", "Open Calendar"} ¬
                    default button "Open Calendar" ¬
                    %s
            end tell
            
            if button returned of myDialog is "Open Calendar" then
                open location "https://calendar.google.com"
            end if
            """, message, iconLine);
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
            try (var in = resourceUrl.openStream()) {
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
