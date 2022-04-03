package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class ScriptCommand {
    private static final String JSMACROS_URL = "https://www.curseforge.com/minecraft/mc-mods/jsmacros";
    private static final SimpleCommandExceptionType NO_JSMACROS_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cscript.nojsmacros",
            new TranslatableText("commands.cscript.nojsmacros.link")
                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, JSMACROS_URL))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(JSMACROS_URL)))
                            .withUnderline(true))));
    private static boolean warnedDeprecated = false;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cscript")
            .then(literal("reload")
                .executes(ctx -> reloadScripts()))
            .then(literal("run")
                .then(argument("script", string())
                    .suggests((ctx, builder) -> {
                        if (!ClientCommandsScripting.isJsMacrosPresent) {
                            return builder.buildFuture();
                        }
                        return CommandSource.suggestMatching(ScriptManager.getLegacyScriptNames(), builder);
                    })
                    .executes(ctx -> runLegacyScript(getString(ctx, "script")))))
            .then(literal("exec")
                .then(argument("script", string())
                    .suggests(ClientCommandsScripting.isJsMacrosPresent ? ScriptManager.getScriptSuggestions() : (ctx, builder) -> builder.buildFuture())
                    .executes(ctx -> execScript(getString(ctx, "script"))))));
    }

    private static int reloadScripts() throws CommandSyntaxException {
        if (!ClientCommandsScripting.isJsMacrosPresent) {
            throw NO_JSMACROS_EXCEPTION.create();
        }
        warnDeprecated();
        ScriptManager.reloadLegacyScripts();
        sendFeedback("commands.cscript.reload.success");
        return ScriptManager.getLegacyScriptNames().size();
    }

    private static int runLegacyScript(String name) throws CommandSyntaxException {
        if (!ClientCommandsScripting.isJsMacrosPresent) {
            throw NO_JSMACROS_EXCEPTION.create();
        }
        warnDeprecated();
        ScriptManager.executeLegacyScript(name);
        sendFeedback("commands.cscript.run.success");
        return 0;
    }

    private static int execScript(String name) throws CommandSyntaxException {
        if (!ClientCommandsScripting.isJsMacrosPresent) {
            throw NO_JSMACROS_EXCEPTION.create();
        }
        warnDeprecated();
        ScriptManager.executeScript(name);
        sendFeedback("commands.cscript.run.success");
        return 0;
    }

    private static void warnDeprecated() {
        if (!warnedDeprecated) {
            warnedDeprecated = true;
            sendFeedback(new TranslatableText("commands.cscript.deprecated")
                    .append(new TranslatableText("commands.cscript.nojsmacros.link")
                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, JSMACROS_URL))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(JSMACROS_URL)))
                                    .withUnderline(true)))
                    .formatted(Formatting.YELLOW));
        }
    }
}
