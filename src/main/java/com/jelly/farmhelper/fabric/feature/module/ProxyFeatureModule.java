package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Locale;

public class ProxyFeatureModule extends AbstractFeatureModule {
    private boolean applied;
    private String appliedType = "";

    public ProxyFeatureModule(boolean enabled) {
        super("proxy", "Proxy", enabled);
    }

    @Override
    public void onEnable() {
        applyProxyConfig();
    }

    @Override
    public void onDisable() {
        clearProxyConfig();
    }

    @Override
    public void onDisconnect() {
        if (enabled()) {
            applyProxyConfig();
        } else {
            clearProxyConfig();
        }
    }

    private void applyProxyConfig() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.proxyEnabled || config.proxyAddress == null || config.proxyAddress.isBlank()) {
            clearProxyConfig();
            return;
        }

        String[] parts = config.proxyAddress.trim().split(":");
        if (parts.length != 2) {
            FarmHelperFabric.LOGGER.warn("Proxy address must be host:port, got '{}'", config.proxyAddress);
            clearProxyConfig();
            return;
        }

        String host = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            FarmHelperFabric.LOGGER.warn("Invalid proxy port in '{}'", config.proxyAddress);
            clearProxyConfig();
            return;
        }

        String type = config.proxyType == null ? "SOCKS" : config.proxyType.trim().toUpperCase(Locale.ROOT);
        clearProxyConfig();
        if ("HTTP".equals(type)) {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", Integer.toString(port));
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", Integer.toString(port));
            appliedType = "HTTP";
        } else {
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", Integer.toString(port));
            appliedType = "SOCKS";
        }

        if (config.proxyUsername != null && !config.proxyUsername.isBlank()
                && config.proxyPassword != null && !config.proxyPassword.isBlank()) {
            String username = config.proxyUsername;
            char[] password = config.proxyPassword.toCharArray();
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        }

        applied = true;
        FarmHelperFabric.LOGGER.info("Proxy module applied {} proxy {}", appliedType, config.proxyAddress);
    }

    private void clearProxyConfig() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        if (applied) {
            Authenticator.setDefault(null);
        }
        applied = false;
        appliedType = "";
    }
}
