package navrelis.dynamiclighting.config;

import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * The mod options as one immutable value behind one volatile reference.
 * Safe to read from any thread; a change applies at once.
 *
 * @param mode                 overall mode
 * @param range                how far light reaches
 * @param entityLights         entities other than the local player may emit
 * @param selfLight            the local player may emit
 * @param waterSensitive       water-sensitive items go dark under water
 * @param glowingEntities      living entities with the glowing outline emit
 * @param creeper              fuse behaviour of ignited creepers
 * @param tnt                  fuse behaviour of primed TNT
 * @param disabledEntityTypes  entity type ids that never emit
 */
public record Options(
	Mode mode,
	LightRange range,
	boolean entityLights,
	boolean selfLight,
	boolean waterSensitive,
	boolean glowingEntities,
	FuseMode creeper,
	FuseMode tnt,
	Set<ResourceLocation> disabledEntityTypes
) {
	public static final Options DEFAULT = new Options(
		Mode.FANCY, LightRange.SHORT, true, true, true, true, FuseMode.SIMPLE, FuseMode.SIMPLE, Set.of()
	);

	private static volatile Options current = DEFAULT;

	public Options {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(range, "range");
		Objects.requireNonNull(creeper, "creeper");
		Objects.requireNonNull(tnt, "tnt");
		Objects.requireNonNull(disabledEntityTypes, "disabledEntityTypes");
		disabledEntityTypes = Set.copyOf(disabledEntityTypes);
	}

	public static Options get() {
		return current;
	}

	public static void set(Options options) {
		current = Objects.requireNonNull(options, "options");
	}
}
