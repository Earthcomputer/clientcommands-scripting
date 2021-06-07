package net.earthcomputer.clientcommands.script;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientCommandsScripting implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    public static Path configDir;
    public static boolean isJsMacrosPresent = FabricLoader.getInstance().isModLoaded("jsmacros");

    @Override
    public void onInitialize() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to create config directory", e);
        }

        if (isJsMacrosPresent) {
            ScriptManager.inject();
            ScriptManager.reloadLegacyScripts();
        } else {
            LOGGER.info("Clientcommands scripts are disabled because jsmacros is not present");
        }
    }
}
