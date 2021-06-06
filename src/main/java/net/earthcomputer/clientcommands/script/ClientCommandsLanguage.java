package net.earthcomputer.clientcommands.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.language.impl.JavascriptLanguageDefinition;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class ClientCommandsLanguage extends JavascriptLanguageDefinition {
    private static final Engine engine = Engine.create();

    public ClientCommandsLanguage(String extension, Core runner) {
        super(extension, runner);
    }

    @Override
    protected Context buildContext(Path currentDir, Map<String, String> extraJsOptions, Map<String, Object> globals, Map<String, BaseLibrary> libs) throws IOException {
        Context.Builder build = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowAllAccess(true)
                .allowIO(true)
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true");

        build.options(extraJsOptions);
        if (currentDir == null) {
            currentDir = runner.config.macroFolder.toPath();
        }
        build.currentWorkingDirectory(currentDir);
        build.option("js.commonjs-require-cwd", currentDir.toFile().getCanonicalPath());

        final Context con = build.build();

        // Set Bindings
        final Value binds = con.getBindings("js");

        globals.putAll(ScriptBuiltins.getGlobalFunctions());

        globals.forEach(binds::putMember);

        libs.forEach(binds::putMember);

        ScriptBuiltins.getGlobalTypes().forEach((name, clazz) -> {
            globals.put(name, con.eval("js", "Java.type('" + clazz.getName() + "')"));
        });

        return con;

    }
}
