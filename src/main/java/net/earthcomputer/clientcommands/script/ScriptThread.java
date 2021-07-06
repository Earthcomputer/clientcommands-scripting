package net.earthcomputer.clientcommands.script;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

@SuppressWarnings("unused")
public class ScriptThread {

    final ScriptManager.ThreadInstance thread;

    public ScriptThread(Callable<Void> task) {
        this(task, true);
    }

    public ScriptThread(Callable<Void> task, boolean daemon) {
        this.thread = ScriptManager.createThread(this, task, daemon);
    }

    public static ScriptThread current() {
        ScriptManager.ThreadInstance thread = ScriptManager.currentThread();
        return thread == null ? null : thread.handle;
    }

    public static ScriptThread getCurrent() {
        return current();
    }

    public boolean isRunning() {
        return thread.running && !thread.isKilled();
    }

    public boolean isPaused() {
        return thread.paused;
    }

    public boolean isDaemon() {
        return thread.daemon;
    }

    public ScriptThread getParent() {
        return thread.parent == null || thread.parent.isKilled() ? null : thread.parent.handle;
    }

    public List<ScriptThread> getChildren() {
        //noinspection StaticPseudoFunctionalStyleMethod
        return Collections.unmodifiableList(Lists.transform(thread.children, thread -> thread.handle));
    }

    public void run() {
        if (!thread.running)
            ScriptManager.runThread(thread, false);
    }

    public void pause() {
        thread.paused = true;
    }

    public void unpause() {
        thread.paused = false;
    }

    public void kill() {
        thread.kill();
    }

    public void waitFor() {
        if (thread == ScriptManager.currentThread())
            throw new IllegalStateException();
        while (thread.running)
            ScriptManager.passTick();
    }

}
