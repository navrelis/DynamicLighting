package navrelis.dynamiclighting.luminance;

import java.util.Optional;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The only class that names Trinkets types. {@link WornItems} loads it only if the mod is
 * installed and guards every call.
 */
final class TrinketsCompat {
	private TrinketsCompat() {
	}

	/**
	 * @return dry and wet light of everything worn, packed as {@link WornItems#add} describes
	 */
	static int scan(Player player, ItemTable items) {
		Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(player);

		if (component.isEmpty()) {
			return 0;
		}

		int packed = 0;

		for (Tuple<SlotReference, ItemStack> entry : component.get().getAllEquipped()) {
			packed = WornItems.add(packed, items, entry.getB());
		}

		return packed;
	}
}
