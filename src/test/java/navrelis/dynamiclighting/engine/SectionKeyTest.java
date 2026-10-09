package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SectionKeyTest {
	/**
	 * Sections of the legal world: 30 million blocks each way, build height -2032..2031.
	 */
	private static final int LEGAL_XZ = 30_000_000 / 16;
	private static final int LEGAL_MIN_Y = -127;
	private static final int LEGAL_MAX_Y = 126;

	private static final int[] XZ = {
		0, 1, -1, 2, -2, 255, -256, 65_535, -65_536,
		LEGAL_XZ - 1, LEGAL_XZ, -LEGAL_XZ, -LEGAL_XZ - 1,
		SectionKey.MAX_XZ, SectionKey.MAX_XZ - 1, SectionKey.MIN_XZ, SectionKey.MIN_XZ + 1
	};
	private static final int[] Y = {
		0, 1, -1, -4, 19, 20, LEGAL_MIN_Y, LEGAL_MAX_Y,
		SectionKey.MAX_Y, SectionKey.MAX_Y - 1, SectionKey.MIN_Y, SectionKey.MIN_Y + 1
	};

	@Test
	void roundTripsBoundaryCoordinates() {
		for (int x : XZ) {
			for (int y : Y) {
				for (int z : XZ) {
					long key = SectionKey.of(x, y, z);
					assertEquals(x, SectionKey.x(key), "x of " + x + "," + y + "," + z);
					assertEquals(y, SectionKey.y(key), "y of " + x + "," + y + "," + z);
					assertEquals(z, SectionKey.z(key), "z of " + x + "," + y + "," + z);
				}
			}
		}
	}

	@Test
	void boundaryCoordinatesGiveDistinctKeys() {
		Set<Long> keys = new HashSet<>();

		for (int x : XZ) {
			for (int y : Y) {
				for (int z : XZ) {
					assertTrue(keys.add(SectionKey.of(x, y, z)), "duplicate key for " + x + "," + y + "," + z);
				}
			}
		}

		assertEquals(XZ.length * Y.length * XZ.length, keys.size());
	}

	@Test
	void roundTripsRandomCoordinatesOfTheLegalWorld() {
		Random random = new Random(20261009L);

		for (int i = 0; i < 200_000; i++) {
			int x = random.nextInt(2 * LEGAL_XZ + 1) - LEGAL_XZ;
			int y = random.nextInt(LEGAL_MAX_Y - LEGAL_MIN_Y + 1) + LEGAL_MIN_Y;
			int z = random.nextInt(2 * LEGAL_XZ + 1) - LEGAL_XZ;
			long key = SectionKey.of(x, y, z);

			assertEquals(x, SectionKey.x(key));
			assertEquals(y, SectionKey.y(key));
			assertEquals(z, SectionKey.z(key));
		}
	}

	@Test
	void neighbouringSectionsDiffer() {
		Set<Long> keys = new HashSet<>();

		for (int x = -3; x <= 3; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					keys.add(SectionKey.of(x, y, z));
				}
			}
		}

		assertEquals(7 * 7 * 7, keys.size());
	}

	@Test
	void emptyMarkerIsNeverARealKey() {
		Random random = new Random(7L);

		for (int x : XZ) {
			for (int y : Y) {
				for (int z : XZ) {
					assertRealKey(SectionKey.of(x, y, z));
				}
			}
		}

		// Any three ints at all, also far outside the range the key can hold.
		for (int i = 0; i < 200_000; i++) {
			assertRealKey(SectionKey.of(random.nextInt(), random.nextInt(), random.nextInt()));
		}

		assertRealKey(SectionKey.of(-1, -1, -1));
		assertRealKey(SectionKey.of(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
		assertRealKey(SectionKey.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
	}

	@Test
	void blockCoordinatesFloorToTheirSection() {
		assertEquals(SectionKey.of(0, 0, 0), SectionKey.ofBlock(0, 0, 0));
		assertEquals(SectionKey.of(0, 0, 0), SectionKey.ofBlock(15, 15, 15));
		assertEquals(SectionKey.of(1, 1, 1), SectionKey.ofBlock(16, 16, 16));
		assertEquals(SectionKey.of(-1, -1, -1), SectionKey.ofBlock(-1, -1, -1));
		assertEquals(SectionKey.of(-1, -1, -1), SectionKey.ofBlock(-16, -16, -16));
		assertEquals(SectionKey.of(-2, -2, -2), SectionKey.ofBlock(-17, -17, -17));
		assertEquals(SectionKey.of(-1, 3, 0), SectionKey.ofBlock(-1, 63, 0));
		assertEquals(SectionKey.of(LEGAL_XZ - 1, -4, -LEGAL_XZ), SectionKey.ofBlock(29_999_999, -64, -30_000_000));
		assertNotEquals(SectionKey.ofBlock(-1, 0, 0), SectionKey.ofBlock(0, 0, 0));
	}

	@Test
	void firstSlotLiesInsideTheTable() {
		Random random = new Random(3L);

		for (int bits = 1; bits <= 20; bits++) {
			int capacity = 1 << bits;

			for (int i = 0; i < 2_000; i++) {
				long key = SectionKey.of(random.nextInt(4001) - 2000, random.nextInt(24) - 4, random.nextInt(4001) - 2000);
				int slot = SectionKey.slot(key, 64 - bits);
				assertTrue(slot >= 0 && slot < capacity, "slot " + slot + " for capacity " + capacity);
			}
		}
	}

	private static void assertRealKey(long key) {
		assertNotEquals(SectionKey.EMPTY, key);
		assertTrue(key >= 0, "top bit set in " + Long.toHexString(key));
	}
}
