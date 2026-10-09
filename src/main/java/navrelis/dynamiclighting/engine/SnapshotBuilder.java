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
	private final Sections.Sink pairs = this::addPair;
	private long[] pairKeys = new long[64];
	private int[] pairLights = new int[64];
	private int pairCount;
	/**
	 * The light whose sections are being listed, as its offset in the light data.
	 */
	private int light;

	/**
	 * @param count      number of lights to read from the arrays
	 * @param luminances luminance of each light, values above 15 are possible but the lookup result is
	 *                   limited to 15
	 * @param falloff    light levels lost per block of distance, above 0
	 * @return the snapshot, {@link LightSnapshot#EMPTY} if no light reaches anything
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

			// Every section in which some point lies within reach. That is slightly more than the
			// sections with a block centre in reach, and it makes lookups at any point exact.
			this.light = offset;
			Sections.collect(xs[i], ys[i], zs[i], reach, 0.0, Integer.MIN_VALUE, Integer.MAX_VALUE, this.pairs);
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

	private void addPair(long key) {
		if (this.pairCount == this.pairKeys.length) {
			this.pairKeys = Arrays.copyOf(this.pairKeys, this.pairCount * 2);
			this.pairLights = Arrays.copyOf(this.pairLights, this.pairCount * 2);
		}

		this.pairKeys[this.pairCount] = key;
		this.pairLights[this.pairCount] = this.light;
		this.pairCount++;
		this.counts.addTo(key, 1);
	}
}
