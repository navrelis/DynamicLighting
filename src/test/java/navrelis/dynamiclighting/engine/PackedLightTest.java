package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackedLightTest {
	@Test
	void sixteenthsAreKept() {
		int packed = pack(12, 3);

		assertEquals(12 << 20 | 116, PackedLight.merge(packed, 7.3, false));
		assertEquals(12 << 20 | 7 << 4, PackedLight.merge(packed, 7.0, false));
		assertEquals(12 << 20 | 127, PackedLight.merge(packed, 7.99, false));
		assertEquals(12 << 20 | 49, PackedLight.merge(packed, 3.1, false));
		assertEquals(12 << 20 | 240, PackedLight.merge(packed, 15.0, false));
	}

	@Test
	void wholeLevelsRoundToTheNearest() {
		int packed = pack(12, 3);

		assertEquals(pack(12, 7), PackedLight.merge(packed, 7.3, true));
		assertEquals(pack(12, 7), PackedLight.merge(packed, 7.49, true));
		assertEquals(pack(12, 8), PackedLight.merge(packed, 7.5, true));
		assertEquals(pack(12, 8), PackedLight.merge(packed, 7.99, true));
		assertEquals(pack(12, 4), PackedLight.merge(packed, 3.5, true));
		assertEquals(pack(12, 15), PackedLight.merge(packed, 14.6, true));
		assertEquals(pack(12, 15), PackedLight.merge(packed, 15.0, true));
	}

	@Test
	void dynamicLightNotAboveTheBlockLightChangesNothing() {
		int packed = pack(12, 7);

		assertEquals(packed, PackedLight.merge(packed, 0.0, false));
		assertEquals(packed, PackedLight.merge(packed, 6.9, false));
		assertEquals(packed, PackedLight.merge(packed, 7.0, false));
		assertEquals(packed, PackedLight.merge(packed, 0.0, true));
		assertEquals(packed, PackedLight.merge(packed, 7.0, true));
		// Rounds to 7, which is what is there already.
		assertEquals(packed, PackedLight.merge(packed, 7.4, true));
		// Rounds down to 3 although 3.4 is above the 0 that is there: still a raise.
		assertEquals(pack(0, 3), PackedLight.merge(pack(0, 0), 3.4, true));
		// Less than half a level over nothing is nothing.
		assertEquals(pack(0, 0), PackedLight.merge(pack(0, 0), 0.4, true));
	}

	@Test
	void neverLowersBlockLightAndNeverTouchesSkyLight() {
		for (int sky = 0; sky <= 15; sky++) {
			for (int block = 0; block <= 15; block++) {
				int packed = pack(sky, block);

				for (int step = 0; step <= 1500; step++) {
					double dynamic = step / 100.0;

					for (boolean wholeLevels : new boolean[] {false, true}) {
						int merged = PackedLight.merge(packed, dynamic, wholeLevels);
						String what = "sky " + sky + ", block " + block + ", dynamic " + dynamic + ", whole " + wholeLevels;

						assertEquals(packed >>> 16, merged >>> 16, what);
						assertTrue(PackedLight.block(merged) >= PackedLight.block(packed), what);
						assertTrue(PackedLight.block(merged) <= PackedLight.FULL_BLOCK_LIGHT, what);
						assertTrue(PackedLight.block(merged) <= Math.max(PackedLight.block(packed), dynamic * 16.0 + 8.0), what);

						if (dynamic <= block) {
							assertEquals(packed, merged, what);
						}

						if (wholeLevels) {
							assertEquals(0, merged & 0xF, what);
						}

						// Merging what was merged changes nothing: a position that passes two hooks is safe.
						assertEquals(merged, PackedLight.merge(merged, dynamic, wholeLevels), what);
					}
				}
			}
		}
	}

	@Test
	void fractionalSkyAndBlockPartsSurvive() {
		// Smooth lighting averages give sixteenths in both halves.
		int packed = 0x00B7_0035;

		assertEquals(0x00B7_0050, PackedLight.merge(packed, 5.0, false));
		assertEquals(0x00B7_0050, PackedLight.merge(packed, 5.2, true));
		assertEquals(packed, PackedLight.merge(packed, 3.3, false));
		// 3 whole levels would be less than the 3 5/16 that are there.
		assertEquals(packed, PackedLight.merge(packed, 3.3, true));
	}

	@Test
	void fullBrightIsLeftAlone() {
		// What LevelRenderer returns for blocks with emissive rendering.
		int fullBright = 15728880;

		assertEquals(PackedLight.FULL_BLOCK_LIGHT, PackedLight.block(fullBright));
		assertEquals(fullBright, PackedLight.merge(fullBright, 15.0, false));
		assertEquals(fullBright, PackedLight.merge(fullBright, 15.0, true));
	}

	@Test
	void roundsHalvesUp() {
		assertEquals(0, PackedLight.roundToLevel(0.0));
		assertEquals(0, PackedLight.roundToLevel(0.49));
		assertEquals(1, PackedLight.roundToLevel(0.5));
		assertEquals(11, PackedLight.roundToLevel(11.17));
		assertEquals(15, PackedLight.roundToLevel(14.5));
		assertEquals(15, PackedLight.roundToLevel(15.0));
	}

	private static int pack(int sky, int block) {
		return sky << 20 | block << 4;
	}
}
