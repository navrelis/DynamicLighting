package navrelis.dynamiclighting.engine;

import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * The sections whose meshes must be built again because the dynamic light in them changed.
 * <p>
 * Every section is queued at most once however many lights ask for it. Each flush hands out a limited
 * number of sections, nearest to the camera first, and keeps the rest for the next flush.
 */
final class RebuildQueue {
	/**
	 * Receives the sections of a flush.
	 */
	@FunctionalInterface
	interface SectionMarker {
		void markDirty(int sectionX, int sectionY, int sectionZ);
	}

	private static final int INDEX_BITS = 24;
	private static final int MAX_SORTED = 1 << INDEX_BITS;
	/**
	 * Limit per axis, in sections, so that the squared distance still fits above the index bits.
	 */
	private static final long MAX_DELTA = 1L << 18;
	private static final int SHRINK_ABOVE = 1024;

	private final LongOpenHashSet pending = new LongOpenHashSet();
	private long[] keys = new long[64];
	private long[] order = new long[64];

	/**
	 * Queues every section touched by the box that reaches {@code extent} blocks from the given point
	 * on every axis, within the given section Y range (both ends inclusive).
	 */
	void addBox(double x, double y, double z, double extent, int minSectionY, int maxSectionY) {
		Sections.collect(x, y, z, extent, minSectionY, maxSectionY, this.pending);
	}

	void add(int sectionX, int sectionY, int sectionZ) {
		this.pending.add(SectionKey.of(sectionX, sectionY, sectionZ));
	}

	int size() {
		return this.pending.size();
	}

	void clear() {
		boolean large = this.pending.size() > SHRINK_ABOVE;
		this.pending.clear();

		if (large) {
			// Give the memory of a burst back.
			this.pending.trim();
			this.keys = new long[64];
			this.order = new long[64];
		}
	}

	/**
	 * Hands up to {@code budget} queued sections to {@code marker}, nearest to the given camera section
	 * first, and removes them from the queue.
	 *
	 * @return the number of sections handed out
	 */
	int flush(int cameraSectionX, int cameraSectionY, int cameraSectionZ, int budget, SectionMarker marker) {
		int count = Math.min(this.pending.size(), MAX_SORTED);

		if (count == 0 || budget <= 0) {
			return 0;
		}

		if (this.keys.length < count) {
			int capacity = Math.max(count, this.keys.length * 2);
			this.keys = new long[capacity];
			this.order = new long[capacity];
		}

		long[] keys = this.keys;
		long[] order = this.order;
		LongIterator iterator = this.pending.iterator();

		for (int i = 0; i < count; i++) {
			long key = iterator.nextLong();
			long distance = squared(SectionKey.x(key) - (long) cameraSectionX)
				+ squared(SectionKey.y(key) - (long) cameraSectionY)
				+ squared(SectionKey.z(key) - (long) cameraSectionZ);

			// Distance in the high bits, position in the scratch array in the low bits: sorting the
			// plain longs sorts by distance without any comparator.
			keys[i] = key;
			order[i] = distance << INDEX_BITS | i;
		}

		Arrays.sort(order, 0, count);

		int marked = Math.min(count, budget);
		boolean all = marked == this.pending.size();

		for (int i = 0; i < marked; i++) {
			long key = keys[(int) (order[i] & (MAX_SORTED - 1))];

			if (!all) {
				this.pending.remove(key);
			}

			marker.markDirty(SectionKey.x(key), SectionKey.y(key), SectionKey.z(key));
		}

		if (all) {
			this.clear();
		}

		return marked;
	}

	private static long squared(long delta) {
		long limited = Math.min(Math.abs(delta), MAX_DELTA);
		return limited * limited;
	}
}
