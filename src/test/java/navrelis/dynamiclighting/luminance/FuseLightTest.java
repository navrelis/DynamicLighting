package navrelis.dynamiclighting.luminance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import navrelis.dynamiclighting.config.FuseMode;
import org.junit.jupiter.api.Test;

class FuseLightTest {
	@Test
	void tntIsDarkWhenOff() {
		for (int fuse = -5; fuse <= 400; fuse++) {
			assertEquals(0, FuseLight.tnt(FuseMode.OFF, fuse));
		}
	}

	@Test
	void tntIsConstantWhenSimple() {
		for (int fuse = -5; fuse <= 400; fuse++) {
			assertEquals(10, FuseLight.tnt(FuseMode.SIMPLE, fuse));
		}
	}

	@Test
	void fancyTntRunsFromFourToFourteen() {
		assertEquals(4, FuseLight.tnt(FuseMode.FANCY, 80));
		assertEquals(9, FuseLight.tnt(FuseMode.FANCY, 40));
		assertEquals(14, FuseLight.tnt(FuseMode.FANCY, 0));
	}

	@Test
	void fancyTntIsClampedOutsideTheNormalFuse() {
		// A long fuse (/summon with fuse:400) stays at the dimmest value instead of going dark or negative.
		assertEquals(4, FuseLight.tnt(FuseMode.FANCY, 81));
		assertEquals(4, FuseLight.tnt(FuseMode.FANCY, 400));
		assertEquals(4, FuseLight.tnt(FuseMode.FANCY, Integer.MAX_VALUE));
		assertEquals(14, FuseLight.tnt(FuseMode.FANCY, -1));
		assertEquals(14, FuseLight.tnt(FuseMode.FANCY, Integer.MIN_VALUE));
	}

	@Test
	void fancyTntNeverGetsDarkerAsTheFuseRunsDown() {
		int previous = FuseLight.tnt(FuseMode.FANCY, 200);

		for (int fuse = 199; fuse >= -10; fuse--) {
			int light = FuseLight.tnt(FuseMode.FANCY, fuse);
			assertTrue(light >= previous, "light dropped at fuse " + fuse);
			assertTrue(light - previous <= 1, "light jumped at fuse " + fuse);
			assertTrue(light >= 4 && light <= 14, "light out of bounds at fuse " + fuse);
			previous = light;
		}
	}

	@Test
	void calmCreeperIsDarkInEveryMode() {
		for (FuseMode mode : FuseMode.values()) {
			assertEquals(0, FuseLight.creeper(mode, 0.0F));
			assertEquals(0, FuseLight.creeper(mode, -1.0F));
			assertEquals(0, FuseLight.creeper(mode, Float.NaN));
		}
	}

	@Test
	void swellingCreeperFollowsTheMode() {
		assertEquals(0, FuseLight.creeper(FuseMode.OFF, 0.5F));
		assertEquals(10, FuseLight.creeper(FuseMode.SIMPLE, 0.01F));
		assertEquals(10, FuseLight.creeper(FuseMode.SIMPLE, 1.0F));
	}

	@Test
	void fancyCreeperRunsFromTwoToTwelve() {
		assertEquals(2, FuseLight.creeper(FuseMode.FANCY, 0.01F));
		assertEquals(7, FuseLight.creeper(FuseMode.FANCY, 0.5F));
		assertEquals(12, FuseLight.creeper(FuseMode.FANCY, 1.0F));
		// getSwelling divides by (maxSwell - 2), so it ends slightly above 1.
		assertEquals(12, FuseLight.creeper(FuseMode.FANCY, 30.0F / 28.0F));
		assertEquals(12, FuseLight.creeper(FuseMode.FANCY, Float.POSITIVE_INFINITY));
	}

	@Test
	void fancyCreeperNeverGetsDarkerWhileSwelling() {
		int previous = 0;

		for (int swell = 1; swell <= 30; swell++) {
			int light = FuseLight.creeper(FuseMode.FANCY, swell / 28.0F);
			assertTrue(light >= previous, "light dropped at swell " + swell);
			assertTrue(light >= 2 && light <= 12, "light out of bounds at swell " + swell);
			previous = light;
		}
	}
}
