package navrelis.dynamiclighting.luminance;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.world.entity.player.Player;

/**
 * The only class that names Accessories types. {@link WornItems} loads it only if the mod is
 * installed and guards every call.
 */
final class AccessoriesCompat {
	private AccessoriesCompat() {
	}

	/**
	 * @return dry and wet light of everything worn, packed as {@link WornItems#add} describes
	 */
	static int scan(Player player, ItemTable items) {
		AccessoriesCapability capability = AccessoriesCapability.get(player);

		if (capability == null) {
			return 0;
		}

		int packed = 0;

		for (SlotEntryReference entry : capability.getAllEquipped()) {
			packed = WornItems.add(packed, items, entry.stack());
		}

		return packed;
	}
}
