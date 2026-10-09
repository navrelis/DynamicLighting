package navrelis.dynamiclighting.luminance;

import navrelis.dynamiclighting.DynamicLighting;
import net.minecraft.world.entity.player.Player;

/**
 * One accessory mod as a source of worn items, guarded against its failures.
 * <p>
 * A failure of any kind (an exception, a changed API) gives no light for that scan. The first one
 * is logged with its stack trace. {@link #MAX_FAILURES} failures in a row switch the source off for
 * the rest of the session; a scan that works in between starts the count again, so a single bad
 * moment does not cost the integration. Client thread only.
 */
final class WornSource {
	/** Failures in a row after which the source is switched off. */
	static final int MAX_FAILURES = 3;

	/** Asks the mod what a player wears. */
	@FunctionalInterface
	interface Scanner {
		/**
		 * @return dry and wet light of everything worn, packed as {@link WornItems#add} describes
		 */
		int scan(Player player, ItemTable items);
	}

	private final String name;
	private final Scanner scanner;
	private boolean active;
	private boolean traceLogged;
	private int failures;

	/**
	 * @param name      the mod's name for log lines
	 * @param installed whether the mod is there at all; the scanner is never called if not
	 */
	WornSource(String name, boolean installed, Scanner scanner) {
		this.name = name;
		this.scanner = scanner;
		this.active = installed;
	}

	boolean active() {
		return this.active;
	}

	/**
	 * @return the scanner's result, 0 if the source is off or the scan failed
	 */
	int scan(Player player, ItemTable items) {
		if (!this.active) {
			return 0;
		}

		try {
			int packed = this.scanner.scan(player, items);
			this.failures = 0;
			return packed;
		} catch (Throwable t) {
			this.failed(t);
			return 0;
		}
	}

	private void failed(Throwable t) {
		this.failures++;

		if (!this.traceLogged) {
			this.traceLogged = true;
			DynamicLighting.LOGGER.warn(
				"{} integration failed; it is switched off after {} failures in a row", this.name, MAX_FAILURES, t
			);
		}

		if (this.failures >= MAX_FAILURES) {
			this.active = false;
			DynamicLighting.LOGGER.warn(
				"{} integration switched off after {} failures in a row, the last one: {}", this.name, MAX_FAILURES, t.toString()
			);
		}
	}
}
