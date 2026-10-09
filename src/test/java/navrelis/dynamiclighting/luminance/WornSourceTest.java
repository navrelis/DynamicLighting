package navrelis.dynamiclighting.luminance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The guard around an accessory mod, with a scanner that fails on demand. No game object is needed:
 * the guard only passes its arguments on.
 */
class WornSourceTest {
	private static final int WORN = 14 | 3 << 4;

	private int calls;
	private boolean failing;

	private final WornSource source = new WornSource("Test", true, (player, items) -> {
		this.calls++;

		if (this.failing) {
			// What a changed API looks like at run time.
			throw new NoSuchMethodError("scan " + this.calls);
		}

		return WORN;
	});

	private int scan() {
		return this.source.scan(null, null);
	}

	@Test
	void workingSourcePassesItsResultOn() {
		assertTrue(this.source.active());
		assertEquals(WORN, this.scan());
		assertEquals(WORN, this.scan());
		assertEquals(2, this.calls);
	}

	@Test
	void oneFailureGivesNoLightButKeepsTheSourceOn() {
		this.failing = true;
		assertEquals(0, this.scan());
		assertTrue(this.source.active());

		this.failing = false;
		assertEquals(WORN, this.scan());
		assertTrue(this.source.active());
	}

	@Test
	void threeFailuresInARowSwitchTheSourceOff() {
		assertEquals(3, WornSource.MAX_FAILURES);
		this.failing = true;

		assertEquals(0, this.scan());
		assertEquals(0, this.scan());
		assertTrue(this.source.active());
		assertEquals(0, this.scan());
		assertFalse(this.source.active());

		// Off for good: the mod is not asked again, even if it would work now.
		this.failing = false;
		assertEquals(0, this.scan());
		assertEquals(0, this.scan());
		assertEquals(3, this.calls);
		assertFalse(this.source.active());
	}

	@Test
	void successStartsTheCountAgain() {
		for (int round = 0; round < 10; round++) {
			this.failing = true;
			assertEquals(0, this.scan());
			assertEquals(0, this.scan());

			this.failing = false;
			assertEquals(WORN, this.scan());
			assertTrue(this.source.active(), "switched off in round " + round);
		}

		// Twenty failures in all, never three in a row.
		assertEquals(30, this.calls);

		this.failing = true;
		this.scan();
		this.scan();
		assertTrue(this.source.active());
		this.scan();
		assertFalse(this.source.active());
	}

	@Test
	void everyKindOfThrowableCounts() {
		WornSource broken = new WornSource("Test", true, (player, items) -> {
			throw new StackOverflowError();
		});

		assertEquals(0, broken.scan(null, null));
		assertEquals(0, broken.scan(null, null));
		assertEquals(0, broken.scan(null, null));
		assertFalse(broken.active());
	}

	@Test
	void missingModIsNeverAsked() {
		WornSource missing = new WornSource("Test", false, (player, items) -> {
			this.calls++;
			return WORN;
		});

		assertFalse(missing.active());
		assertEquals(0, missing.scan(null, null));
		assertEquals(0, this.calls);
	}
}
