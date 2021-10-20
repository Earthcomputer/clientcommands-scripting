package net.earthcomputer.clientcommands.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import xyz.wagyourtail.jsmacros.client.JsMacros;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.language.BaseScriptContext;
import xyz.wagyourtail.jsmacros.core.language.impl.JavascriptLanguageDefinition;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ClientCommandsLanguage extends JavascriptLanguageDefinition {
    private static final Engine engine = Engine.create();
    private static final JavascriptLanguageDefinition jsLanguage = JsMacros.core.languages.stream()
            .filter(it -> it.getClass() == JavascriptLanguageDefinition.class)
            .findFirst()
            .map(it -> (JavascriptLanguageDefinition) it)
            .orElseThrow(AssertionError::new);

    public ClientCommandsLanguage(String extension, Core runner) {
        super(extension, runner);
    }

    @Override
    protected Context buildContext(File currentDir, Map<String, String> extraJsOptions, Map<String, Object> globals, Map<String, BaseLibrary> libs) throws IOException {
        Context.Builder build = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowAllAccess(true)
                .allowIO(true)
                .allowExperimentalOptions(true)
                .option("js.commonjs-require", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022");

        build.options(extraJsOptions);
        if (currentDir == null) {
            currentDir = runner.config.macroFolder;
        }
        build.currentWorkingDirectory(currentDir.toPath());
        build.option("js.commonjs-require-cwd", currentDir.getCanonicalPath());

        final Context con = build.build();

        // Set Bindings
        final Value binds = con.getBindings("js");

        globals.putAll(ScriptBuiltins.getGlobalFunctions());
        globals.putAll(ScriptBuiltins.getGlobalVars());
        ScriptBuiltins.getGlobalTypes().forEach((name, clazz) -> {
            globals.put(name, con.eval("js", "Java.type('" + clazz.getName() + "')"));
        });

        globals.forEach(binds::putMember);
        libs.forEach(binds::putMember);

        return con;
    }

    @Override
    public Map<String, BaseLibrary> retrieveLibs(BaseScriptContext<Context> context) {
        return jsLanguage.retrieveLibs(context);
    }

    @Override
    public Map<String, BaseLibrary> retrieveOnceLibs() {
        return jsLanguage.retrieveOnceLibs();
    }

    @Override
    public Map<String, BaseLibrary> retrievePerExecLibs(BaseScriptContext<Context> context) {
        return jsLanguage.retrievePerExecLibs(context);
    }
}
