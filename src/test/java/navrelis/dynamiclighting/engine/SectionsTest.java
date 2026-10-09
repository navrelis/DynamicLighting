package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class SectionsTest {
	private static final int NO_MIN = Integer.MIN_VALUE;
	private static final int NO_MAX = Integer.MAX_VALUE;

	/**
	 * Reach of luminance 15 with the default fall-off of 2 per block.
	 */
	private static final double SHORT_REACH = 15 / 2.0;
	/**
	 * Reach of luminance 15 with the long range fall-off of 1 per block.
	 */
	private static final double LONG_REACH = 15 / 1.0;

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
	void reachWithMarginInTheMiddleOfASectionStaysInIt() {
		// 8 blocks each way from the middle: exactly 0..16.
		assertEquals(Set.of("0,0,0"), rebuilt(8.0, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(Set.of("3,4,-2"), rebuilt(56.0, 72.0, -24.0, SHORT_REACH, NO_MIN, NO_MAX));
	}

	@Test
	void reachOfSixteenBlocksNeverTouchesThreeSectionsOnAnAxis() {
		// 8..24 on X: sections 0 and 1, not 2 although the reach ends on its border.
		assertEquals(Set.of("0,0,0", "1,0,0"), rebuilt(16.0, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,0,0", "1,0,0"), rebuilt(8.01, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(Set.of("-1,0,0", "0,0,0"), rebuilt(7.99, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));

		for (double x = -40.0; x <= 40.0; x += 0.37) {
			int count = rebuilt(x, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX).size();
			assertTrue(count == 1 || count == 2, count + " sections on X at " + x);
		}
	}

	@Test
	void smallLightsTouchFewSections() {
		// Luminance 4, reach 2.
		assertEquals(Set.of("0,4,0"), rebuilt(8.0, 70.0, 8.0, 2.0, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,4,0", "1,4,0"), rebuilt(14.0, 70.0, 8.0, 2.0, NO_MIN, NO_MAX));
		// 13.5 + 2 + 0.5 ends on the border.
		assertEquals(Set.of("0,4,0"), rebuilt(13.5, 70.0, 8.0, 2.0, NO_MIN, NO_MAX));
	}

	@Test
	void negativeCoordinates() {
		assertEquals(Set.of("-1,-1,-1"), rebuilt(-8.0, -8.0, -8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(
			Set.of("-1,4,-1", "-1,4,0", "0,4,-1", "0,4,0"),
			rebuilt(-0.5, 70.0, -0.5, 3.5, NO_MIN, NO_MAX)
		);
		// -24..-8 on X.
		assertEquals(Set.of("-2,0,0", "-1,0,0"), rebuilt(-16.0, 8.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(Set.of("-1875000,-4,1874999"), rebuilt(-29_999_992.0, -56.0, 29_999_992.0, SHORT_REACH, NO_MIN, NO_MAX));
	}

	@Test
	void sectionYIsLimitedToTheLevel() {
		// Overworld: sections -4..19.
		assertEquals(Set.of("0,19,0"), rebuilt(8.0, 318.0, 8.0, SHORT_REACH, -4, 19));
		assertEquals(Set.of("0,19,0", "0,20,0"), rebuilt(8.0, 318.0, 8.0, SHORT_REACH, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,-4,0"), rebuilt(8.0, -62.0, 8.0, SHORT_REACH, -4, 19));
		assertEquals(Set.of(), rebuilt(8.0, 400.0, 8.0, SHORT_REACH, -4, 19));
		assertEquals(Set.of(), rebuilt(8.0, -100.0, 8.0, SHORT_REACH, -4, 19));
		// Just above the top section the reach still gets down into it.
		assertEquals(Set.of("0,19,0"), rebuilt(8.0, 327.0, 8.0, SHORT_REACH, -4, 19));
		assertEquals(Set.of(), rebuilt(8.0, 328.0, 8.0, SHORT_REACH, -4, 19));
	}

	@Test
	void cornerSectionsOfTheBoxAreLeftOutWhenTheLightCannotReachThem() {
		// The box of this light touches the 2x2x2 sections around the corner at 0,64,0. Its
		// neighbours on the three axes begin 2.5, 5.5 and 4.5 blocks away; the section that is all
		// three away at once is out of reach.
		Set<String> sections = rebuilt(3.0, 70.0, -5.0, SHORT_REACH, NO_MIN, NO_MAX);
		assertEquals(
			Set.of("-1,3,-1", "-1,4,-1", "-1,4,0", "0,3,-1", "0,3,0", "0,4,-1", "0,4,0"),
			sections
		);
		assertFalse(sections.contains("-1,3,0"));

		// Long range, near the low corner of a section: 27 sections in the box, and the seven in
		// which two or three axes are 13.5 blocks away are out of reach.
		Set<String> longRange = rebuilt(2.0, 2.0, 2.0, LONG_REACH, NO_MIN, NO_MAX);
		assertEquals(20, longRange.size());
		assertTrue(longRange.contains("-1,-1,-1"));
		assertTrue(longRange.contains("1,0,0"));
		assertTrue(longRange.contains("1,-1,-1"));
		assertFalse(longRange.contains("1,1,0"));
		assertFalse(longRange.contains("1,0,1"));
		assertFalse(longRange.contains("1,1,1"));
	}

	@Test
	void longRangeTouchesUpToThreeSectionsOnAnAxis() {
		// From the middle of a section every one of the 27 around it is in reach.
		Set<String> middle = rebuilt(8.0, 8.0, 8.0, LONG_REACH, NO_MIN, NO_MAX);
		assertEquals(27, middle.size());
		assertTrue(middle.contains("-1,-1,-1"));
		assertTrue(middle.contains("1,1,1"));

		// -15..16 on X: the high edge lies on a border, so only two sections there. Three on Z, and Y
		// is limited to one.
		assertEquals(
			Set.of("-1,0,-1", "-1,0,0", "-1,0,1", "0,0,-1", "0,0,0", "0,0,1"),
			rebuilt(0.5, 8.0, 8.0, LONG_REACH, 0, 0)
		);
		// A little further and the third section on X is reached, but only straight ahead.
		assertEquals(
			Set.of("-1,0,-1", "-1,0,0", "-1,0,1", "0,0,-1", "0,0,0", "0,0,1", "1,0,0"),
			rebuilt(0.51, 8.0, 8.0, LONG_REACH, 0, 0)
		);

		// With the level limit a light near the top loses the sections above it.
		assertEquals(18, rebuilt(8.0, 312.0, 8.0, LONG_REACH, -4, 19).size());
	}

	@Test
	void everyBlockWithALitNeighbourIsInARebuiltSection() {
		assertCoversLitNeighbours(13.3, 65.2, -2.7, 7.0);
		assertCoversLitNeighbours(3.0, 70.0, -5.0, SHORT_REACH);
		assertCoversLitNeighbours(15.9, 64.1, 0.1, 6.5);
		assertCoversLitNeighbours(2.0, 66.0, 2.0, LONG_REACH);
		assertCoversLitNeighbours(-7.4, 79.6, 23.2, 14.0);
	}

	@Test
	void faceJustAcrossASectionBorderIsRebuilt() {
		// The last block before the border at X 16 is lit: its centre is 6.74 blocks from the light.
		// The first block behind the border samples it for its west face, although its own centre
		// is 7.73 blocks away and no block centre of its section is within reach plus the margin.
		double x = 8.8;
		double reach = 7.0;
		assertTrue(distance(x, 8.0, 8.0, 15.5, 8.5, 8.5) < reach);
		assertTrue(distance(x, 8.0, 8.0, 16.5, 8.5, 8.5) > reach + LightTracker.REBUILD_MARGIN);

		assertEquals(Set.of("0,0,0", "1,0,0"), rebuilt(x, 8.0, 8.0, reach, NO_MIN, NO_MAX));
		// Half a block further back the lit centre is out of reach, and so is the section.
		assertEquals(Set.of("0,0,0"), rebuilt(8.5, 8.0, 8.0, reach, NO_MIN, NO_MAX));
	}

	@Test
	void sectionsWithAPointInReachForTheLookupTable() {
		// Without the margin: 0.5..15.5 around the middle.
		assertEquals(Set.of("0,0,0"), collect(8.0, 8.0, 8.0, SHORT_REACH, 0.0, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,0,0"), collect(8.4, 8.0, 8.0, SHORT_REACH, 0.0, NO_MIN, NO_MAX));
		assertEquals(Set.of("0,0,0", "1,0,0"), collect(8.6, 8.0, 8.0, SHORT_REACH, 0.0, NO_MIN, NO_MAX));
		// A reach too short for any block centre still has its own section.
		assertEquals(Set.of("0,0,0"), collect(8.0, 8.0, 8.0, 0.25, 0.0, NO_MIN, NO_MAX));
		// On a corner even the shortest reach touches all eight sections around it.
		assertEquals(8, collect(16.0, 16.0, 16.0, 0.25, 0.0, NO_MIN, NO_MAX).size());
	}

	@Test
	void noReachNoSections() {
		assertEquals(Set.of(), rebuilt(8.0, 8.0, 8.0, 0.0, NO_MIN, NO_MAX));
		assertEquals(Set.of(), rebuilt(8.0, 8.0, 8.0, -1.0, NO_MIN, NO_MAX));
		assertEquals(Set.of(), rebuilt(8.0, 8.0, 8.0, Double.NaN, NO_MIN, NO_MAX));
	}

	@Test
	void positionsFarOutsideAnyWorldAreRejectedAndNeverLoop() {
		assertTrue(Sections.isValidPosition(0.0, 0.0, 0.0));
		assertTrue(Sections.isValidPosition(3.0e7, -2032.0, -3.0e7));
		assertTrue(Sections.isValidPosition(Sections.MAX_COORDINATE, Sections.MAX_COORDINATE, -Sections.MAX_COORDINATE));

		// The second value is where the section arithmetic used to wrap around into an endless loop.
		double[] broken = {3.3e7, -3.3e7, 3.4e10, -3.4e10, 1.0e15, -1.0e15, Double.MAX_VALUE, -Double.MAX_VALUE,
			Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};

		for (double value : broken) {
			assertFalse(Sections.isValidPosition(value, 64.0, 0.0), "x " + value);
			assertFalse(Sections.isValidPosition(0.0, value, 0.0), "y " + value);
			assertFalse(Sections.isValidPosition(0.0, 64.0, value), "z " + value);

			assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
				assertEquals(Set.of(), rebuilt(value, 64.0, 0.0, LONG_REACH, NO_MIN, NO_MAX), "x " + value);
				assertEquals(Set.of(), rebuilt(0.0, value, 0.0, LONG_REACH, NO_MIN, NO_MAX), "y " + value);
				assertEquals(Set.of(), rebuilt(0.0, 64.0, value, LONG_REACH, NO_MIN, NO_MAX), "z " + value);
				assertEquals(Set.of(), collect(value, value, value, LONG_REACH, 0.0, NO_MIN, NO_MAX), "all " + value);
			});
		}
	}

	/**
	 * Checks that every block that samples a lit position lies in a section collected for a rebuild.
	 * A block samples its own position and the 26 around it: the ones across its faces and, with
	 * smooth lighting, the diagonal ones.
	 */
	private static void assertCoversLitNeighbours(double x, double y, double z, double reach) {
		Set<String> sections = rebuilt(x, y, z, reach, NO_MIN, NO_MAX);
		int range = (int) Math.ceil(reach) + 2;
		int lit = 0;

		for (int bx = (int) Math.floor(x) - range; bx <= Math.floor(x) + range; bx++) {
			for (int by = (int) Math.floor(y) - range; by <= Math.floor(y) + range; by++) {
				for (int bz = (int) Math.floor(z) - range; bz <= Math.floor(z) + range; bz++) {
					if (distance(x, y, z, bx + 0.5, by + 0.5, bz + 0.5) >= reach) {
						continue;
					}

					lit++;

					for (int ox = -1; ox <= 1; ox++) {
						for (int oy = -1; oy <= 1; oy++) {
							for (int oz = -1; oz <= 1; oz++) {
								String section = ((bx + ox) >> 4) + "," + ((by + oy) >> 4) + "," + ((bz + oz) >> 4);
								assertTrue(sections.contains(section), "missing section " + section + " for a light at " + x + "," + y + "," + z);
							}
						}
					}
				}
			}
		}

		assertTrue(lit > 100, "lit positions: " + lit);
	}

	private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		double dx = x1 - x2;
		double dy = y1 - y2;
		double dz = z1 - z2;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * The sections queued for a rebuild: reach plus the margin for sampled neighbours.
	 */
	private static Set<String> rebuilt(double x, double y, double z, double reach, int minSectionY, int maxSectionY) {
		return collect(x, y, z, reach, LightTracker.REBUILD_MARGIN, minSectionY, maxSectionY);
	}

	private static Set<String> collect(double x, double y, double z, double reach, double grow, int minSectionY, int maxSectionY) {
		Set<String> sections = new TreeSet<>();
		int[] count = {0};

		Sections.collect(x, y, z, reach, grow, minSectionY, maxSectionY, key -> {
			sections.add(SectionKey.x(key) + "," + SectionKey.y(key) + "," + SectionKey.z(key));
			count[0]++;
		});

		assertEquals(count[0], sections.size(), "a section was reported twice");
		return sections;
	}
}
