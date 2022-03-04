package net.earthcomputer.clientcommands.script.mixin;

import net.earthcomputer.clientcommands.script.ClientCommandsScripting;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Unique private boolean wasSprintPressed = false;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    public void onStartTickMovement(CallbackInfo ci) {
        if (ClientCommandsScripting.isJsMacrosPresent) {
            wasSprintPressed = MinecraftClient.getInstance().options.sprintKey.isPressed();
            boolean shouldBeSprinting = (wasSprintPressed && !ScriptManager.blockingInput()) || ScriptManager.isSprinting();
            ((KeyBindingAccessor) MinecraftClient.getInstance().options.sprintKey).setPressed(shouldBeSprinting);
        }
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    public void onEndTickMovement(CallbackInfo ci) {
        if (ClientCommandsScripting.isJsMacrosPresent) {
            ((KeyBindingAccessor) MinecraftClient.getInstance().options.sprintKey).setPressed(wasSprintPressed);
        }
    }
}
