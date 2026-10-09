package navrelis.dynamiclighting.engine;

import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Which 16-block sections an axis-aligned box around a point touches.
 * <p>
 * The box is treated as open: a box that ends exactly on a section border does not touch the section
 * behind that border, so a box of exactly 16 blocks never touches three sections on one axis.
 */
final class Sections {
	private static final double BLOCKS_TO_SECTIONS = 1.0 / 16.0;

	private Sections() {
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
	 * Adds to {@code out} the key of every section touched by the box that reaches {@code extent}
	 * blocks from the given point on every axis. Sections below {@code minSectionY} or above
	 * {@code maxSectionY} are left out.
	 */
	static void collect(double x, double y, double z, double extent, int minSectionY, int maxSectionY, LongSet out) {
		int minY = Math.max(min(y - extent), minSectionY);
		int maxY = Math.min(max(y + extent), maxSectionY);

		if (minY > maxY) {
			return;
		}

		int minX = min(x - extent);
		int maxX = max(x + extent);
		int minZ = min(z - extent);
		int maxZ = max(z + extent);

		for (int sectionX = minX; sectionX <= maxX; sectionX++) {
			for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
				for (int sectionY = minY; sectionY <= maxY; sectionY++) {
					out.add(SectionKey.of(sectionX, sectionY, sectionZ));
				}
			}
		}
	}
}
