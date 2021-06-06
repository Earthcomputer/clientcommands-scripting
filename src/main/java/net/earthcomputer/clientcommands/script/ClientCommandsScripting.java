package net.earthcomputer.clientcommands.script;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.wagyourtail.jsmacros.client.JsMacros;

public class ClientCommandsScripting implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ClientCommandsLanguage LANGUAGE = new ClientCommandsLanguage(".clientcommands", JsMacros.core);

    @Override
    public void onInitialize() {
        ScriptManager.reloadScripts();

        LOGGER.info("Injecting clientcommands into jsmacros");
        JsMacros.core.addLanguage(LANGUAGE);
    }
}
