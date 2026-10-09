package navrelis.dynamiclighting.luminance;

import navrelis.dynamiclighting.config.FuseMode;
import net.minecraft.util.Mth;

/**
 * Light of things about to explode. Pure functions of the current fuse state: nothing is
 * remembered per entity, so a long or a short fuse cannot throw the ramp off.
 */
final class FuseLight {
	/** Light in {@link FuseMode#SIMPLE}. */
	static final int SIMPLE = 10;

	static final int TNT_MIN = 4;
	static final int TNT_MAX = 14;
	/** Fuse length, in ticks, at and above which primed TNT is at its dimmest. */
	static final int TNT_FULL_FUSE = 80;

	static final int CREEPER_MIN = 2;
	static final int CREEPER_MAX = 12;

	private FuseLight() {
	}

	/**
	 * @param fuse remaining ticks, as {@code PrimedTnt.getFuse()} reports them
	 * @return 0 when off, else the light of primed TNT: brighter the closer it is to exploding
	 */
	static int tnt(FuseMode mode, int fuse) {
		return switch (mode) {
			case OFF -> 0;
			case SIMPLE -> SIMPLE;
			case FANCY -> {
				int burnt = TNT_FULL_FUSE - Mth.clamp(fuse, 0, TNT_FULL_FUSE);
				yield TNT_MIN + ((TNT_MAX - TNT_MIN) * burnt + TNT_FULL_FUSE / 2) / TNT_FULL_FUSE;
			}
		};
	}

	/**
	 * @param swelling {@code Creeper.getSwelling}: 0 while calm, about 1 at the explosion
	 * @return 0 when off or not swelling, else the light of an ignited creeper
	 */
	static int creeper(FuseMode mode, float swelling) {
		if (!(swelling > 0.0F)) {
			return 0;
		}

		return switch (mode) {
			case OFF -> 0;
			case SIMPLE -> SIMPLE;
			case FANCY -> CREEPER_MIN + Math.round((CREEPER_MAX - CREEPER_MIN) * Math.min(swelling, 1.0F));
		};
	}
}
