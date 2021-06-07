package net.earthcomputer.clientcommands.script.mixin;

import net.earthcomputer.clientcommands.script.ClientCommandsScripting;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.earthcomputer.clientcommands.script.ducks.IMinecraftClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class, priority = -1000)
public abstract class MixinMinecraftClient implements IMinecraftClient {
    @Shadow protected int attackCooldown;

    @Shadow protected abstract void handleBlockBreaking(boolean bl);

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (ClientCommandsScripting.isJsMacrosPresent) {
            ScriptManager.tick();
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0), cancellable = true)
    public void onHandleInputEvents(CallbackInfo ci) {
        if (ClientCommandsScripting.isJsMacrosPresent) {
            if (ScriptManager.blockingInput()) {
                ci.cancel();
            }
        }
    }

    @Override
    public void continueBreakingBlock() {
        handleBlockBreaking(true);
    }

    @Override
    public void resetAttackCooldown() {
        attackCooldown = 0;
    }
}
