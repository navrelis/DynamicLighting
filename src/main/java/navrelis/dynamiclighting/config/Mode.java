package navrelis.dynamiclighting.config;

public enum Mode {
	OFF,
	FASTEST,
	FAST,
	FANCY;

	public boolean enabled() {
		return this != OFF;
	}
}
