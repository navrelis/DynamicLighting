package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LightSnapshotTest {
	private static final double EXACT = 1.0e-9;

	private final SnapshotBuilder builder = new SnapshotBuilder();

	@ParameterizedTest
	@ValueSource(doubles = {2.0, 1.0})
	void matchesBruteForceAroundTheOrigin(double falloff) {
		assertMatchesBruteForce(falloff, 0.0, 64.0, 0.0, 11L);
	}

	@ParameterizedTest
	@ValueSource(doubles = {2.0, 1.0})
	void matchesBruteForceFarOutAndBelowZero(double falloff) {
		assertMatchesBruteForce(falloff, -29_999_900.0, -40.0, 29_999_900.0, 12L);
	}

	private void assertMatchesBruteForce(double falloff, double centreX, double centreY, double centreZ, long seed) {
		Random random = new Random(seed);
		int count = 300;
		double[] xs = new double[count];
		double[] ys = new double[count];
		double[] zs = new double[count];
		int[] luminances = new int[count];

		for (int i = 0; i < count; i++) {
			xs[i] = centreX + (random.nextDouble() - 0.5) * 80.0;
			ys[i] = centreY + (random.nextDouble() - 0.5) * 40.0;
			zs[i] = centreZ + (random.nextDouble() - 0.5) * 80.0;
			luminances[i] = 1 + random.nextInt(15);
		}

		LightSnapshot snapshot = this.builder.build(count, xs, ys, zs, luminances, falloff);
		assertEquals(count, snapshot.lightCount());
		assertEquals(falloff, snapshot.falloff());

		int lit = 0;

		for (int i = 0; i < 40_000; i++) {
			int x = (int) Math.floor(centreX) + random.nextInt(141) - 70;
			int y = (int) Math.floor(centreY) + random.nextInt(81) - 40;
			int z = (int) Math.floor(centreZ) + random.nextInt(141) - 70;
			double expected = bruteForce(count, xs, ys, zs, luminances, falloff, x, y, z);

			assertEquals(expected, snapshot.lightAt(x, y, z), EXACT, "at " + x + "," + y + "," + z);

			if (expected > 0.0) {
				lit++;
			}
		}

		// The comparison means little unless both lit and dark positions were hit.
		assertTrue(lit > 1_000 && lit < 39_000, "lit positions: " + lit);
	}

	private static double bruteForce(int count, double[] xs, double[] ys, double[] zs, int[] luminances, double falloff, int x, int y, int z) {
		double best = 0.0;

		for (int i = 0; i < count; i++) {
			double dx = xs[i] - (x + 0.5);
			double dy = ys[i] - (y + 0.5);
			double dz = zs[i] - (z + 0.5);
			best = Math.max(best, luminances[i] - falloff * Math.sqrt(dx * dx + dy * dy + dz * dz));
		}

		return Math.min(best, 15.0);
	}

	@Test
	void emptySnapshotIsDarkEverywhere() {
		assertEquals(0.0, LightSnapshot.EMPTY.lightAt(0, 0, 0));
		assertEquals(0.0, LightSnapshot.EMPTY.lightAt(-1, -1, -1));
		assertEquals(0.0, LightSnapshot.EMPTY.lightAt(29_999_999, 319, -30_000_000));
		assertEquals(0.0, LightSnapshot.EMPTY.lightAt(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
		assertEquals(0, LightSnapshot.EMPTY.lightCount());
		assertEquals(0, LightSnapshot.EMPTY.sectionCount());
	}

	@Test
	void noLightsBuildTheSharedEmptySnapshot() {
		assertSame(LightSnapshot.EMPTY, this.builder.build(0, new double[0], new double[0], new double[0], new int[0], 2.0));
		// A light that reaches no block centre lights nothing either.
		assertSame(LightSnapshot.EMPTY, this.one(8.0, 8.0, 8.0, 0, 2.0));
		assertSame(LightSnapshot.EMPTY, this.one(16.0, 16.0, 16.0, 1, 2.0));
	}

	@Test
	void valueFallsOffLinearlyFromTheBlockCentre() {
		LightSnapshot snapshot = this.one(0.5, 64.5, 0.5, 14, 2.0);

		assertEquals(14.0, snapshot.lightAt(0, 64, 0), EXACT);
		assertEquals(12.0, snapshot.lightAt(1, 64, 0), EXACT);
		assertEquals(8.0, snapshot.lightAt(0, 64, -3), EXACT);
		assertEquals(2.0, snapshot.lightAt(0, 70, 0), EXACT);
		assertEquals(14.0 - 2.0 * Math.sqrt(3.0), snapshot.lightAt(-1, 63, -1), EXACT);
		assertEquals(0.0, snapshot.lightAt(7, 64, 0));
		assertEquals(0.0, snapshot.lightAt(0, 64, 8));
		assertEquals(0.0, snapshot.lightAt(100, 64, 100));
	}

	@Test
	void longRangeReachesFifteenBlocks() {
		LightSnapshot snapshot = this.one(0.5, 64.5, 0.5, 15, 1.0);

		assertEquals(15.0, snapshot.lightAt(0, 64, 0), EXACT);
		assertEquals(1.0, snapshot.lightAt(14, 64, 0), EXACT);
		assertEquals(1.0, snapshot.lightAt(0, 64, -14), EXACT);
		assertEquals(1.0, snapshot.lightAt(0, 78, 0), EXACT);
		assertEquals(0.0, snapshot.lightAt(15, 64, 0));
		assertEquals(0.0, snapshot.lightAt(-15, 64, 0));
		// Reach 15 from block 0 spans the sections -1, 0 and, upwards, 4 and 3.
		assertTrue(snapshot.lightsInSection(-1, 4, 0) > 0);
		assertTrue(snapshot.lightsInSection(0, 3, 0) > 0);
		assertTrue(snapshot.lightsInSection(0, 4, -1) > 0);
	}

	@Test
	void resultIsLimitedToFifteenAndNeverNegative() {
		LightSnapshot snapshot = this.one(0.5, 0.5, 0.5, 40, 2.0);

		assertEquals(15.0, snapshot.lightAt(0, 0, 0));
		assertEquals(15.0, snapshot.lightAt(5, 0, 0));
		assertEquals(40.0 - 2.0 * 15.0, snapshot.lightAt(15, 0, 0), EXACT);
		assertEquals(0.0, snapshot.lightAt(20, 0, 0));

		Random random = new Random(5L);

		for (int i = 0; i < 20_000; i++) {
			double value = snapshot.lightAt(random.nextInt(81) - 40, random.nextInt(81) - 40, random.nextInt(81) - 40);
			assertTrue(value >= 0.0 && value <= 15.0, "out of range: " + value);
		}
	}

	@Test
	void brightestLightWinsAndLightsDoNotAddUp() {
		double[] xs = {0.5, 0.5, 4.5};
		double[] ys = {0.5, 0.5, 0.5};
		double[] zs = {0.5, 0.5, 0.5};
		int[] luminances = {10, 6, 12};
		LightSnapshot snapshot = this.builder.build(3, xs, ys, zs, luminances, 2.0);

		// Two lights in the same spot: 10, not 16.
		assertEquals(10.0, snapshot.lightAt(0, 0, 0), EXACT);
		// Next to the third light, that one is brighter here.
		assertEquals(12.0, snapshot.lightAt(4, 0, 0), EXACT);
		assertEquals(10.0, snapshot.lightAt(3, 0, 0), EXACT);
		// Halfway: 10 - 2 * 2 against 12 - 2 * 2.
		assertEquals(8.0, snapshot.lightAt(2, 0, 0), EXACT);
		assertEquals(8.0, snapshot.lightAt(-1, 0, 0), EXACT);
	}

	@Test
	void sectionTableListsOnlySectionsALightCanReach() {
		// Reach 7.5 from the middle of section 0: the nearest block centre of any neighbour is 8.5 away.
		LightSnapshot middle = this.one(8.0, 8.0, 8.0, 15, 2.0);
		assertEquals(1, middle.sectionCount());
		assertEquals(1, middle.lightsInSection(0, 0, 0));
		assertEquals(0, middle.lightsInSection(1, 0, 0));
		assertEquals(0, middle.lightsInSection(0, -1, 0));

		// On a section corner the same light reaches into all eight sections around it.
		LightSnapshot corner = this.one(-16.0, 0.0, 32.0, 15, 2.0);
		assertEquals(8, corner.sectionCount());
		assertEquals(1, corner.lightsInSection(-2, -1, 1));
		assertEquals(1, corner.lightsInSection(-1, 0, 2));

		// Near one face only: the section across that face, but not the diagonal ones, which the
		// reach box touches and the sphere does not.
		LightSnapshot face = this.one(15.0, 2.0, 2.0, 6, 2.0);
		assertEquals(1, face.lightsInSection(0, 0, 0));
		assertEquals(1, face.lightsInSection(1, 0, 0));
		assertEquals(0, face.lightsInSection(1, -1, -1));
	}

	@Test
	void manyLightsInOneSectionAreAllListed() {
		int count = 500;
		double[] xs = new double[count];
		double[] ys = new double[count];
		double[] zs = new double[count];
		int[] luminances = new int[count];
		Random random = new Random(9L);

		for (int i = 0; i < count; i++) {
			xs[i] = 6.0 + random.nextDouble() * 4.0;
			ys[i] = 6.0 + random.nextDouble() * 4.0;
			zs[i] = 6.0 + random.nextDouble() * 4.0;
			luminances[i] = 4;
		}

		LightSnapshot snapshot = this.builder.build(count, xs, ys, zs, luminances, 2.0);
		assertEquals(1, snapshot.sectionCount());
		assertEquals(count, snapshot.lightsInSection(0, 0, 0));
	}

	@Test
	void builderCanBeReused() {
		LightSnapshot first = this.one(0.5, 0.5, 0.5, 14, 2.0);
		LightSnapshot second = this.one(100.5, 0.5, 0.5, 8, 1.0);

		// The first snapshot is not touched by the second build.
		assertEquals(14.0, first.lightAt(0, 0, 0), EXACT);
		assertEquals(0.0, first.lightAt(100, 0, 0));
		assertEquals(8.0, second.lightAt(100, 0, 0), EXACT);
		assertEquals(0.0, second.lightAt(0, 0, 0));
	}

	private LightSnapshot one(double x, double y, double z, int luminance, double falloff) {
		return this.builder.build(1, new double[] {x}, new double[] {y}, new double[] {z}, new int[] {luminance}, falloff);
	}
}
