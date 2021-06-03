package net.earthcomputer.clientcommands.script;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.wagyourtail.jsmacros.client.JsMacros;

public class ClientCommandsScripting implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Injecting clientcommands into jsmacros");
        JsMacros.core.addLanguage(new ClientCommandsLanguage("clientcommands", JsMacros.core));
    }
}
