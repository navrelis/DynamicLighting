package navrelis.dynamiclighting.engine;

/**
 * Packs the coordinates of a 16-block section into one {@code long}.
 * <p>
 * Layout from the top bit down: one bit that is always zero, 26 bits X, 26 bits Z, 11 bits Y, each
 * in two's complement. That holds sections -33554432..33554431 on X and Z (the legal world needs
 * -1875000..1874999) and -1024..1023 on Y (the legal world needs -127..126). Because the top bit of
 * a real key is never set, {@link #EMPTY} can never be mistaken for one.
 * <p>
 * Coordinates outside those ranges wrap. That is harmless for the light lookup, which always checks
 * the real distance to each light it finds.
 */
final class SectionKey {
	/**
	 * Marks an unused slot in a key table.
	 */
	static final long EMPTY = -1L;

	static final int MIN_XZ = -(1 << 25);
	static final int MAX_XZ = (1 << 25) - 1;
	static final int MIN_Y = -(1 << 10);
	static final int MAX_Y = (1 << 10) - 1;

	private static final long XZ_MASK = (1L << 26) - 1;
	private static final long Y_MASK = (1L << 11) - 1;
	private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

	private SectionKey() {
	}

	static long of(int sectionX, int sectionY, int sectionZ) {
		return (sectionX & XZ_MASK) << 37 | (sectionZ & XZ_MASK) << 11 | (sectionY & Y_MASK);
	}

	static long ofBlock(int blockX, int blockY, int blockZ) {
		return of(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	static int x(long key) {
		return (int) (key << 1 >> 38);
	}

	static int y(long key) {
		return (int) (key << 53 >> 53);
	}

	static int z(long key) {
		return (int) (key << 27 >> 38);
	}

	/**
	 * Returns the first slot to probe in a table of {@code 1 << (64 - shift)} slots.
	 */
	static int slot(long key, int shift) {
		return (int) (key * HASH_MULTIPLIER >>> shift);
	}
}
