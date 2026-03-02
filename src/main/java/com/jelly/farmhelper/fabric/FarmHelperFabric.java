package com.jelly.farmhelper.fabric;

import com.jelly.farmhelper.fabric.config.ConfigManager;
import com.jelly.farmhelper.fabric.event.FarmEventBus;
import com.jelly.farmhelper.fabric.failsafe.FailsafeManager;
import com.jelly.farmhelper.fabric.feature.FeatureManager;
import com.jelly.farmhelper.fabric.macro.MacroController;
import com.jelly.farmhelper.fabric.notification.DiscordWebhookService;
import com.jelly.farmhelper.fabric.runtime.ClientActionQueue;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmHelperFabric implements ModInitializer {
    public static final String MOD_ID = "farmhelperfabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ConfigManager CONFIG_MANAGER = new ConfigManager();
    private static final FeatureManager FEATURE_MANAGER = new FeatureManager();
    private static final FailsafeManager FAILSAFE_MANAGER = new FailsafeManager();
    private static final MacroController MACRO_CONTROLLER = new MacroController();
    private static final DiscordWebhookService WEBHOOK_SERVICE = new DiscordWebhookService();
    private static final ClientActionQueue CLIENT_ACTION_QUEUE = new ClientActionQueue();
    private static final FarmEventBus EVENT_BUS = new FarmEventBus();

    @Override
    public void onInitialize() {
        CONFIG_MANAGER.load();
        FEATURE_MANAGER.bootstrap();
        LOGGER.info("FarmHelper Fabric initialized for modern Fabric runtime");
    }

    public static ConfigManager getConfigManager() {
        return CONFIG_MANAGER;
    }

    public static FeatureManager getFeatureManager() {
        return FEATURE_MANAGER;
    }

    public static FailsafeManager getFailsafeManager() {
        return FAILSAFE_MANAGER;
    }

    public static MacroController getMacroController() {
        return MACRO_CONTROLLER;
    }

    public static DiscordWebhookService getWebhookService() {
        return WEBHOOK_SERVICE;
    }

    public static ClientActionQueue getClientActionQueue() {
        return CLIENT_ACTION_QUEUE;
    }

    public static FarmEventBus getEventBus() {
        return EVENT_BUS;
    }
}
