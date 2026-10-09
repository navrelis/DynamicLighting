package navrelis.dynamiclighting.engine;

/**
 * Minecraft's packed light: sky light from bit 20 up, block light in sixteenths of a level in the low
 * 16 bits ({@code sky << 20 | block << 4}).
 */
final class PackedLight {
	/**
	 * Block light 15 in sixteenths. Nothing can be raised above it.
	 */
	static final int FULL_BLOCK_LIGHT = 15 << 4;

	private static final int BLOCK_MASK = 0xFFFF;

	private PackedLight() {
	}

	/**
	 * The block light part in sixteenths of a level.
	 */
	static int block(int packed) {
		return packed & BLOCK_MASK;
	}

	/**
	 * Raises the block light part of {@code packed} to the dynamic light value if that is brighter.
	 * Never lowers it and never touches the sky light bits, so merging twice changes nothing.
	 *
	 * @param dynamic     dynamic light, 0 to 15
	 * @param wholeLevels round to the nearest whole level instead of keeping sixteenths
	 */
	static int merge(int packed, double dynamic, boolean wholeLevels) {
		int raised = wholeLevels ? roundToLevel(dynamic) << 4 : (int) (dynamic * 16.0);
		return raised > (packed & BLOCK_MASK) ? (packed & ~BLOCK_MASK) | raised : packed;
	}

	/**
	 * Rounds a dynamic light value of 0 to 15 to the nearest whole level.
	 */
	static int roundToLevel(double dynamic) {
		return (int) (dynamic + 0.5);
	}
}
