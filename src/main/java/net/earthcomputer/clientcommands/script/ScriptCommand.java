package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class ScriptCommand {
    private static final String JSMACROS_URL = "https://www.curseforge.com/minecraft/mc-mods/jsmacros";
    private static final SimpleCommandExceptionType NO_JSMACROS_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cscript.nojsmacros",
            new TranslatableText("commands.cscript.nojsmacros.link")
                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, JSMACROS_URL))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(JSMACROS_URL)))
                            .withUnderline(true))));
    private static boolean warnedDeprecated = false;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cscript");

        dispatcher.register(literal("cscript")
            .then(literal("reload")
                .executes(ctx -> reloadScripts()))
            .then(literal("run")
                .then(argument("script", string())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(ScriptManager.getLegacyScriptNames(), builder))
                    .executes(ctx -> runLegacyScript(getString(ctx, "script")))))
            .then(literal("exec")
                .then(argument("script", string())
                    .suggests(ScriptManager.getScriptSuggestions())
                    .executes(ctx -> execScript(getString(ctx, "script"))))));
    }

    private static int reloadScripts() {
        ScriptManager.reloadLegacyScripts();
        sendFeedback("commands.cscript.reload.success");
        return ScriptManager.getLegacyScriptNames().size();
    }

    private static int runLegacyScript(String name) throws CommandSyntaxException {
        if (!ScriptManager.isJsMacrosPresent) {
            throw NO_JSMACROS_EXCEPTION.create();
        }
        if (!warnedDeprecated) {
            warnedDeprecated = true;
            sendFeedback(new TranslatableText("commands.cscript.run.deprecated").formatted(Formatting.YELLOW));
        }
        ScriptManager.executeLegacyScript(name);
        sendFeedback("commands.cscript.run.success");
        return 0;
    }

    private static int execScript(String name) throws CommandSyntaxException {
        if (!ScriptManager.isJsMacrosPresent) {
            throw NO_JSMACROS_EXCEPTION.create();
        }
        ScriptManager.executeScript(name);
        sendFeedback("commands.cscript.run.success");
        return 0;
    }
}
