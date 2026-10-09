package navrelis.dynamiclighting.config;

public enum LightRange {
	SHORT,
	LONG;

	/**
	 * Light levels lost per block of distance.
	 */
	public float falloffPerBlock() {
		return this == SHORT ? 2.0f : 1.0f;
	}
}
