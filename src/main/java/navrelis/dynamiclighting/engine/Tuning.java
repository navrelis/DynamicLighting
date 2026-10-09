package navrelis.dynamiclighting.engine;

import navrelis.dynamiclighting.config.Mode;

/**
 * How much work each mode spends: how often an entity is looked at, how far a light may move before
 * it is published again, and how many sections are rebuilt per tick.
 */
final class Tuning {
	private static final double NEAR_SQUARED = 32.0 * 32.0;
	private static final double MIDDLE_SQUARED = 64.0 * 64.0;

	/**
	 * The smallest rebuild budget whatever the mode and the frame rate.
	 */
	static final int MIN_BUDGET = 4;
	/**
	 * Frame rate from which the whole budget of a mode is used.
	 */
	private static final double FULL_BUDGET_FPS = 90.0;
	/**
	 * Frame rate at which the straight line through the budget scale reaches zero. The scale never
	 * gets there: it stops at {@link #MIN_BUDGET_SCALE}.
	 */
	private static final double SCALE_ZERO_FPS = 15.0;
	private static final double MIN_BUDGET_SCALE = 0.25;

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
	 * Sections marked for a rebuild per tick, at most.
	 * <p>
	 * The mode sets the limit. A client that is already below a smooth frame rate gets less of it,
	 * because building more sections per tick would slow it further: all of it from 90 frames per
	 * second up, 60 % at 60, 40 % at 45, and a quarter at about 34 and below. What does not fit
	 * waits in the queue, which hands out the sections nearest to the camera first.
	 *
	 * @param fps frames drawn in the last full second, 0 or less if that is not known yet
	 * @return the budget, never below {@link #MIN_BUDGET}
	 */
	static int budget(Mode mode, int fps) {
		int base = switch (mode) {
			case FANCY -> 64;
			case FAST -> 32;
			default -> 16;
		};

		if (fps <= 0) {
			return base;
		}

		double scale = Math.max(MIN_BUDGET_SCALE, Math.min(1.0, (fps - SCALE_ZERO_FPS) / (FULL_BUDGET_FPS - SCALE_ZERO_FPS)));
		return Math.max(MIN_BUDGET, (int) Math.round(base * scale));
	}

	private static int modeFactor(Mode mode) {
		return switch (mode) {
			case FANCY -> 1;
			case FAST -> 2;
			default -> 4;
		};
	}
}
