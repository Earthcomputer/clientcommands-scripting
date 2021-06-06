package net.earthcomputer.clientcommands.script;

@FunctionalInterface
public interface ScriptFunction {
    Object call(Object... args);
}
