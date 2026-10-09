package navrelis.dynamiclighting.luminance;

import net.minecraft.world.entity.Entity;

/**
 * One way an entity can emit light. Implementations hold no per-entity state and allocate nothing.
 */
@FunctionalInterface
interface EntityLight {
	/**
	 * @param entity the entity to look at; an implementation made for another entity class returns 0
	 * @param rules  the item table and options in effect
	 * @return luminance 0 to 15
	 */
	int luminance(Entity entity, LightRules rules);
}
