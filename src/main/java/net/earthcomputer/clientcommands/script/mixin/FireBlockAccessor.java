package net.earthcomputer.clientcommands.script.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FireBlock.class)
public interface FireBlockAccessor {
    @Invoker
    int callGetSpreadChance(BlockState state);

    @Invoker
    int callGetBurnChance(BlockState state);
}
