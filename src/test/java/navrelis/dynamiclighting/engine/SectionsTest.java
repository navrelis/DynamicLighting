package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

class SectionsTest {
	private static final int NO_MIN = Integer.MIN_VALUE;
	private static final int NO_MAX = Integer.MAX_VALUE;

	/**
	 * Reach of luminance 15 with the default fall-off of 2 per block, plus the rebuild margin.
	 */
	private static final double SHORT_EXTENT = 15 / 2.0 + LightTracker.REBUILD_MARGIN;
	/**
	 * Reach of luminance 15 with the long range fall-off of 1 per block, plus the rebuild margin.
	 */
	private static final double LONG_EXTENT = 15 / 1.0 + LightTracker.REBUILD_MARGIN;

	@Test
	void lowAndHighEdgesAreHalfOpen() {
		assertEquals(0, Sections.min(0.0));
		assertEquals(0, Sections.min(15.99));
		assertEquals(1, Sections.min(16.0));
		assertEquals(-1, Sections.min(-0.01));
		assertEquals(-1, Sections.min(-16.0));
		assertEquals(-2, Sections.min(-16.01));

		// A box that ends exactly on a border does not touch the section behind it.
		assertEquals(0, Sections.max(16.0));
		assertEquals(1, Sections.max(16.01));
		assertEquals(0, Sections.max(0.01));
		assertEquals(-1, Sections.max(0.0));
		assertEquals(-1, Sections.max(-0.01));
		assertEquals(-2, Sections.max(-16.0));
	}

