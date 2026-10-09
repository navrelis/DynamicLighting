package navrelis.dynamiclighting.engine;

import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Builds a {@link LightSnapshot} from a list of lights. Keeps its working memory between builds, so
 * it belongs to one thread.
 */
final class SnapshotBuilder {
	private static final int SHRINK_ABOVE = 4096;

	private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
	private long[] pairKeys = new long[64];
	private int[] pairLights = new int[64];
	private int pairCount;

	/**
	 * @param count      number of lights to read from the arrays
	 * @param luminances luminance of each light, values above 15 are possible but the lookup result is
	 *                   limited to 15
	 * @param falloff    light levels lost per block of distance, above 0
	 * @return the snapshot, {@link LightSnapshot#EMPTY} if no light reaches any block centre
	 */
	LightSnapshot build(int count, double[] xs, double[] ys, double[] zs, int[] luminances, double falloff) {
		if (count == 0) {
			return LightSnapshot.EMPTY;
		}

		double[] lights = new double[count * LightSnapshot.STRIDE];
		boolean oversized = this.counts.size() > SHRINK_ABOVE;
		this.pairCount = 0;
		this.counts.clear();

		if (oversized) {
			// Walking the map costs time by its capacity, so give the memory of a burst back.
			this.counts.trim(SHRINK_ABOVE);
		}

		for (int i = 0; i < count; i++) {
			int offset = i * LightSnapshot.STRIDE;
			double reach = luminances[i] / falloff;

			lights[offset] = xs[i];
			lights[offset + 1] = ys[i];
			lights[offset + 2] = zs[i];
			lights[offset + 3] = luminances[i];
			lights[offset + 4] = reach * reach;

			if (reach > 0.0) {
				this.addSections(offset, xs[i], ys[i], zs[i], reach);
			}
		}

		if (this.pairCount == 0) {
			return LightSnapshot.EMPTY;
		}

		int sectionCount = this.counts.size();
		int capacity = Math.max(2, Integer.highestOneBit(sectionCount * 2 - 1) << 1);
		int shift = 64 - Integer.numberOfTrailingZeros(capacity);
		int mask = capacity - 1;
		long[] table = new long[capacity * 2];
		int[] refs = new int[this.pairCount];

		for (int slot = 0; slot < capacity; slot++) {
			table[slot << 1] = SectionKey.EMPTY;
		}

		int start = 0;

		for (ObjectIterator<Long2IntMap.Entry> entries = this.counts.long2IntEntrySet().fastIterator(); entries.hasNext();) {
			Long2IntMap.Entry entry = entries.next();
			int slot = -LightSnapshot.find(table, shift, mask, entry.getLongKey()) - 1;

			table[slot << 1] = entry.getLongKey();
			table[(slot << 1) + 1] = (long) start << 32;
			start += entry.getIntValue();
		}

		for (int pair = 0; pair < this.pairCount; pair++) {
			int rangeIndex = (LightSnapshot.find(table, shift, mask, this.pairKeys[pair]) << 1) + 1;
			long range = table[rangeIndex];

			refs[(int) (range >>> 32) + (int) range] = this.pairLights[pair];
			table[rangeIndex] = range + 1;
		}

		return new LightSnapshot(falloff, lights, table, refs, sectionCount);
	}

	/**
	 * Lists the light for every section in which some block centre can lie within its reach: the
	 * sections its reach box touches, minus those whose block centres are all further away.
	 */
	private void addSections(int light, double x, double y, double z, double reach) {
		double reachSquared = reach * reach;
		int minX = Sections.min(x - reach);
		int maxX = Sections.max(x + reach);
		int minY = Sections.min(y - reach);
		int maxY = Sections.max(y + reach);
		int minZ = Sections.min(z - reach);
		int maxZ = Sections.max(z + reach);

		for (int sectionX = minX; sectionX <= maxX; sectionX++) {
			double gapX = gapToCentres(x, sectionX);
			double squaredX = gapX * gapX;

			if (squaredX >= reachSquared) {
				continue;
			}

			for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
				double gapZ = gapToCentres(z, sectionZ);
				double squaredXZ = squaredX + gapZ * gapZ;

				if (squaredXZ >= reachSquared) {
					continue;
				}

				for (int sectionY = minY; sectionY <= maxY; sectionY++) {
					double gapY = gapToCentres(y, sectionY);

					if (squaredXZ + gapY * gapY < reachSquared) {
						this.addPair(SectionKey.of(sectionX, sectionY, sectionZ), light);
					}
				}
			}
		}
	}

	/**
	 * Distance on one axis from {@code position} to the nearest block centre of a section, 0 if the
	 * position lies between the first and the last centre.
	 */
	private static double gapToCentres(double position, int section) {
		double first = section * 16.0 + 0.5;
		double last = first + 15.0;
		return position < first ? first - position : position > last ? position - last : 0.0;
	}

	private void addPair(long key, int light) {
		if (this.pairCount == this.pairKeys.length) {
			this.pairKeys = Arrays.copyOf(this.pairKeys, this.pairCount * 2);
			this.pairLights = Arrays.copyOf(this.pairLights, this.pairCount * 2);
		}

		this.pairKeys[this.pairCount] = key;
		this.pairLights[this.pairCount] = light;
		this.pairCount++;
		this.counts.addTo(key, 1);
	}
}
