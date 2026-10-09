package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LightTrackerTest {
	private static final double EXACT = 1.0e-9;
	/**
	 * Movement threshold of the default range in the best mode with Sodium: a quarter block.
	 */
	private static final double THRESHOLD = 0.25;
	private static final int MIN_SECTION_Y = -4;
	private static final int MAX_SECTION_Y = 19;

	private final LightTracker tracker = new LightTracker();
	private final Set<String> marked = new TreeSet<>();
	private int markCalls;
	/** What the fake renderer answers: {@code false} is a section whose first build has not landed. */
	private boolean rendererTakesRequests = true;

	@BeforeEach
	void setUp() {
		LightSnapshot.publish(LightSnapshot.EMPTY);
		this.tracker.setSectionBounds(MIN_SECTION_Y, MAX_SECTION_Y);
	}

	@AfterEach
	void tearDown() {
		LightSnapshot.publish(LightSnapshot.EMPTY);
	}

	@Test
	void newSourceIsPublishedAndItsSectionsAreQueued() {
		assertTrue(this.tracker.update(7, 8.0, 72.0, 8.0, 14, THRESHOLD));
		assertEquals(1, this.tracker.size());
		assertTrue(this.tracker.isTracked(7));
		// Nothing reaches other threads before the snapshot is published.
		assertSame(LightSnapshot.EMPTY, LightSnapshot.current());

		assertTrue(this.tracker.publishIfChanged());
		assertEquals(14.0 - 2.0 * Math.sqrt(0.75), LightSnapshot.current().lightAt(8, 72, 8), EXACT);
		// Reach 7 plus margin: 0.5..15.5 on X and Z, 64.5..79.5 on Y.
		assertEquals(Set.of("0,4,0"), this.flushAll());
	}

	@Test
	void standingSourceCostsNoSnapshotAndNoRebuild() {
		this.tracker.update(7, 8.0, 72.0, 8.0, 14, THRESHOLD);
		this.tracker.publishIfChanged();
		this.flushAll();
		LightSnapshot published = LightSnapshot.current();
		this.tracker.takePublished();

		for (int tick = 0; tick < 100; tick++) {
			assertFalse(this.tracker.update(7, 8.0, 72.0, 8.0, 14, THRESHOLD));
			assertFalse(this.tracker.publishIfChanged());
		}

		assertSame(published, LightSnapshot.current());
		assertEquals(0, this.tracker.takePublished());
		assertEquals(0, this.tracker.queue().size());
	}

	@Test
	void movementUpToTheThresholdIsNotCommitted() {
		this.commit(7, 8.0, 72.0, 8.0, 14);
		LightSnapshot published = LightSnapshot.current();

		assertFalse(this.tracker.update(7, 8.2, 72.0, 8.0, 14, THRESHOLD));
		assertFalse(this.tracker.update(7, 8.0, 72.25, 8.0, 14, THRESHOLD));
		assertFalse(this.tracker.update(7, 8.14, 72.14, 8.14, 14, THRESHOLD));
		assertFalse(this.tracker.publishIfChanged());
		assertSame(published, LightSnapshot.current());
		assertEquals(0, this.tracker.queue().size());

		assertTrue(this.tracker.update(7, 8.26, 72.0, 8.0, 14, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		assertNotSame(published, LightSnapshot.current());
		assertEquals(14.0 - 2.0 * Math.sqrt(0.24 * 0.24 + 0.5), LightSnapshot.current().lightAt(8, 72, 8), EXACT);
	}

	@Test
	void diagonalMovementIsMeasuredAsDistance() {
		this.commit(7, 8.0, 72.0, 8.0, 14);

		// 0.15 on each axis is 0.26 in space.
		assertTrue(this.tracker.update(7, 8.15, 72.15, 8.15, 14, THRESHOLD));
	}

	@Test
	void slowDriftAddsUpAgainstTheCommittedPosition() {
		this.commit(7, 8.0, 72.0, 8.0, 14);

		assertFalse(this.tracker.update(7, 8.1, 72.0, 8.0, 14, THRESHOLD));
		assertFalse(this.tracker.update(7, 8.2, 72.0, 8.0, 14, THRESHOLD));
		assertTrue(this.tracker.update(7, 8.3, 72.0, 8.0, 14, THRESHOLD));
		// The committed position moved along, so the count starts again.
		assertFalse(this.tracker.update(7, 8.4, 72.0, 8.0, 14, THRESHOLD));
		assertFalse(this.tracker.update(7, 8.5, 72.0, 8.0, 14, THRESHOLD));
		assertTrue(this.tracker.update(7, 8.6, 72.0, 8.0, 14, THRESHOLD));
	}

	@Test
	void commitQueuesTheSectionsAroundTheOldAndTheNewPosition() {
		this.commit(7, 8.0, 72.0, 8.0, 14);

		// A jump: nothing connects the two boxes.
		assertTrue(this.tracker.update(7, 108.0, 72.0, 8.0, 14, THRESHOLD));
		assertEquals(Set.of("0,4,0", "6,4,0", "7,4,0"), this.flushAll());
	}

	@Test
	void luminanceChangeIsCommittedWithoutMovement() {
		this.commit(7, 8.0, 72.0, 8.0, 14);

		assertTrue(this.tracker.update(7, 8.0, 72.0, 8.0, 15, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(15.0 - 2.0 * Math.sqrt(0.75), LightSnapshot.current().lightAt(8, 72, 8), EXACT);
		// Luminance 15: the box is now 0..16 and still one section.
		assertEquals(Set.of("0,4,0"), this.flushAll());

		assertTrue(this.tracker.update(7, 8.0, 72.0, 8.0, 3, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(3.0 - 2.0 * Math.sqrt(0.75), LightSnapshot.current().lightAt(8, 72, 8), EXACT);
	}

	@Test
	void luminanceZeroRemovesTheSource() {
		this.commit(7, 20.0, 72.0, 8.0, 14);

		assertTrue(this.tracker.update(7, 20.0, 72.0, 8.0, 0, THRESHOLD));
		assertEquals(0, this.tracker.size());
		assertFalse(this.tracker.isTracked(7));
		assertTrue(this.tracker.publishIfChanged());
		assertSame(LightSnapshot.EMPTY, LightSnapshot.current());
		// 12.5..27.5 on X.
		assertEquals(Set.of("0,4,0", "1,4,0"), this.flushAll());

		// An entity that emits nothing and is not tracked changes nothing.
		assertFalse(this.tracker.update(7, 20.0, 72.0, 8.0, 0, THRESHOLD));
		assertFalse(this.tracker.update(8, 0.0, 0.0, 0.0, 0, THRESHOLD));
		assertFalse(this.tracker.publishIfChanged());
		assertEquals(0, this.tracker.queue().size());
	}

	@Test
	void removedSourceLeavesTheOthersInPlace() {
		this.commit(1, 8.0, 72.0, 8.0, 14);
		this.commit(2, 108.0, 72.0, 8.0, 10);
		this.commit(3, 208.0, 72.0, 8.0, 6);

		// Find and remove the first one the way the engine does for vanished entities.
		for (int slot = this.tracker.size() - 1; slot >= 0; slot--) {
			if (this.tracker.idAt(slot) == 1) {
				this.tracker.removeAt(slot);
			}
		}

		assertEquals(2, this.tracker.size());
		assertFalse(this.tracker.isTracked(1));
		assertTrue(this.tracker.isTracked(2));
		assertTrue(this.tracker.isTracked(3));
		assertEquals(Set.of(2, 3), Set.of(this.tracker.idAt(0), this.tracker.idAt(1)));
		assertEquals(Set.of("0,4,0"), this.flushAll());

		assertTrue(this.tracker.publishIfChanged());
		LightSnapshot snapshot = LightSnapshot.current();
		assertEquals(2, snapshot.lightCount());
		assertEquals(0.0, snapshot.lightAt(8, 72, 8));
		assertEquals(10.0 - 2.0 * Math.sqrt(0.75), snapshot.lightAt(108, 72, 8), EXACT);
		assertEquals(6.0 - 2.0 * Math.sqrt(0.75), snapshot.lightAt(208, 72, 8), EXACT);

		// The source that changed its slot is still found under its id.
		assertFalse(this.tracker.update(3, 208.0, 72.0, 8.0, 6, THRESHOLD));
		assertFalse(this.tracker.update(2, 108.0, 72.0, 8.0, 10, THRESHOLD));
		assertEquals(2, this.tracker.size());
	}

	@Test
	void manySourcesGrowTheStorage() {
		for (int id = 0; id < 500; id++) {
			assertTrue(this.tracker.update(id, id * 40.0 + 8.0, 72.0, 8.0, 14, THRESHOLD));
		}

		assertEquals(500, this.tracker.size());
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(500, LightSnapshot.current().lightCount());
		assertEquals(14.0 - 2.0 * Math.sqrt(0.75), LightSnapshot.current().lightAt(499 * 40 + 8, 72, 8), EXACT);

		for (int id = 0; id < 500; id += 2) {
			assertTrue(this.tracker.update(id, 0.0, 0.0, 0.0, 0, THRESHOLD));
		}

		assertEquals(250, this.tracker.size());

		for (int id = 0; id < 500; id++) {
			assertEquals(id % 2 == 1, this.tracker.isTracked(id));
		}
	}

	@Test
	void levelChangeDropsEverythingWithoutDirtyRequests() {
		this.commit(1, 8.0, 72.0, 8.0, 14);
		// A change that is neither published nor flushed yet when the level goes away.
		this.tracker.update(2, 108.0, 72.0, 8.0, 10, THRESHOLD);
		this.tracker.update(1, 9.0, 72.0, 8.0, 14, THRESHOLD);
		assertTrue(this.tracker.queue().size() > 0);
		assertNotSame(LightSnapshot.EMPTY, LightSnapshot.current());

		this.tracker.reset();

		assertEquals(0, this.tracker.size());
		assertFalse(this.tracker.isTracked(1));
		assertSame(LightSnapshot.EMPTY, LightSnapshot.current());
		assertEquals(0, this.tracker.queue().size());
		assertEquals(Set.of(), this.flushAll());
		assertEquals(0, this.markCalls);
		assertFalse(this.tracker.publishIfChanged());
		assertSame(LightSnapshot.EMPTY, LightSnapshot.current());

		// In the next level the same entity id starts as a new source.
		assertTrue(this.tracker.update(1, -40.0, 72.0, -40.0, 14, THRESHOLD));
		assertEquals(Set.of("-3,4,-3"), this.flushAll());
	}

	@Test
	void switchingOffCleansUpEverySectionThatWasLit() {
		this.commit(1, 8.0, 72.0, 8.0, 14);
		this.commit(2, 100.0, 60.0, -30.0, 9);
		this.commit(3, -7.0, 3.0, 250.0, 15);
		Set<String> lit = new TreeSet<>();
		lit.addAll(sections(8.0, 72.0, 8.0, 14 / 2.0));
		lit.addAll(sections(100.0, 60.0, -30.0, 9 / 2.0));
		lit.addAll(sections(-7.0, 3.0, 250.0, 15 / 2.0));
		assertEquals(0, this.tracker.queue().size());

		this.tracker.switchOff();

		// Published before anything is marked dirty.
		assertSame(LightSnapshot.EMPTY, LightSnapshot.current());
		assertEquals(0, this.tracker.size());
		assertEquals(lit, this.flushAll());
		assertFalse(this.tracker.publishIfChanged());

		// Staying off does nothing more.
		this.tracker.switchOff();
		assertEquals(0, this.tracker.queue().size());

		// Switching back on needs nothing special: the sources are simply found again.
		assertTrue(this.tracker.update(1, 8.0, 72.0, 8.0, 14, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		assertTrue(LightSnapshot.current().lightAt(8, 72, 8) > 0.0);
	}

	@Test
	void changedFalloffRebuildsTheSnapshotAndQueuesBothFootprints() {
		this.commit(1, 8.0, 72.0, 8.0, 14);
		assertEquals(2.0, LightSnapshot.current().falloff());
		assertEquals(0.0, LightSnapshot.current().lightAt(18, 72, 8));

		// Same value: nothing happens.
		this.tracker.setFalloff(2.0);
		assertFalse(this.tracker.publishIfChanged());
		assertEquals(0, this.tracker.queue().size());

		this.tracker.setFalloff(1.0);

		assertEquals(1.0, this.tracker.falloff());
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(1.0, LightSnapshot.current().falloff());
		assertEquals(14.0 - Math.sqrt(10.5 * 10.5 + 0.5), LightSnapshot.current().lightAt(18, 72, 8), EXACT);
		// Reach 14 plus margin: -6.5..22.5 on X and Z, 57.5..86.5 on Y.
		assertEquals(sections(8.0, 72.0, 8.0, 14.0), this.flushAll());
		assertEquals(27, this.marked.size());

		this.tracker.setFalloff(2.0);
		assertTrue(this.tracker.publishIfChanged());
		// Back to the short range: the 27 sections lit before must lose the light.
		assertEquals(27, this.flushAll().size());
	}

	@Test
	void falloffWithoutSourcesNeedsNoSnapshot() {
		this.tracker.setFalloff(1.0);

		assertEquals(1.0, this.tracker.falloff());
		assertFalse(this.tracker.publishIfChanged());
		assertEquals(0, this.tracker.queue().size());

		// An invalid value is ignored.
		this.tracker.setFalloff(0.0);
		this.tracker.setFalloff(Double.NaN);
		assertEquals(1.0, this.tracker.falloff());
	}

	@Test
	void rebuildsStayInsideTheLevelHeight() {
		// At the top of the overworld: 312..328 would reach section 20.
		assertTrue(this.tracker.update(1, 8.0, 320.0, 8.0, 15, THRESHOLD));
		assertEquals(Set.of("0,19,0"), this.flushAll());

		// Far above the world nothing can be rebuilt, but entities up there are still lit.
		assertTrue(this.tracker.update(2, 8.0, 500.5, 8.0, 15, THRESHOLD));
		assertEquals(Set.of(), this.flushAll());
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(15.0 - 2.0 * Math.sqrt(0.5), LightSnapshot.current().lightAt(8, 500, 8), EXACT);
	}

	@Test
	void brokenPositionsAndValuesAreHandled() {
		assertFalse(this.tracker.update(1, Double.NaN, 72.0, 8.0, 14, THRESHOLD));
		assertFalse(this.tracker.update(1, 8.0, Double.POSITIVE_INFINITY, 8.0, 14, THRESHOLD));
		assertEquals(0, this.tracker.size());

		// A source whose position breaks is removed like one that went dark.
		this.commit(1, 8.0, 72.0, 8.0, 14);
		assertTrue(this.tracker.update(1, 8.0, 72.0, Double.NEGATIVE_INFINITY, 14, THRESHOLD));
		assertEquals(0, this.tracker.size());
		assertEquals(Set.of("0,4,0"), this.flushAll());

		// Luminance above 15 is treated as 15.
		assertTrue(this.tracker.update(2, 8.0, 72.0, 8.0, 99, THRESHOLD));
		assertFalse(this.tracker.update(2, 8.0, 72.0, 8.0, 15, THRESHOLD));
		assertEquals(Set.of("0,4,0"), this.flushAll());
	}

	@Test
	void positionsFarOutsideAnyWorldAreNotLightSources() {
		// The second value is where the section arithmetic used to run into an endless loop.
		double[] outside = {3.3e7, -3.4e10, 1.0e15, Double.MAX_VALUE};
		int id = 0;

		for (double value : outside) {
			for (double signed : new double[] {value, -value}) {
				String what = "coordinate " + signed;

				assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
					assertFalse(this.tracker.update(1, signed, 72.0, 8.0, 15, THRESHOLD), "x " + what);
					assertFalse(this.tracker.update(1, 8.0, signed, 8.0, 15, THRESHOLD), "y " + what);
					assertFalse(this.tracker.update(1, 8.0, 72.0, signed, 15, THRESHOLD), "z " + what);
				});
				assertEquals(0, this.tracker.size(), what);
				assertEquals(0, this.tracker.queue().size(), what);
				assertFalse(this.tracker.publishIfChanged(), what);

				// A source that is carried out there is removed, and only its old place is rebuilt.
				for (int axis = 0; axis < 3; axis++) {
					int source = ++id;
					double x = axis == 0 ? signed : 8.0;
					double y = axis == 1 ? signed : 72.0;
					double z = axis == 2 ? signed : 8.0;
					this.commit(source, 8.0, 72.0, 8.0, 15);

					assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertTrue(this.tracker.update(source, x, y, z, 15, THRESHOLD), what));
					assertFalse(this.tracker.isTracked(source), what);
					assertEquals(Set.of("0,4,0"), this.flushAll(), what);
					assertTrue(this.tracker.publishIfChanged(), what);
					assertSame(LightSnapshot.EMPTY, LightSnapshot.current(), what);
				}
			}
		}

		// The edge of the real world is fine.
		assertTrue(this.tracker.update(1, 29_999_999.5, 72.0, -29_999_999.5, 15, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		assertEquals(15.0, LightSnapshot.current().lightAtPoint(29_999_999.5, 72.0, -29_999_999.5));
	}

	@Test
	void levelChangeAlsoDropsRequestsTheRendererHasNotTaken() {
		this.rendererTakesRequests = false;
		this.tracker.update(1, 8.0, 72.0, 8.0, 14, THRESHOLD);
		this.tracker.publishIfChanged();
		assertEquals(Set.of("0,4,0"), this.flushAll());
		assertEquals(1, this.tracker.queue().retrying());
		// Offered again in the next tick.
		assertEquals(Set.of("0,4,0"), this.flushAll());

		this.tracker.reset();

		assertEquals(0, this.tracker.queue().retrying());
		assertTrue(this.tracker.queue().isEmpty());
		assertEquals(Set.of(), this.flushAll());
		assertEquals(0, this.markCalls);
	}

	@Test
	void switchingOffKeepsOfferingSectionsTheRendererHasNotTaken() {
		this.commit(1, 8.0, 72.0, 8.0, 14);
		this.rendererTakesRequests = false;

		this.tracker.switchOff();
		assertEquals(Set.of("0,4,0"), this.flushAll());
		assertEquals(Set.of("0,4,0"), this.flushAll());

		this.rendererTakesRequests = true;
		assertEquals(Set.of("0,4,0"), this.flushAll());
		assertEquals(Set.of(), this.flushAll());
		assertTrue(this.tracker.queue().isEmpty());
	}

	@Test
	void publishedSnapshotsAreCounted() {
		assertEquals(0, this.tracker.takePublished());
		this.commit(1, 8.0, 72.0, 8.0, 14);
		this.tracker.update(1, 9.0, 72.0, 8.0, 14, THRESHOLD);
		this.tracker.update(2, 30.0, 72.0, 8.0, 14, THRESHOLD);
		// Two changes in one tick are one snapshot.
		assertTrue(this.tracker.publishIfChanged());

		assertEquals(2, this.tracker.takePublished());
		assertEquals(0, this.tracker.takePublished());
	}

	/**
	 * Adds or moves a source and plays the rest of the tick: publish, then rebuild everything queued.
	 */
	private void commit(int id, double x, double y, double z, int luminance) {
		assertTrue(this.tracker.update(id, x, y, z, luminance, THRESHOLD));
		assertTrue(this.tracker.publishIfChanged());
		this.flushAll();
	}

	private Set<String> flushAll() {
		this.marked.clear();
		this.markCalls = 0;
		this.tracker.queue().flush(0, 0, 0, Integer.MAX_VALUE, (x, y, z) -> {
			this.marked.add(x + "," + y + "," + z);
			this.markCalls++;
			return this.rendererTakesRequests;
		});
		assertEquals(this.markCalls, this.marked.size(), "a section was marked twice");
		return new TreeSet<>(this.marked);
	}

	/**
	 * The sections to rebuild for a light with the given reach, within the level of these tests.
	 */
	private static Set<String> sections(double x, double y, double z, double reach) {
		Set<String> sections = new TreeSet<>();
		Sections.collect(
			x, y, z, reach, LightTracker.REBUILD_MARGIN, MIN_SECTION_Y, MAX_SECTION_Y,
			key -> sections.add(SectionKey.x(key) + "," + SectionKey.y(key) + "," + SectionKey.z(key))
		);
		return sections;
	}
}
