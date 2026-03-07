package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;

import java.io.IOException;
import java.util.Locale;

public final class DesktopNotifier {
    private static long lastNotificationMs;

    private DesktopNotifier() {
    }

    public static void notifyFailsafe(String failsafeName, String reason) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.popUpNotifications) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0, config.desktopNotificationCooldownSeconds) * 1000L;
        if (now - lastNotificationMs < cooldownMs) {
            return;
        }
        lastNotificationMs = now;

        String normalizedFailsafe = failsafeName == null || failsafeName.isBlank() ? "UNKNOWN" : failsafeName.trim();
        String title = "FarmHelper Failsafe: " + normalizedFailsafe;
        String message = reason == null || reason.isBlank()
                ? "A failsafe was triggered."
                : reason.trim();

        if (!dispatchNativeNotification(title, message)) {
            Chat.info(title + " - " + message);
        }
    }

    private static boolean dispatchNativeNotification(String title, String message) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                return sendMacNotification(title, message);
            }
            if (os.contains("linux")) {
                return sendLinuxNotification(title, message);
            }
            if (os.contains("win")) {
                return sendWindowsNotification(title, message);
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed to send desktop notification", e);
        }
        return false;
    }

    private static boolean sendMacNotification(String title, String message) throws IOException {
        String escapedTitle = escapeForAppleScript(title);
        String escapedMessage = escapeForAppleScript(message);
        String script = "display notification \"" + escapedMessage + "\" with title \"" + escapedTitle + "\"";
        new ProcessBuilder("osascript", "-e", script).start();
        return true;
    }

    private static boolean sendLinuxNotification(String title, String message) throws IOException {
        new ProcessBuilder("notify-send", title, message).start();
        return true;
    }

    private static boolean sendWindowsNotification(String title, String message) throws IOException {
        String psTitle = title.replace("'", "''");
        String psBody = message.replace("'", "''");
        String command = "$wshell = New-Object -ComObject WScript.Shell;"
                + "$wshell.Popup('" + psBody + "', 5, '" + psTitle + "', 0x40) | Out-Null";
        new ProcessBuilder("powershell", "-NoProfile", "-Command", command).start();
        return true;
    }

    private static String escapeForAppleScript(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
