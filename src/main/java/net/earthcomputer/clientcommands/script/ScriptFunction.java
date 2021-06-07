package net.earthcomputer.clientcommands.script;

import org.graalvm.polyglot.Value;

@FunctionalInterface
public interface ScriptFunction {
    Value call(Object... args);
}
