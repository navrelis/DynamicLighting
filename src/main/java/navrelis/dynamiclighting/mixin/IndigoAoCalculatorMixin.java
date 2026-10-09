package navrelis.dynamiclighting.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import navrelis.dynamiclighting.engine.LightHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric's Indigo renderer meshes terrain when Sodium is not installed and, in its default setting,
 * computes packed light itself instead of asking {@code LevelRenderer}. Its other branch does ask
 * {@code LevelRenderer}; merging twice changes nothing.
 * <p>
 * Optional in every respect: Indigo is an implementation detail of Fabric API, so neither the class
 * nor the method is required to exist.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator", remap = false)
abstract class IndigoAoCalculatorMixin {
	@ModifyReturnValue(method = "getLightmapCoordinates", at = @At("RETURN"), require = 0, remap = false)
	private static int dynamiclighting$addDynamicLight(int packed, BlockAndTintGetter level, BlockState state, BlockPos pos) {
		return LightHooks.lightColor(packed, level, state, pos);
	}
}
