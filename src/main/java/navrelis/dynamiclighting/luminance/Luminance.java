package navrelis.dynamiclighting.luminance;

import net.minecraft.world.entity.Entity;

public final class Luminance {
	private Luminance() {
	}

	public static void init() {
	}

	/**
	 * Returns the luminance 0..15 this entity emits right now.
	 * <p>
	 * Called on the client thread only, once per evaluated entity. Already applies every option in
	 * {@code Options} (entity lights, self light, disabled types, glowing, fuse modes, water) and the
	 * rules for invisible entities and spectators, so callers do no filtering of their own. Must be
	 * cheap for entities that emit nothing. Never throws.
	 *
	 * @param entity the entity to evaluate
	 * @return the emitted luminance, 0 to 15
	 */
	public static int ofEntity(Entity entity) {
		return 0;
	}
}
