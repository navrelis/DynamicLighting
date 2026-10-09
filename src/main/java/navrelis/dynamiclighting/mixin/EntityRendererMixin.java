package navrelis.dynamiclighting.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import navrelis.dynamiclighting.engine.LightHooks;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entities standing in dynamic light are rendered with it.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@ModifyReturnValue(
		method = "getBlockLightLevel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN")
	)
	private int dynamiclighting$addDynamicLight(int level, Entity entity, BlockPos pos) {
		return LightHooks.entityBlockLight(level, pos);
	}
}
