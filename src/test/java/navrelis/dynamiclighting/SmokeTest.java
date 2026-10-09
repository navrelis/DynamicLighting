package navrelis.dynamiclighting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
	@Test
	void modIdIsStable() {
		assertEquals("dynamiclighting", DynamicLighting.MOD_ID);
	}
}
