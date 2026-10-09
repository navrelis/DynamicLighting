package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RebuildQueueTest {
	private final RebuildQueue queue = new RebuildQueue();
	private final List<int[]> marked = new ArrayList<>();
	private final RebuildQueue.SectionMarker marker = (x, y, z) -> this.marked.add(new int[] {x, y, z});

	@Test
	void sectionIsQueuedOnceHoweverOftenItIsRequested() {
		this.queue.add(1, 2, 3);
		this.queue.add(1, 2, 3);
		this.queue.addBox(24.0, 40.0, 56.0, 8.0, Integer.MIN_VALUE, Integer.MAX_VALUE);
		this.queue.addBox(24.0, 40.0, 56.0, 8.0, Integer.MIN_VALUE, Integer.MAX_VALUE);
		// A second light whose box overlaps the first one's sections.
		this.queue.addBox(30.0, 40.0, 56.0, 8.0, Integer.MIN_VALUE, Integer.MAX_VALUE);

		assertEquals(2, this.queue.size());
		assertEquals(2, this.queue.flush(0, 0, 0, 100, this.marker));
		assertEquals(Set.of("1,2,3", "2,2,3"), this.markedSet());
		assertEquals(2, this.marked.size());
		assertEquals(0, this.queue.size());
	}

	@Test
	void flushOfAnEmptyQueueDoesNothing() {
		assertEquals(0, this.queue.flush(0, 0, 0, 64, this.marker));
		assertTrue(this.marked.isEmpty());
	}

	@Test
	void nearestToTheCameraComesFirst() {
		int cameraX = -3;
		int cameraY = 4;
		int cameraZ = 7;
		Random random = new Random(1L);

		for (int i = 0; i < 400; i++) {
			this.queue.add(cameraX + random.nextInt(41) - 20, cameraY + random.nextInt(9) - 4, cameraZ + random.nextInt(41) - 20);
		}

		int queued = this.queue.size();
		assertEquals(queued, this.queue.flush(cameraX, cameraY, cameraZ, Integer.MAX_VALUE, this.marker));
		assertEquals(queued, this.marked.size());

		long previous = -1;

		for (int[] section : this.marked) {
			long distance = distanceSquared(section, cameraX, cameraY, cameraZ);
			assertTrue(distance >= previous, "section at distance " + distance + " after one at " + previous);
			previous = distance;
		}
	}

	@Test
	void budgetLimitsAFlushAndTheRestIsCarriedOver() {
		for (int x = 1; x <= 10; x++) {
			this.queue.add(x, 0, 0);
		}

		assertEquals(3, this.queue.flush(0, 0, 0, 3, this.marker));
		assertEquals(List.of("1,0,0", "2,0,0", "3,0,0"), this.markedList());
		assertEquals(7, this.queue.size());

		// Requests made in the meantime join the queue and are ordered with the rest.
		this.queue.add(-1, 0, 0);
		this.queue.add(5, 0, 0);
		this.marked.clear();
		assertEquals(4, this.queue.flush(0, 0, 0, 4, this.marker));
		assertEquals(List.of("-1,0,0", "4,0,0", "5,0,0", "6,0,0"), this.markedList());
		assertEquals(4, this.queue.size());

		this.marked.clear();
		assertEquals(4, this.queue.flush(0, 0, 0, 64, this.marker));
		assertEquals(List.of("7,0,0", "8,0,0", "9,0,0", "10,0,0"), this.markedList());
		assertEquals(0, this.queue.size());

		this.marked.clear();
		assertEquals(0, this.queue.flush(0, 0, 0, 64, this.marker));
		assertTrue(this.marked.isEmpty());
	}

	@Test
	void everySectionIsHandedOutExactlyOnceOverSeveralFlushes() {
		Random random = new Random(2L);
		Set<String> expected = new HashSet<>();

		for (int i = 0; i < 1_000; i++) {
			int x = random.nextInt(61) - 30;
			int y = random.nextInt(24) - 4;
			int z = random.nextInt(61) - 30;
			this.queue.add(x, y, z);
			expected.add(x + "," + y + "," + z);
		}

		assertEquals(expected.size(), this.queue.size());

		int flushes = 0;
		long previousFlushFarthest = -1;

		while (this.queue.size() > 0) {
			int before = this.marked.size();
			int handedOut = this.queue.flush(2, 3, -4, 64, this.marker);
			assertTrue(handedOut > 0 && handedOut <= 64);
			assertEquals(before + handedOut, this.marked.size());

			// Nothing handed out later is nearer than what an earlier flush handed out.
			long nearest = Long.MAX_VALUE;
			long farthest = -1;

			for (int[] section : this.marked.subList(before, this.marked.size())) {
				long distance = distanceSquared(section, 2, 3, -4);
				nearest = Math.min(nearest, distance);
				farthest = Math.max(farthest, distance);
			}

			assertTrue(nearest >= previousFlushFarthest);
			previousFlushFarthest = farthest;
			flushes++;
		}

		assertEquals((expected.size() + 63) / 64, flushes);
		assertEquals(expected.size(), this.marked.size());
		assertEquals(expected, this.markedSet());
	}

	@Test
	void zeroBudgetKeepsEverythingQueued() {
		this.queue.add(0, 0, 0);
		this.queue.add(1, 0, 0);

		assertEquals(0, this.queue.flush(0, 0, 0, 0, this.marker));
		assertTrue(this.marked.isEmpty());
		assertEquals(2, this.queue.size());
	}

	@Test
	void clearDropsPendingSectionsWithoutMarkingThem() {
		for (int x = 0; x < 5_000; x++) {
			this.queue.add(x, 0, 0);
		}

		this.queue.clear();

		assertEquals(0, this.queue.size());
		assertEquals(0, this.queue.flush(0, 0, 0, 64, this.marker));
		assertTrue(this.marked.isEmpty());

		// Still usable afterwards.
		this.queue.add(-7, 3, 9);
		assertEquals(1, this.queue.flush(0, 0, 0, 64, this.marker));
		assertEquals(List.of("-7,3,9"), this.markedList());
	}

	@Test
	void farAwayAndNegativeSectionsKeepTheirCoordinates() {
		this.queue.add(-1_875_000, -4, 1_874_999);
		this.queue.add(1_874_999, 19, -1_875_000);
		this.queue.add(0, 0, 0);

		assertEquals(3, this.queue.flush(1_874_000, 5, -1_874_000, 64, this.marker));
		assertEquals(List.of("1874999,19,-1875000", "0,0,0", "-1875000,-4,1874999"), this.markedList());
	}

	private static long distanceSquared(int[] section, int x, int y, int z) {
		long dx = section[0] - x;
		long dy = section[1] - y;
		long dz = section[2] - z;
		return dx * dx + dy * dy + dz * dz;
	}

	private List<String> markedList() {
		List<String> sections = new ArrayList<>();

		for (int[] section : this.marked) {
			sections.add(section[0] + "," + section[1] + "," + section[2]);
		}

		return sections;
	}

	private Set<String> markedSet() {
		return new HashSet<>(this.markedList());
	}
}
