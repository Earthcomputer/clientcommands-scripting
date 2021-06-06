package net.earthcomputer.clientcommands.script;

import net.minecraft.client.MinecraftClient;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;
import xyz.wagyourtail.jsmacros.core.library.Library;

import java.util.concurrent.Callable;

@SuppressWarnings("unused")
@Library("cc")
public class ClientCommandsLibrary extends BaseLibrary {
    public Object exec(String command) {
        return ScriptBuiltins.exec(command);
    }

    public void print(String message) {
        ScriptBuiltins.print(message);
    }

    public void chat(String message) {
        ScriptBuiltins.chat(message);
    }

    public void tick() {
        ScriptManager.passTick();
    }

    public boolean isLoggedIn() {
        return MinecraftClient.getInstance().player != null;
    }

    public Object player = BeanWrapper.wrap(ScriptPlayer.INSTANCE);
    public Object world = BeanWrapper.wrap(ScriptWorld.INSTANCE);

    public ThreadLibrary Thread = new ThreadLibrary();
    public BlockStateLibrary BlockState = new BlockStateLibrary();
    public ItemStackLibrary ItemStack = new ItemStackLibrary();

    public static class ThreadLibrary extends BaseLibrary {
        public ScriptThread current() {
            return ScriptThread.current();
        }

        public ScriptThread create(Callable<Void> task) {
            return new ScriptThread(task);
        }

        public ScriptThread create(Callable<Void> task, boolean daemon) {
            return new ScriptThread(task, daemon);
        }
    }

    public static class BlockStateLibrary extends BaseLibrary {
        public Object defaultState(String block) {
            return ScriptBlockState.defaultState(block);
        }
    }

    public static class ItemStackLibrary extends BaseLibrary {
        public Object of(Object obj) {
            return ScriptItemStack.of(obj);
        }
    }
}
