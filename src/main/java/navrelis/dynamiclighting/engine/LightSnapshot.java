package navrelis.dynamiclighting.engine;

/**
 * All active dynamic lights at one moment, as plain numbers.
 * <p>
 * A snapshot never changes after it is built and holds no game objects, so chunk meshing threads
 * read it without a lock. The client thread replaces the published snapshot as a whole; everything
 * meshed from one snapshot agrees with everything else meshed from it.
 * <p>
 * Besides the lights it holds an open-addressing hash table from section key to the lights that can
 * reach a block centre in that section. A lookup for a position whose section is not in the table
 * ends after one probe sequence in that table.
 */
final class LightSnapshot {
	/**
	 * Numbers per light in {@link #lights}: X, Y, Z, luminance, squared reach.
	 */
	static final int STRIDE = 5;

	static final int MAX_LIGHT = 15;

	/**
	 * The snapshot without any light. Compared by identity on the fast path.
	 */
	static final LightSnapshot EMPTY = new LightSnapshot(
		1.0, new double[0], new long[] {SectionKey.EMPTY, 0L, SectionKey.EMPTY, 0L}, new int[0], 0
	);

	private static volatile LightSnapshot current = EMPTY;

	private final double falloff;
	private final double[] lights;
	/**
	 * Two longs per slot: the section key, then the start of its lights in {@link #refs} in the high
	 * half and their number in the low half.
	 */
	private final long[] table;
	/**
	 * Offsets into {@link #lights}, grouped by section.
	 */
	private final int[] refs;
	private final int shift;
	private final int mask;
	private final int sectionCount;

	/**
	 * @param table {@code 2 * capacity} longs, capacity a power of two of at least 2, with at least
	 *              one unused slot
	 */
	LightSnapshot(double falloff, double[] lights, long[] table, int[] refs, int sectionCount) {
		int capacity = table.length >> 1;

		this.falloff = falloff;
		this.lights = lights;
		this.table = table;
		this.refs = refs;
		this.shift = 64 - Integer.numberOfTrailingZeros(capacity);
		this.mask = capacity - 1;
		this.sectionCount = sectionCount;
	}

	/**
	 * The snapshot to read light from. Safe to call from any thread.
	 */
	static LightSnapshot current() {
		return current;
	}

	/**
	 * Replaces the published snapshot. Client thread only.
	 */
	static void publish(LightSnapshot snapshot) {
		current = snapshot;
	}

	/**
	 * Returns the dynamic light, 0 to 15, at the centre of the given block. The brightest light wins;
	 * lights do not add up.
	 */
	double lightAt(int blockX, int blockY, int blockZ) {
		long key = SectionKey.ofBlock(blockX, blockY, blockZ);
		long[] table = this.table;
		int mask = this.mask;
		int slot = SectionKey.slot(key, this.shift);
		long found;

		while ((found = table[slot << 1]) != key) {
			if (found == SectionKey.EMPTY) {
				return 0.0;
			}

			slot = (slot + 1) & mask;
		}

		return this.brightest(table[(slot << 1) + 1], blockX + 0.5, blockY + 0.5, blockZ + 0.5);
	}

	private double brightest(long range, double x, double y, double z) {
		double[] lights = this.lights;
		int[] refs = this.refs;
		double falloff = this.falloff;
		int index = (int) (range >>> 32);
		int end = index + (int) range;
		double best = 0.0;

		for (; index < end; index++) {
			int light = refs[index];
			double dx = lights[light] - x;
			double dy = lights[light + 1] - y;
			double dz = lights[light + 2] - z;
			double distanceSquared = dx * dx + dy * dy + dz * dz;

			if (distanceSquared < lights[light + 4]) {
				double value = lights[light + 3] - falloff * Math.sqrt(distanceSquared);

				if (value > best) {
					best = value;
				}
			}
		}

		return best > MAX_LIGHT ? MAX_LIGHT : best;
	}

	/**
	 * Light levels lost per block of distance in this snapshot.
	 */
	double falloff() {
		return this.falloff;
	}

	int lightCount() {
		return this.lights.length / STRIDE;
	}

	/**
	 * Number of sections that at least one light reaches.
	 */
	int sectionCount() {
		return this.sectionCount;
	}

	/**
	 * Returns how many lights are listed for the given section, 0 if it is not in the table.
	 */
	int lightsInSection(int sectionX, int sectionY, int sectionZ) {
		int slot = find(this.table, this.shift, this.mask, SectionKey.of(sectionX, sectionY, sectionZ));
		return slot < 0 ? 0 : (int) this.table[(slot << 1) + 1];
	}

	/**
	 * Returns the slot of {@code key}, or {@code -(free slot) - 1} if the key is not in the table.
	 */
	static int find(long[] table, int shift, int mask, long key) {
		int slot = SectionKey.slot(key, shift);
		long found;

		while ((found = table[slot << 1]) != key) {
			if (found == SectionKey.EMPTY) {
				return -slot - 1;
			}

			slot = (slot + 1) & mask;
		}

		return slot;
	}
}
