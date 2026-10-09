package navrelis.dynamiclighting.engine;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * The light sources that are published right now, by entity id, and the bookkeeping that follows
 * from changing them: which sections must be rebuilt and whether a new snapshot is due.
 * <p>
 * Each source is kept as the position and luminance it was last published with ("committed"). No
 * game object is stored. Belongs to the client thread.
 */
final class LightTracker {
	/**
	 * A block samples the positions next to it, so a section must be rebuilt as soon as the light
	 * reaches the centre of a block just outside it: half a block beyond its faces.
	 */
	static final double REBUILD_MARGIN = 0.5;

	private final Int2IntOpenHashMap slots = new Int2IntOpenHashMap();
	private final RebuildQueue queue = new RebuildQueue();
	private final SnapshotBuilder builder = new SnapshotBuilder();

	private int[] ids = new int[16];
	private double[] xs = new double[16];
	private double[] ys = new double[16];
	private double[] zs = new double[16];
	private int[] luminances = new int[16];
	private int size;

	private double falloff = 2.0;
	private int minSectionY = Integer.MIN_VALUE;
	private int maxSectionY = Integer.MAX_VALUE;
	private boolean changed;
	private int published;

	LightTracker() {
		this.slots.defaultReturnValue(-1);
	}

	/**
	 * Number of sources.
	 */
	int size() {
		return this.size;
	}

	/**
	 * The entity id of the source in the given slot, {@code 0 <= slot < size()}.
	 */
	int idAt(int slot) {
		return this.ids[slot];
	}

	boolean isTracked(int id) {
		return this.slots.containsKey(id);
	}

	RebuildQueue queue() {
		return this.queue;
	}

	double falloff() {
		return this.falloff;
	}

	/**
	 * Returns the number of snapshots published since the last call.
	 */
	int takePublished() {
		int count = this.published;
		this.published = 0;
		return count;
	}

	/**
	 * Sets the range of section Y coordinates (both inclusive) that exist in the level. Rebuilds are
	 * never requested outside it.
	 */
	void setSectionBounds(int minSectionY, int maxSectionY) {
		this.minSectionY = minSectionY;
		this.maxSectionY = maxSectionY;
	}

	/**
	 * Sets the light levels lost per block. A change alters the footprint of every source: all sections
	 * lit before and after are queued and a new snapshot is due.
	 */
	void setFalloff(double falloff) {
		if (falloff == this.falloff || !(falloff > 0.0)) {
			return;
		}

		this.queueAll();
		this.falloff = falloff;
		this.queueAll();
		this.changed |= this.size > 0;
	}

	/**
	 * Reports what an entity emits now and decides whether that is published.
	 * <p>
	 * A new source is always published. A known source is published again only if its luminance
	 * changed or it is further than {@code threshold} blocks from its committed position; smaller
	 * movements are measured against the committed position, so slow drift adds up. Luminance 0
	 * removes the source, and so does a position that is not a number or lies far outside any world
	 * ({@link Sections#isValidPosition}).
	 *
	 * @return whether the set of published lights changed
	 */
	boolean update(int id, double x, double y, double z, int luminance, double threshold) {
		int slot = this.slots.get(id);

		if (luminance <= 0 || !Sections.isValidPosition(x, y, z)) {
			if (slot < 0) {
				return false;
			}

			this.removeAt(slot);
			return true;
		}

		luminance = Math.min(luminance, LightSnapshot.MAX_LIGHT);

		if (slot < 0) {
			slot = this.size;
			this.ensureCapacity(slot + 1);
			this.size = slot + 1;
			this.ids[slot] = id;
			this.slots.put(id, slot);
		} else {
			if (luminance == this.luminances[slot]) {
				double dx = x - this.xs[slot];
				double dy = y - this.ys[slot];
				double dz = z - this.zs[slot];

				if (dx * dx + dy * dy + dz * dz <= threshold * threshold) {
					return false;
				}
			}

			this.queueSource(slot);
		}

		this.xs[slot] = x;
		this.ys[slot] = y;
		this.zs[slot] = z;
		this.luminances[slot] = luminance;
		this.queueSource(slot);
		this.changed = true;
		return true;
	}

	/**
	 * Removes the source in the given slot and queues its sections. The last source moves into the
	 * slot, so a loop that removes while iterating must run from the last slot down.
	 */
	void removeAt(int slot) {
		this.queueSource(slot);
		this.slots.remove(this.ids[slot]);

		int last = --this.size;

		if (slot != last) {
			this.ids[slot] = this.ids[last];
			this.xs[slot] = this.xs[last];
			this.ys[slot] = this.ys[last];
			this.zs[slot] = this.zs[last];
			this.luminances[slot] = this.luminances[last];
			this.slots.put(this.ids[slot], slot);
		}

		this.changed = true;
	}

	/**
	 * Publishes a new snapshot if a source was added, removed or committed since the last one.
	 *
	 * @return whether a snapshot was published
	 */
	boolean publishIfChanged() {
		if (!this.changed) {
			return false;
		}

		this.changed = false;
		this.publish(this.builder.build(this.size, this.xs, this.ys, this.zs, this.luminances, this.falloff));
		return true;
	}

	/**
	 * Switches all light off inside a level that stays: publishes the empty snapshot, queues every
	 * section that was lit and forgets the sources.
	 */
	void switchOff() {
		if (this.size == 0) {
			return;
		}

		this.publish(LightSnapshot.EMPTY);
		this.queueAll();
		this.forgetSources();
	}

	/**
	 * Drops everything because the level is gone: sources, pending rebuilds and the published lights.
	 * Requests no rebuilds, the meshes of the old level no longer exist.
	 */
	void reset() {
		this.forgetSources();
		this.queue.clear();

		if (LightSnapshot.current() != LightSnapshot.EMPTY) {
			this.publish(LightSnapshot.EMPTY);
		}
	}

	private void publish(LightSnapshot snapshot) {
		LightSnapshot.publish(snapshot);
		this.published++;
	}

	private void forgetSources() {
		this.slots.clear();
		this.size = 0;
		this.changed = false;
	}

	private void queueAll() {
		for (int slot = 0; slot < this.size; slot++) {
			this.queueSource(slot);
		}
	}

	private void queueSource(int slot) {
		this.queue.addReach(
			this.xs[slot], this.ys[slot], this.zs[slot],
			this.luminances[slot] / this.falloff, REBUILD_MARGIN,
			this.minSectionY, this.maxSectionY
		);
	}

	private void ensureCapacity(int capacity) {
		if (capacity > this.ids.length) {
			int grown = Math.max(capacity, this.ids.length * 2);
			this.ids = Arrays.copyOf(this.ids, grown);
			this.xs = Arrays.copyOf(this.xs, grown);
			this.ys = Arrays.copyOf(this.ys, grown);
			this.zs = Arrays.copyOf(this.zs, grown);
			this.luminances = Arrays.copyOf(this.luminances, grown);
		}
	}
}