	@Test
	void reachBoxWithMarginInTheMiddleOfASectionStaysInIt() {
		// 8 blocks each way from the middle: exactly 0..16.
		assertEquals(Set.of("0,0,0"), collect(8.0, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(Set.of("3,4,-2"), collect(56.0, 72.0, -24.0, SHORT_EXTENT, NO_MIN, NO_MAX));
	}

	@Test
	void boxOfSixteenBlocksNeverTouchesThreeSectionsOnAnAxis() {
		// 8..24 on X: sections 0 and 1, not 2 although the box ends on its border.
		assertEquals(Set.of("0,0,0", "1,0,0"), collect(16.0, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,0,0", "1,0,0"), collect(8.01, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(Set.of("-1,0,0", "0,0,0"), collect(7.99, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));

		for (double x = -40.0; x <= 40.0; x += 0.37) {
			int count = collect(x, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX).size();
			assertTrue(count == 1 || count == 2, count + " sections on X at " + x);
		}

		// Off the middle on every axis: the 2x2x2 block around the nearest corner.
		assertEquals(8, collect(3.0, 70.0, -5.0, SHORT_EXTENT, NO_MIN, NO_MAX).size());
	}

	@Test
	void smallLightsTouchFewSections() {
		// Luminance 4, reach 2, extent 2.5.
		assertEquals(Set.of("0,4,0"), collect(8.0, 70.0, 8.0, 2.5, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,4,0", "1,4,0"), collect(14.0, 70.0, 8.0, 2.5, NO_MIN, NO_MAX));
		// 13.5 + 2.5 ends on the border.
		assertEquals(Set.of("0,4,0"), collect(13.5, 70.0, 8.0, 2.5, NO_MIN, NO_MAX));
	}

	@Test
	void negativeCoordinates() {
		assertEquals(Set.of("-1,-1,-1"), collect(-8.0, -8.0, -8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(
			Set.of("-1,4,-1", "-1,4,0", "0,4,-1", "0,4,0"),
			collect(-0.5, 70.0, -0.5, 4.0, NO_MIN, NO_MAX)
		);
		// -24..-8 on X.
		assertEquals(Set.of("-2,0,0", "-1,0,0"), collect(-16.0, 8.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(Set.of("-1875000,-4,1874999"), collect(-29_999_992.0, -56.0, 29_999_992.0, SHORT_EXTENT, NO_MIN, NO_MAX));
	}

	@Test
	void sectionYIsLimitedToTheLevel() {
		// Overworld: sections -4..19.
		assertEquals(Set.of("0,19,0"), collect(8.0, 318.0, 8.0, SHORT_EXTENT, -4, 19));
		assertEquals(Set.of("0,19,0", "0,20,0"), collect(8.0, 318.0, 8.0, SHORT_EXTENT, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,-4,0"), collect(8.0, -62.0, 8.0, SHORT_EXTENT, -4, 19));
		assertEquals(Set.of(), collect(8.0, 400.0, 8.0, SHORT_EXTENT, -4, 19));
		assertEquals(Set.of(), collect(8.0, -100.0, 8.0, SHORT_EXTENT, -4, 19));
		// Just above the top section the box still reaches down into it.
		assertEquals(Set.of("0,19,0"), collect(8.0, 327.0, 8.0, SHORT_EXTENT, -4, 19));
		assertEquals(Set.of(), collect(8.0, 328.0, 8.0, SHORT_EXTENT, -4, 19));
	}

	@Test
	void longRangeTouchesUpToThreeSectionsOnAnAxis() {
		// 15.5 blocks each way from the middle: -7.5..23.5.
		Set<String> middle = collect(8.0, 8.0, 8.0, LONG_EXTENT, NO_MIN, NO_MAX);
		assertEquals(27, middle.size());
		assertTrue(middle.contains("-1,-1,-1"));
		assertTrue(middle.contains("1,1,1"));

		// -15..16 on X: the high edge lies on a border, so only two sections there. Three on Z, and Y
		// is limited to one.
		assertEquals(
			Set.of("-1,0,-1", "-1,0,0", "-1,0,1", "0,0,-1", "0,0,0", "0,0,1"),
			collect(0.5, 8.0, 8.0, LONG_EXTENT, 0, 0)
		);
		assertEquals(9, collect(0.51, 8.0, 8.0, LONG_EXTENT, 0, 0).size());

		for (double x = -40.0; x <= 40.0; x += 0.37) {
			int count = collect(x, 8.0, 8.0, LONG_EXTENT, 0, 0).size();
			assertTrue(count == 2 * 3 || count == 3 * 3, count + " sections at X " + x);
		}

		// With the level limit a light near the top loses the sections above it.
		assertEquals(18, collect(8.0, 312.0, 8.0, LONG_EXTENT, -4, 19).size());
	}

	@Test
	void everyBlockWithALitNeighbourIsInACollectedSection() {
		double x = 13.3;
		double y = 65.2;
		double z = -2.7;
		double reach = 7.0;
		Set<String> sections = collect(x, y, z, reach + LightTracker.REBUILD_MARGIN, NO_MIN, NO_MAX);

		for (int bx = -20; bx <= 40; bx++) {
			for (int by = 40; by <= 90; by++) {
				for (int bz = -30; bz <= 30; bz++) {
					double dx = bx + 0.5 - x;
					double dy = by + 0.5 - y;
					double dz = bz + 0.5 - z;

					if (dx * dx + dy * dy + dz * dz >= reach * reach) {
						continue;
					}

					// A lit position is sampled by the block itself and by every block around it,
					// diagonal ones included (smooth lighting).
					for (int ox = -1; ox <= 1; ox++) {
						for (int oy = -1; oy <= 1; oy++) {
							for (int oz = -1; oz <= 1; oz++) {
								String section = ((bx + ox) >> 4) + "," + ((by + oy) >> 4) + "," + ((bz + oz) >> 4);
								assertTrue(sections.contains(section), "missing section " + section);
							}
						}
					}
				}
			}
		}
	}

	private static Set<String> collect(double x, double y, double z, double extent, int minSectionY, int maxSectionY) {
		LongOpenHashSet keys = new LongOpenHashSet();
		Sections.collect(x, y, z, extent, minSectionY, maxSectionY, keys);

		Set<String> sections = new TreeSet<>();

		for (long key : keys) {
			sections.add(SectionKey.x(key) + "," + SectionKey.y(key) + "," + SectionKey.z(key));
		}

		assertEquals(keys.size(), sections.size());
		return sections;
	}
}
