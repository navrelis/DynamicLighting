package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import navrelis.dynamiclighting.config.LightRange;
import navrelis.dynamiclighting.config.Mode;
import org.junit.jupiter.api.Test;

class TuningTest {
	private static final double EXACT = 1.0e-12;

	@Test
	void intervalGrowsWithModeAndDistance() {
		assertEquals(1, Tuning.interval(Mode.FANCY, 0.0));
		assertEquals(1, Tuning.interval(Mode.FANCY, 32.0 * 32.0));
		assertEquals(2, Tuning.interval(Mode.FANCY, 32.1 * 32.1));
		assertEquals(2, Tuning.interval(Mode.FANCY, 64.0 * 64.0));
		assertEquals(4, Tuning.interval(Mode.FANCY, 64.1 * 64.1));
		assertEquals(4, Tuning.interval(Mode.FANCY, 1.0e12));

		assertEquals(2, Tuning.interval(Mode.FAST, 10.0 * 10.0));
		assertEquals(4, Tuning.interval(Mode.FAST, 50.0 * 50.0));
		assertEquals(8, Tuning.interval(Mode.FAST, 100.0 * 100.0));

		assertEquals(4, Tuning.interval(Mode.FASTEST, 10.0 * 10.0));
		assertEquals(8, Tuning.interval(Mode.FASTEST, 50.0 * 50.0));
		assertEquals(16, Tuning.interval(Mode.FASTEST, 100.0 * 100.0));
	}

	@Test
	void everyEntityIsDueOncePerInterval() {
		for (int interval : new int[] {1, 2, 4, 8, 16}) {
			for (int id : new int[] {0, 1, 2, 3, 17, 4096, 123_456_789, Integer.MAX_VALUE, -5}) {
				for (int start : new int[] {0, 1, 999, Integer.MAX_VALUE - 7}) {
					int due = 0;

					for (int i = 0; i < interval; i++) {
						if (Tuning.isDue(start + i, id, interval)) {
							due++;
						}
					}

					assertEquals(1, due, "interval " + interval + ", id " + id + ", from tick " + start);
				}
			}
		}
	}

	@Test
	void entitiesWithTheSameIntervalAreSpreadOverItsTicks() {
		int[] duePerTick = new int[4];

		for (int id = 100; id < 500; id++) {
			for (int tick = 0; tick < 4; tick++) {
				if (Tuning.isDue(tick, id, 4)) {
					duePerTick[tick]++;
				}
			}
		}

		for (int due : duePerTick) {
			assertEquals(100, due);
		}
	}

	@Test
	void moveThresholdFollowsFalloffModeAndDistance() {
		double shortRange = LightRange.SHORT.falloffPerBlock();
		double longRange = LightRange.LONG.falloffPerBlock();

		// Whole light levels (Sodium): half a level of change, a quarter block at 2 levels per block.
		assertEquals(0.25, Tuning.moveThreshold(shortRange, Mode.FANCY, 0.0, true), EXACT);
		assertEquals(0.5, Tuning.moveThreshold(shortRange, Mode.FAST, 0.0, true), EXACT);
		assertEquals(1.0, Tuning.moveThreshold(shortRange, Mode.FASTEST, 0.0, true), EXACT);
		assertEquals(0.5, Tuning.moveThreshold(longRange, Mode.FANCY, 0.0, true), EXACT);
		assertEquals(2.0, Tuning.moveThreshold(longRange, Mode.FASTEST, 0.0, true), EXACT);

		// Sixteenths of a level show finer steps, so the best mode halves the threshold. Only that mode.
		assertEquals(0.125, Tuning.moveThreshold(shortRange, Mode.FANCY, 0.0, false), EXACT);
		assertEquals(0.25, Tuning.moveThreshold(longRange, Mode.FANCY, 0.0, false), EXACT);
		assertEquals(0.5, Tuning.moveThreshold(shortRange, Mode.FAST, 0.0, false), EXACT);
		assertEquals(1.0, Tuning.moveThreshold(shortRange, Mode.FASTEST, 0.0, false), EXACT);

		// Far lights may move further: twice at 32 blocks, three times at 64.
		assertEquals(0.5, Tuning.moveThreshold(shortRange, Mode.FANCY, 32.0, true), EXACT);
		assertEquals(0.75, Tuning.moveThreshold(shortRange, Mode.FANCY, 64.0, true), EXACT);
		assertEquals(0.375, Tuning.moveThreshold(shortRange, Mode.FANCY, 16.0, true), EXACT);
		assertEquals(0.25, Tuning.moveThreshold(shortRange, Mode.FANCY, 32.0, false), EXACT);
		assertEquals(3.0, Tuning.moveThreshold(shortRange, Mode.FASTEST, 64.0, false), EXACT);
	}

	@Test
	void budgetPerMode() {
		assertEquals(64, Tuning.budget(Mode.FANCY));
		assertEquals(32, Tuning.budget(Mode.FAST));
		assertEquals(16, Tuning.budget(Mode.FASTEST));
	}
}
