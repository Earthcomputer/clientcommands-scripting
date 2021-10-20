package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.jetbrains.annotations.Nullable;
import xyz.wagyourtail.jsmacros.client.JsMacros;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.config.ScriptTrigger;
import xyz.wagyourtail.jsmacros.core.language.BaseScriptContext;
import xyz.wagyourtail.jsmacros.core.language.EventContainer;
import xyz.wagyourtail.jsmacros.core.library.impl.FJsMacros;
import xyz.wagyourtail.jsmacros.core.library.impl.FWrapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ScriptManager {
    private static final Logger LOGGER = LogManager.getLogger("ScriptManager");
    private static final DynamicCommandExceptionType SCRIPT_NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.cscript.notFound", arg));

    private static ClientCommandsLanguage language;

    private static Path legacyScriptsDir;
    private static final Map<String, String> legacyScripts = new HashMap<>();

    private static final List<ThreadInstance> runningThreads = new ArrayList<>();
    private static final Map<BaseScriptContext<?>, AdditionalContextInfo> additionalContextInfo = new WeakHashMap<>();

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

    @Nullable
    static ThreadInstance currentThread() {
        AdditionalContextInfo additionalContext = additionalContext();
        ThreadInstance thread = additionalContext.currentClientcommandsThread.get();
        if (thread != null) {
            return thread;
        }
        Thread currentThread = Thread.currentThread();
        if (currentThread != scriptContext().getMainThread()) {
            return null;
        }
        thread = new ScriptThread(() -> null, false).thread;
        thread.mainThread = new WeakReference<>(currentThread);
        runThread(thread, true);
        return thread;
    }

    static ThreadInstance requireCurrentThread() {
        ThreadInstance thread = currentThread();
        if (thread == null) {
            throw new IllegalStateException("This operation must be called in a clientcommands thread or the main thread");
        }
        return thread;
    }

    @Nullable
    static ThreadInstance getFirstRunningThread() {
        AdditionalContextInfo additionalContext = additionalContext();
        Iterator<ThreadInstance> threadItr = additionalContext.runningClientcommandsThreads.iterator();
        if (!threadItr.hasNext()) {
            return null;
        }

        ThreadInstance thread = threadItr.next();
        if (thread.mainThread != null || isMainThreadRunning()) {
            return thread;
        }

        threadItr.remove();
        if (!threadItr.hasNext()) {
            return null;
        }
        return threadItr.next();
    }

    static boolean isMainThreadRunning() {
        Thread mainThread = scriptContext().getMainThread();
        return mainThread != null && mainThread.isAlive();
    }

    @Nullable
    static EventContainer<?> currentContext() {
        return scriptContext().getBoundEvents().get(Thread.currentThread());
    }

    static BaseScriptContext<?> scriptContext() {
        Thread current = Thread.currentThread();
        return Core.instance.getContexts().stream().filter(e -> e.getBoundThreads().contains(current)).findFirst().orElseThrow();
    }

    static AdditionalContextInfo additionalContext() {
        return additionalContextInfo.computeIfAbsent(scriptContext(), k -> new AdditionalContextInfo());
    }

    static <T> T getBinding(String name) {
        Context context = (Context) scriptContext().getContext();
        if (context == null) {
            throw new IllegalStateException("Could not get " + name + " because context is null");
        }
        return context.getBindings("js").getMember(name).asHostObject();
    }

    static FJsMacros jsMacros() {
        return getBinding("JsMacros");
    }

    static FWrapper javaWrapper() {
        return getBinding("JavaWrapper");
    }

    public static void executeScript(String scriptFile) throws CommandSyntaxException {
        if (!Files.exists(JsMacros.core.config.macroFolder.toPath().resolve(scriptFile))) {
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptFile);
        }

        JsMacros.core.exec(new ScriptTrigger(ScriptTrigger.TriggerType.EVENT, "", scriptFile, true), null);
    }

    public static void executeLegacyScript(String scriptName) throws CommandSyntaxException {
        String scriptSource = legacyScripts.get(scriptName);
        if (scriptSource == null)
            throw SCRIPT_NOT_FOUND_EXCEPTION.create(scriptName);

        language.trigger(scriptSource, legacyScriptsDir.resolve(scriptName).toFile(), null, null);
    }

    static ThreadInstance createThread(ScriptThread handle, Callable<Void> task, boolean daemon) {
        ThreadInstance thread = new ThreadInstance();
        thread.handle = handle;
        thread.onRun = task;
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

    static void runThread(ThreadInstance thread, boolean mainThread) {
        ThreadInstance parentThread = mainThread ? null : currentThread();

        TaskManager.addTask("cscript", thread.task);
        runningThreads.add(thread);
        thread.running = true;

        if (parentThread != null) {
            parentThread.children.add(thread);
            thread.parent = parentThread;
        }

        if (mainThread) {
            AdditionalContextInfo additionalContext = additionalContext();
            additionalContext.currentClientcommandsThread.set(thread);
            // insert main thread at the start of the running clientcommands threads
            ArrayList<ThreadInstance> copy = new ArrayList<>(additionalContext.runningClientcommandsThreads);
            additionalContext.runningClientcommandsThreads.clear();
            additionalContext.runningClientcommandsThreads.add(thread);
            additionalContext.runningClientcommandsThreads.addAll(copy);
        } else {
            FWrapper javaWrapper = javaWrapper();
            FWrapper.WrappedThread currentTask = javaWrapper.tasks.peek();
            assert currentTask != null && currentTask.thread == Thread.currentThread();
            // Hack: clear the task list to make our task run straight away
            List<FWrapper.WrappedThread> oldTasks = new ArrayList<>(javaWrapper.tasks);
            javaWrapper.tasks.clear();

            Semaphore threadStarted = new Semaphore(0);

            Context context = (Context) scriptContext().getContext();
            assert context != null;

            context.leave();

            javaWrapper.methodToJavaAsync(args -> {
                // add back the tasks we cleared.
                // note that this adds the parent thread task first,
                javaWrapper().tasks.addAll(oldTasks);
                threadStarted.release();

                AdditionalContextInfo additionalContext = additionalContext();
                additionalContext.currentClientcommandsThread.set(thread);
                additionalContext.runningClientcommandsThreads.add(thread);

                try {
                    thread.onRun.call();
                } catch (Throwable e) {
                    if (!thread.killed && !thread.task.isCompleted()) {
                        Core.instance.profile.logError(e);
                    }
                }

                runningThreads.remove(thread);
                additionalContext.runningClientcommandsThreads.remove(thread);
                additionalContext.currentClientcommandsThread.set(null);

                if (thread.parent != null) {
                    thread.parent.children.remove(thread);
                }
                for (ThreadInstance child : thread.children) {
                    child.parent = null;
                    if (child.daemon) {
                        child.killed = true;
                    }
                }
                thread.running = false;

                return null;
            }).run();

            // Wait for started thread to either finish or reach a tick() method
            try {
                threadStarted.acquire();

                FWrapper.WrappedThread joinable = javaWrapper.tasks.peek();
                while (true) {
                    assert joinable != null;
                    if (joinable.thread == Thread.currentThread()) break;
                    joinable.waitFor();
                    joinable = javaWrapper.tasks.peek();
                }
            } catch (InterruptedException e) {
                thread.kill();
            }

            context.enter();
        }
    }

    static void passTick() {
        ThreadInstance thread = requireCurrentThread();
        try {
            if (thread == getFirstRunningThread()) {
                jsMacros().waitForEvent("JoinedTick", null, javaWrapper().methodToJava(args -> {
                    EventContainer<?> context = currentContext();
                    if (context != null) {
                        context.releaseLock();
                    }
                    try {
                        javaWrapper().deferCurrentTask();
                    } catch (InterruptedException e) {
                        thread.kill();
                    }
                    return null;
                }));
            } else {
                javaWrapper().deferCurrentTask();
            }
        } catch (InterruptedException e) {
            thread.kill();
        }
        if (thread.daemon && thread.parent != null && thread.parent.isKilled()) {
            thread.parent = null;
            thread.kill();
        }
        if (thread.killed || thread.task.isCompleted()) {
            throw new ScriptInterruptedException();
        }
    }

    static void blockInput(boolean blockInput) {
        requireCurrentThread().blockingInput = blockInput;
    }

    static boolean isCurrentScriptBlockingInput() {
        return requireCurrentThread().blockingInput;
    }

    public static boolean blockingInput() {
        return anyRunningThread(t -> t.blockingInput);
    }

    static Input getScriptInput() {
        return requireCurrentThread().input;
    }

    public static void copyScriptInputToPlayer(boolean inSneakingPose) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Input playerInput = player.input;
        forEachRunningThread(thread -> {
            playerInput.pressingForward |= thread.input.pressingForward;
            playerInput.pressingBack |= thread.input.pressingBack;
            playerInput.pressingLeft |= thread.input.pressingLeft;
            playerInput.pressingRight |= thread.input.pressingRight;
            playerInput.jumping |= thread.input.jumping;
            playerInput.sneaking |= thread.input.sneaking;
        });
        playerInput.movementForward = playerInput.pressingForward ^ playerInput.pressingBack ? (playerInput.pressingForward ? 1 : -1) : 0;
        playerInput.movementSideways = playerInput.pressingLeft ^ playerInput.pressingRight ? (playerInput.pressingLeft ? 1 : -1) : 0;
        if (playerInput.sneaking || inSneakingPose) {
            playerInput.movementSideways = (float)(playerInput.movementSideways * 0.3D);
            playerInput.movementForward = (float)(playerInput.movementForward * 0.3D);
        }
    }

    static void setSprinting(boolean sprinting) {
        requireCurrentThread().sprinting = sprinting;
    }

    static boolean isCurrentThreadSprinting() {
        return requireCurrentThread().sprinting;
    }

    public static boolean isSprinting() {
        return anyRunningThread(t -> t.sprinting);
    }

    private static void forEachRunningThread(Consumer<ThreadInstance> consumer) {
        anyRunningThread(t -> {
            consumer.accept(t);
            return false;
        });
    }

    private static boolean anyRunningThread(Predicate<ThreadInstance> predicate) {
        boolean result = false;
        Iterator<ThreadInstance> itr = runningThreads.iterator();
        while (itr.hasNext()) {
            ThreadInstance thread = itr.next();
            if (thread.isKilled()) {
                itr.remove();
            } else {
                result |= predicate.test(thread);
            }
        }
        return result;
    }

    static class ThreadInstance {
        ScriptThread handle;

        // A reference to the current thread if this is the main thread
        WeakReference<Thread> mainThread = null;
        Callable<Void> onRun;
        boolean daemon;
        boolean paused;
        private boolean killed;
        ThreadInstance parent;
        List<ThreadInstance> children = new ArrayList<>(0);

        boolean running;
        private LongTask task;
        private boolean blockingInput = false;
        @SuppressWarnings("NewExpressionSideOnly")
        private final Input input = new Input();
        private boolean sprinting = false;

        boolean isKilled() {
            if (killed) {
                return true;
            }
            if (mainThread != null) {
                Thread mainThread = this.mainThread.get();
                if (mainThread == null || !mainThread.isAlive()) {
                    running = false;
                    return killed = true;
                }
            }
            return false;
        }

        void kill() {
            killed = true;
        }
    }

    static class AdditionalContextInfo {
        Set<ThreadInstance> runningClientcommandsThreads = new LinkedHashSet<>();
        ThreadLocal<ThreadInstance> currentClientcommandsThread = new ThreadLocal<>();
    }

}
