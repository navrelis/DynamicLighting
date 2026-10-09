package navrelis.dynamiclighting.luminance;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * One decoded entity file.
 *
 * @param file  where the rule comes from
 * @param types the entity types it applies to, never empty
 * @param light what entities of these types emit; flags and equipment conditions are already part of it
 */
record EntityRule(ResourceLocation file, List<EntityType<?>> types, EntityLight light) {
	EntityRule {
		types = List.copyOf(types);
	}
}
