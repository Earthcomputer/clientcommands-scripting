package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import xyz.wagyourtail.jsmacros.client.JsMacros;
import xyz.wagyourtail.jsmacros.core.config.ScriptTrigger;
import xyz.wagyourtail.jsmacros.core.language.ContextContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ScriptManager {
    private static final Logger LOGGER = LogManager.getLogger("ScriptManager");
    private static final DynamicCommandExceptionType SCRIPT_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cscript.notFound", arg));

    private static ClientCommandsLanguage language;

    private static Path legacyScriptsDir;
    private static final Map<String, String> legacyScripts = new HashMap<>();

    private static final Deque<ThreadInstance> threadStack = new ArrayDeque<>();
    private static final List<ThreadInstance> runningThreads = new ArrayList<>();

    private static final AtomicInteger nextThreadId = new AtomicInteger();

    public static void inject() {
        LOGGER.info("Injecting clientcommands into jsmacros");
        language = new ClientCommandsLanguage(".clientcommands", JsMacros.core);
        JsMacros.core.addLanguage(language);
        JsMacros.core.libraryRegistry.addLibrary(ClientCommandsLibrary.class);
    }

    public static void reloadLegacyScripts() {
        LOGGER.info("Reloading legacy clientcommands scripts");

        legacyScriptsDir = ClientCommandsScripting.configDir.resolve("scripts");

        legacyScripts.clear();

        if (!Files.exists(legacyScriptsDir)) {
            return;
        }

        try {
            Files.walk(legacyScriptsDir, FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            legacyScripts.put(legacyScriptsDir.relativize(path).toString(), FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("Error reloading clientcommands scripts", e);
        }
    }

    public static SuggestionProvider<ServerCommandSource> getScriptSuggestions() {
        return (ctx, builder) -> CompletableFuture.supplyAsync(() -> {
            Path macroFolder = JsMacros.core.config.macroFolder.toPath();
            if (!Files.exists(macroFolder)) {
                return builder.build();
            }
            try {
                return CommandSource.suggestMatching(Files.walk(macroFolder)
                        .filter(Files::isRegularFile)
                        .map(path -> macroFolder.relativize(path).toString()), builder).join();
            } catch (IOException e) {
                return builder.build();
            }
        });
    }

    public static Set<String> getLegacyScriptNames() {
        return Collections.unmodifiableSet(legacyScripts.keySet());
    }

    static ThreadInstance currentThread() {
        return threadStack.peek();
    }

    static Context currentContext() {
        Context context = Context.getCurrent();
        return context.getBindings("js").getMember("context").<ContextContainer<Context>>asHostObject().getCtx().getContext().get();
    }

    public static void executeScript(String scriptFile) throws CommandSyntaxException {
        if (!Files.exists(JsMacros.core.config.macroFolder.toPath().resolve(scriptFile))) {
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptFile);
        }
        execute0((then, catcher) -> JsMacros.core.exec(new ScriptTrigger(ScriptTrigger.TriggerType.EVENT, "", scriptFile, true), null, then, catcher));
    }

    public static void executeLegacyScript(String scriptName) throws CommandSyntaxException {
        String scriptSource = legacyScripts.get(scriptName);
        if (scriptSource == null)
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptName);

        execute0((then, catcher) -> language.trigger(scriptSource, then, catcher));
    }

    private static void execute0(BiConsumer<Runnable, Consumer<Throwable>> runner) {
        ThreadInstance thread = new ScriptThread(() -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            runner.accept(() -> future.complete(null), future::completeExceptionally);
            future.join();
            return null;
        }, false).thread;
        runThread(thread);
    }

    static ThreadInstance createThread(ScriptThread handle, Callable<Void> task, boolean daemon) {
        ThreadInstance thread = new ThreadInstance();
        thread.handle = handle;
        thread.javaThread = new Thread(() -> {
            if (thread.parentContext != null) {
                Context parentContext = thread.parentContext.get();
                if (parentContext != null) {
                    parentContext.enter();
                }
            }
            try {
                task.call();
            } catch (Throwable e) {
                if (!thread.killed && !thread.task.isCompleted()) {
                    try {
                        ClientCommandManager.sendError(new LiteralText(e.getMessage() == null ? e.toString() : e.getMessage()));
                    } catch (Throwable e1) {
                        LOGGER.error("Error sending error to chat", e1);
                    }
                    LOGGER.error("An error occurred in a script command: ", e);
                }
            }
            runningThreads.remove(thread);
            if (thread.parentContext != null && !thread.killed && !thread.task.isCompleted()) {
                Context parentContext = thread.parentContext.get();
                if (parentContext != null) {
                    parentContext.leave();
                }
            }
            if (thread.parent != null)
                thread.parent.children.remove(thread);
            for (ThreadInstance child : thread.children) {
                child.parent = null;
                if (child.daemon)
                    child.killed = true;
            }
            thread.running = false;
            thread.blocked.set(true);
        });
        thread.javaThread.setName("ClientCommands script thread " + nextThreadId.getAndIncrement());
        thread.javaThread.setDaemon(true);
        thread.task = new SimpleTask() {
            @Override
            public boolean condition() {
                return thread.running;
            }

            @Override
            protected void onTick() {
            }
        };
        thread.daemon = daemon;

        return thread;
    }

    static void runThread(ThreadInstance thread) {
        TaskManager.addTask("cscript", thread.task);
        runningThreads.add(thread);
        thread.running = true;

        Context context = null;
        if (currentThread() != null) {
            currentThread().children.add(thread);
            thread.parent = currentThread();
            context = currentContext();
            context.leave();
            thread.parentContext = new WeakReference<>(context);
        }

        threadStack.push(thread);
        thread.javaThread.start();
        while (!thread.blocked.get()) {
            try {
                //noinspection BusyWait
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threadStack.pop();

        if (context != null) {
            context.enter();
        }
    }

    public static void tick() {
        for (ThreadInstance thread : new ArrayList<>(runningThreads)) {
            if (thread.paused || !thread.running) continue;

            threadStack.push(thread);
            thread.blocked.set(false);
            while (!thread.blocked.get()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            threadStack.pop();
        }
    }

    static void passTick() {
        ThreadInstance thread = currentThread();
        Context context = currentContext();
        context.leave();
        thread.blocked.set(true);
        while (thread.blocked.get()) {
            try {
                //noinspection BusyWait
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (thread.killed || thread.task.isCompleted()) {
            throw new ScriptInterruptedException();
        }
        context.enter();
    }

    static void blockInput(boolean blockInput) {
        currentThread().blockingInput = blockInput;
    }

    static boolean isCurrentScriptBlockingInput() {
        return currentThread().blockingInput;
    }

    public static boolean blockingInput() {
        for (ThreadInstance script : runningThreads)
            if (script.blockingInput)
                return true;
        return false;
    }

    static Input getScriptInput() {
        return currentThread().input;
    }

    public static void copyScriptInputToPlayer(boolean inSneakingPose) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Input playerInput = player.input;
        for (ThreadInstance thread : runningThreads) {
            playerInput.pressingForward |= thread.input.pressingForward;
            playerInput.pressingBack |= thread.input.pressingBack;
            playerInput.pressingLeft |= thread.input.pressingLeft;
            playerInput.pressingRight |= thread.input.pressingRight;
            playerInput.jumping |= thread.input.jumping;
            playerInput.sneaking |= thread.input.sneaking;
        }
        playerInput.movementForward = playerInput.pressingForward ^ playerInput.pressingBack ? (playerInput.pressingForward ? 1 : -1) : 0;
        playerInput.movementSideways = playerInput.pressingLeft ^ playerInput.pressingRight ? (playerInput.pressingLeft ? 1 : -1) : 0;
        if (playerInput.sneaking || inSneakingPose) {
            playerInput.movementSideways = (float)(playerInput.movementSideways * 0.3D);
            playerInput.movementForward = (float)(playerInput.movementForward * 0.3D);
        }
    }

    static void setSprinting(boolean sprinting) {
        currentThread().sprinting = sprinting;
    }

    static boolean isCurrentThreadSprinting() {
        return currentThread().sprinting;
    }

    public static boolean isSprinting() {
        for (ThreadInstance thread : runningThreads)
            if (thread.sprinting)
                return true;
        return false;
    }

    static class ThreadInstance {
        ScriptThread handle;

        boolean daemon;
        boolean paused;
        boolean killed;
        ThreadInstance parent;
        WeakReference<Context> parentContext;
        List<ThreadInstance> children = new ArrayList<>(0);

        private Thread javaThread;
        private final AtomicBoolean blocked = new AtomicBoolean(false);
        boolean running;
        private LongTask task;
        private boolean blockingInput = false;
        @SuppressWarnings("NewExpressionSideOnly")
        private final Input input = new Input();
        private boolean sprinting = false;
    }

}
