package navrelis.dynamiclighting.engine;

/**
 * Which 16-block sections a light can reach.
 * <p>
 * Edges are treated as open: a reach that ends exactly on a section border does not touch the
 * section behind that border.
 */
final class Sections {
	/**
	 * Largest coordinate, on any axis, at which a light is accepted. The world ends at 30 million
	 * blocks; beyond a few billion the section arithmetic below would overflow.
	 */
	static final double MAX_COORDINATE = 3.2e7;

	private static final double BLOCKS_TO_SECTIONS = 1.0 / 16.0;

	/**
	 * Receives the key of each section.
	 */
	@FunctionalInterface
	interface Sink {
		void add(long sectionKey);
	}

	private Sections() {
	}

	/**
	 * Whether a light may be placed here: all three coordinates are numbers within
	 * {@link #MAX_COORDINATE} of the origin.
	 */
	static boolean isValidPosition(double x, double y, double z) {
		return Math.abs(x) <= MAX_COORDINATE && Math.abs(y) <= MAX_COORDINATE && Math.abs(z) <= MAX_COORDINATE;
	}

	/**
	 * The lowest section touched by a box whose low edge is at {@code low}.
	 */
	static int min(double low) {
		return (int) Math.floor(low * BLOCKS_TO_SECTIONS);
	}

	/**
	 * The highest section touched by a box whose high edge is at {@code high}.
	 */
	static int max(double high) {
		return (int) Math.ceil(high * BLOCKS_TO_SECTIONS) - 1;
	}

	/**
	 * Hands {@code sink} the key of every section that comes closer than {@code reach} blocks to
	 * the given point, measured to the section's cube grown by {@code grow} blocks on every side.
	 * <p>
	 * With {@code grow} 0 these are the sections in which some point is within reach. With 0.5 the
	 * centres of the blocks just outside the section count too: the positions its outermost blocks
	 * sample for their faces and, with smooth lighting, their corners.
	 * <p>
	 * Sections below {@code minSectionY} or above {@code maxSectionY} are left out, and so is
	 * everything for a point that is not a {@linkplain #isValidPosition valid position}.
	 */
	static void collect(double x, double y, double z, double reach, double grow, int minSectionY, int maxSectionY, Sink sink) {
		if (!(reach > 0.0) || !isValidPosition(x, y, z)) {
			return;
		}

		double extent = reach + grow;
		double reachSquared = reach * reach;
		int minY = Math.max(min(y - extent), minSectionY);
		int maxY = Math.min(max(y + extent), maxSectionY);
		int minX = min(x - extent);
		int maxX = max(x + extent);
		int minZ = min(z - extent);
		int maxZ = max(z + extent);

		for (int sectionX = minX; sectionX <= maxX; sectionX++) {
			double gapX = gap(x, sectionX, grow);
			double squaredX = gapX * gapX;

			if (squaredX >= reachSquared) {
				continue;
			}

			for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
				double gapZ = gap(z, sectionZ, grow);
				double squaredXZ = squaredX + gapZ * gapZ;

				if (squaredXZ >= reachSquared) {
					continue;
				}

				for (int sectionY = minY; sectionY <= maxY; sectionY++) {
					double gapY = gap(y, sectionY, grow);

					if (squaredXZ + gapY * gapY < reachSquared) {
						sink.add(SectionKey.of(sectionX, sectionY, sectionZ));
					}
				}
			}
		}
	}

	/**
	 * Distance on one axis from {@code position} to a section grown by {@code grow} blocks on both
	 * sides, 0 inside it.
	 */
	private static double gap(double position, int section, double grow) {
		double low = section * 16.0 - grow;
		double high = section * 16.0 + 16.0 + grow;
		return position < low ? low - position : position > high ? position - high : 0.0;
	}
}
