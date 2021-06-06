package net.earthcomputer.clientcommands.script;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.earthcomputer.clientcommands.command.FakeCommandSource;
import net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

class ScriptBuiltins {
    private static final Map<String, Object> GLOBAL_FUNCTIONS = ImmutableMap.<String, Object>builder()
            .put("$", (Function<String, Object>) command -> {
                if (MinecraftClient.getInstance().player == null) {
                    throw new IllegalStateException("Not ingame");
                }
                StringReader reader = new StringReader(command);
                if (command.startsWith("@")) {
                    try {
                        ClientEntitySelector selector = ClientEntityArgumentType.entities().parse(reader);
                        if (reader.getRemainingLength() != 0)
                            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(reader);
                        List<Entity> entities = selector.getEntities(new FakeCommandSource(MinecraftClient.getInstance().player));
                        List<Object> ret = new ArrayList<>(entities.size());
                        for (Entity entity : entities)
                            ret.add(ScriptEntity.create(entity));
                        return ret;
                    } catch (CommandSyntaxException e) {
                        throw new IllegalArgumentException("Invalid selector syntax", e);
                    }
                }
                String commandName = reader.readUnquotedString();
                reader.setCursor(0);
                if (!ClientCommandManager.isClientSideCommand(commandName)) {
                    ClientCommandManager.sendError(new TranslatableText("commands.client.notClient"));
                    return 1;
                }
                return ClientCommandManager.executeCommand(reader, command);
            })
            .put("print", (Consumer<String>) message -> {
                if (MinecraftClient.getInstance().player == null) {
                    throw new IllegalStateException("Not ingame");
                }
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(message));
            })
            .put("chat", (Consumer<String>) message -> {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) {
                    throw new IllegalStateException("Not ingame");
                }
                player.sendChatMessage(message);
            })
            .put("tick", (Runnable) ScriptManager::passTick)
            .put("isLoggedIn", (BooleanSupplier) () -> MinecraftClient.getInstance().player != null)
            .build();

    private static final Map<String, Object> GLOBAL_VARS = ImmutableMap.<String, Object>builder()
            .put("player", new ScriptPlayer())
            .put("world", new ScriptWorld())
            .build();

    private static final Map<String, Class<?>> GLOBAL_TYPES = ImmutableMap.<String, Class<?>>builder()
            .put("Thread", ScriptThread.class)
            .put("BlockState", ScriptBlockState.class)
            .put("ItemStack", ScriptItemStack.class)
            .build();

    public static Map<String, Object> getGlobalFunctions() {
        return GLOBAL_FUNCTIONS;
    }

    public static Map<String, Object> getGlobalVars() {
        return GLOBAL_VARS;
    }

    public static Map<String, Class<?>> getGlobalTypes() {
        return GLOBAL_TYPES;
    }
}
