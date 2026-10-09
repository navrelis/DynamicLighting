package navrelis.dynamiclighting.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import navrelis.dynamiclighting.engine.LightHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds dynamic light to the packed light of a block position. Vanilla chunk meshing, Sodium's light
 * cache, block entities and particles all take their light from this one method.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@ModifyReturnValue(
		method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN")
	)
	private static int dynamiclighting$addDynamicLight(int packed, BlockAndTintGetter level, BlockState state, BlockPos pos) {
		return LightHooks.lightColor(packed, level, state, pos);
	}
}
