package navrelis.dynamiclighting.engine;

import navrelis.dynamiclighting.config.Mode;

/**
 * How much work each mode spends: how often an entity is looked at, how far a light may move before
 * it is published again, and how many sections are rebuilt per tick.
 */
final class Tuning {
	private static final double NEAR_SQUARED = 32.0 * 32.0;
	private static final double MIDDLE_SQUARED = 64.0 * 64.0;

	private Tuning() {
	}

	/**
	 * Ticks between two evaluations of an entity at the given squared distance from the camera.
	 * Always a power of two.
	 */
	static int interval(Mode mode, double cameraDistanceSquared) {
		int distanceFactor = cameraDistanceSquared <= NEAR_SQUARED ? 1 : cameraDistanceSquared <= MIDDLE_SQUARED ? 2 : 4;
		return modeFactor(mode) * distanceFactor;
	}

	/**
	 * Whether an entity with the given interval is evaluated in this tick. Entities with the same
	 * interval are spread over its ticks by their id.
	 */
	static boolean isDue(int tick, int entityId, int interval) {
		return ((tick + entityId) & (interval - 1)) == 0;
	}

	/**
	 * How far, in blocks, a light may move from the position it was published at before it is
	 * published again. The base is the distance over which the light changes by half a level.
	 *
	 * @param wholeLevels the renderer keeps whole light levels only, so finer steps would not show
	 */
	static double moveThreshold(double falloff, Mode mode, double cameraDistance, boolean wholeLevels) {
		double threshold = 0.5 / falloff * modeFactor(mode) * (1.0 + cameraDistance / 32.0);
		return mode == Mode.FANCY && !wholeLevels ? threshold * 0.5 : threshold;
	}

	/**
	 * Sections marked for a rebuild per tick.
	 */
	static int budget(Mode mode) {
		return switch (mode) {
			case FANCY -> 64;
			case FAST -> 32;
			default -> 16;
		};
	}

	private static int modeFactor(Mode mode) {
		return switch (mode) {
			case FANCY -> 1;
			case FAST -> 2;
			default -> 4;
		};
	}
}
