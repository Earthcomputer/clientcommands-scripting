package net.earthcomputer.clientcommands.script.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.script.ScriptCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientCommands.class, remap = false)
public class MixinClientCommands {
    @Inject(method = "registerCommands", at = @At("TAIL"))
    private static void onRegisterCommands(CommandDispatcher<ServerCommandSource> dispatcher, CallbackInfo ci) {
        ScriptCommand.register(dispatcher);
    }
}
