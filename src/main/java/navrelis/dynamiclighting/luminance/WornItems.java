package navrelis.dynamiclighting.luminance;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Light of the items a player wears in Accessories and Trinkets slots.
 * <p>
 * Both mods are asked if both are installed. Each one is only touched if it is installed, and
 * each is guarded by a {@link WornSource}, which switches it off after repeated failures. Asking is
 * not free on their side, so the result is kept per player for a few ticks. Client thread only.
 */
final class WornItems {
	/** How long, in ticks, a result is reused. */
	static final int LIFETIME = 10;
	private static final int MAX_CACHED_PLAYERS = 256;

	// Lambdas, not method references: the compat classes name types of mods that may be missing
	// and must not be touched before a scan is really made.
	private static final WornSource ACCESSORIES = new WornSource(
		"Accessories", FabricLoader.getInstance().isModLoaded("accessories"), (player, items) -> AccessoriesCompat.scan(player, items)
	);
	private static final WornSource TRINKETS = new WornSource(
		"Trinkets", FabricLoader.getInstance().isModLoaded("trinkets"), (player, items) -> TrinketsCompat.scan(player, items)
	);
	private static final Int2ObjectOpenHashMap<Cached> CACHE = new Int2ObjectOpenHashMap<>();

	private WornItems() {
	}

	/**
	 * @return luminance 0 to 15 of the brightest worn accessory, 0 without an accessory mod
	 */
	static int luminance(Player player, LightRules rules) {
		if (!ACCESSORIES.active() && !TRINKETS.active()) {
			return 0;
		}

		Cached cached = CACHE.get(player.getId());
		int age = cached == null ? LIFETIME : player.tickCount - cached.tick;

		if (cached == null || age < 0 || age >= LIFETIME || cached.items != rules.items() || cached.identity != System.identityHashCode(player)) {
			cached = refresh(player, rules.items(), cached);
		}

		if (cached.dry == cached.wet) {
			return cached.dry;
		}

		return rules.options().waterSensitive() && player.isEyeInFluid(FluidTags.WATER) ? cached.wet : cached.dry;
	}

	/** Forgets all results, for example because the item data changed. */
	static void clear() {
		CACHE.clear();
	}

	private static Cached refresh(Player player, ItemTable items, Cached cached) {
		int packed = merge(ACCESSORIES.scan(player, items), TRINKETS.scan(player, items));

		if (cached == null) {
			if (CACHE.size() >= MAX_CACHED_PLAYERS) {
				CACHE.clear();
			}

			cached = new Cached();
			CACHE.put(player.getId(), cached);
		}

		cached.identity = System.identityHashCode(player);
		cached.tick = player.tickCount;
		cached.items = items;
		cached.dry = packed & 15;
		cached.wet = packed >> 4;
		return cached;
	}

	/**
	 * Adds one worn stack to a packed pair of results: dry light in the low four bits, wet light above.
	 */
	static int add(int packed, ItemTable items, ItemStack stack) {
		ItemTable.Entry entry = items.entry(stack);

		if (entry == null) {
			return packed;
		}

		return merge(packed, entry.luminance(stack, false) | entry.luminance(stack, true) << 4);
	}

	private static int merge(int a, int b) {
		return Math.max(a & 15, b & 15) | Math.max(a >> 4, b >> 4) << 4;
	}

	/** Result for one player. Holds numbers only, never the player. */
	private static final class Cached {
		int identity;
		int tick;
		ItemTable items;
		int dry;
		int wet;
	}
}
