package navrelis.dynamiclighting.engine;

import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * The sections whose meshes must be built again because the dynamic light in them changed.
 * <p>
 * Every section is queued at most once however many lights ask for it. Each flush hands out a limited
 * number of sections, nearest to the camera first, and keeps the rest for the next flush.
 * <p>
 * A renderer may be unable to take a request at the moment it is made. Such a section is kept apart
 * and offered again in every later flush, outside the budget, until the request is settled or
 * {@link #RETRY_FLUSHES} more flushes have passed.
 */
final class RebuildQueue {
	/**
	 * Receives the sections of a flush.
	 */
	@FunctionalInterface
	interface SectionMarker {
		/**
		 * Asks the renderer to build the section again.
		 *
		 * @return {@code true} if the request is settled: the renderer took it, or there is no point
		 *         in asking again. {@code false} if the renderer could not take it yet and the same
		 *         section should be offered again later.
		 */
		boolean markDirty(int sectionX, int sectionY, int sectionZ);
	}

	/**
	 * How many later flushes an unsettled request is offered again before it is given up.
	 */
	static final int RETRY_FLUSHES = 40;

	private static final int INDEX_BITS = 24;
	private static final int MAX_SORTED = 1 << INDEX_BITS;
	/**
	 * Limit per axis, in sections, so that the squared distance still fits above the index bits.
	 */
	private static final long MAX_DELTA = 1L << 18;
	private static final int SHRINK_ABOVE = 1024;

	private final LongOpenHashSet pending = new LongOpenHashSet();
	/**
	 * Section key to the number of flushes it is still offered in. A section is never in this map
	 * and in {@link #pending} at once.
	 */
	private final Long2IntOpenHashMap retries = new Long2IntOpenHashMap();
	private final Sections.Sink requests = this::add;
	private long[] keys = new long[64];
	private long[] order = new long[64];

	/**
	 * Queues every section whose mesh can show a light with the given reach at the given point, within
	 * the given section Y range (both ends inclusive).
	 *
	 * @param margin see {@link Sections#collect}
	 */
	void addReach(double x, double y, double z, double reach, double margin, int minSectionY, int maxSectionY) {
		Sections.collect(x, y, z, reach, margin, minSectionY, maxSectionY, this.requests);
	}

	void add(int sectionX, int sectionY, int sectionZ) {
		this.add(SectionKey.of(sectionX, sectionY, sectionZ));
	}

	private void add(long key) {
		if (!this.retries.isEmpty() && this.retries.containsKey(key)) {
			// Already offered in every flush; a new request only renews how long.
			this.retries.put(key, RETRY_FLUSHES);
		} else {
			this.pending.add(key);
		}
	}

	/**
	 * Number of sections waiting to be handed out for the first time.
	 */
	int size() {
		return this.pending.size();
	}

	/**
	 * Number of sections whose request the renderer has not taken yet.
	 */
	int retrying() {
		return this.retries.size();
	}

	boolean isEmpty() {
		return this.pending.isEmpty() && this.retries.isEmpty();
	}

	void clear() {
		boolean large = this.pending.size() > SHRINK_ABOVE || this.retries.size() > SHRINK_ABOVE;
		this.pending.clear();
		this.retries.clear();

		if (large) {
			// Give the memory of a burst back.
			this.pending.trim();
			this.retries.trim();
			this.keys = new long[64];
			this.order = new long[64];
		}
	}

	/**
	 * Offers every unsettled section to {@code marker} again, then hands it up to {@code budget}
	 * queued sections, nearest to the given camera section first, and removes them from the queue.
	 *
	 * @return the number of queued sections handed out, not counting the ones offered again
	 */
	int flush(int cameraSectionX, int cameraSectionY, int cameraSectionZ, int budget, SectionMarker marker) {
		if (!this.retries.isEmpty()) {
			this.retry(marker);
		}

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

			if (!marker.markDirty(SectionKey.x(key), SectionKey.y(key), SectionKey.z(key))) {
				this.retries.put(key, RETRY_FLUSHES);
			}
		}

		if (all) {
			this.pending.clear();

			if (marked > SHRINK_ABOVE) {
				this.pending.trim();
				this.keys = new long[64];
				this.order = new long[64];
			}
		}

		return marked;
	}

	private void retry(SectionMarker marker) {
		ObjectIterator<Long2IntMap.Entry> entries = this.retries.long2IntEntrySet().fastIterator();

		while (entries.hasNext()) {
			Long2IntMap.Entry entry = entries.next();
			long key = entry.getLongKey();
			int left = entry.getIntValue() - 1;

			if (marker.markDirty(SectionKey.x(key), SectionKey.y(key), SectionKey.z(key)) || left <= 0) {
				entries.remove();
			} else {
				entry.setValue(left);
			}
		}
	}

	private static long squared(long delta) {
		long limited = Math.min(Math.abs(delta), MAX_DELTA);
		return limited * limited;
	}
}
