package navrelis.dynamiclighting.luminance;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import navrelis.dynamiclighting.DynamicLighting;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The light of every item, worked out once when data is applied.
 * <p>
 * A lookup is one probe in an identity map; items that emit nothing have no entry at all. Only
 * items with a rule on count, components or sub-predicates, and block items whose block is not
 * equally bright in all states, look at the stack itself, and then only at the rules that can
 * apply to that item. Keyed by the item object, not its raw registry id, because Fabric remaps
 * raw ids when a server is joined.
 * <p>
 * Immutable after construction.
 */
final class ItemTable {
	private final Reference2ObjectOpenHashMap<Item, Entry> entries;

	private ItemTable(Reference2ObjectOpenHashMap<Item, Entry> entries) {
		this.entries = entries;
	}

	/**
	 * Builds the table for all registered items.
	 *
	 * @param rules the active item rules; an empty list leaves only the block fallback
	 */
	static ItemTable build(List<ItemRule> rules) {
		Map<Item, List<ItemRule>> byItem = new IdentityHashMap<>();
		List<ItemRule> forAnyItem = new ArrayList<>();

		for (ItemRule rule : rules) {
			Optional<HolderSet<Item>> items = rule.predicate().items();

			if (items.isEmpty()) {
				forAnyItem.add(rule);
				continue;
			}

			for (Holder<Item> holder : items.get()) {
				byItem.computeIfAbsent(holder.value(), item -> new ArrayList<>()).add(rule);
			}
		}

		Reference2ObjectOpenHashMap<Item, Entry> entries = new Reference2ObjectOpenHashMap<>();
		int failed = 0;

		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}

			try {
				Entry entry = Entry.of(item, byItem.getOrDefault(item, List.of()), forAnyItem);

				if (entry != null) {
					entries.put(item, entry);
				}
			} catch (RuntimeException e) {
				// One odd modded item or block must not cost every other item its light.
				failed++;
				DynamicLighting.LOGGER.debug("Could not work out the light of item {}", BuiltInRegistries.ITEM.getKey(item), e);
			}
		}

		if (failed > 0) {
			DynamicLighting.LOGGER.warn("{} items were left without light because their block or rules could not be read.", failed);
		}

		entries.trim();
		return new ItemTable(entries);
	}

	/**
	 * @return the entry for the stack's item, or {@code null} if no stack of that item ever emits
	 */
	@Nullable
	Entry entry(ItemStack stack) {
		// An empty stack reports air as its item, and air has no entry.
		return this.entries.get(stack.getItem());
	}

	/**
	 * @param wet the stack is in water and the user wants water-sensitive items to go dark
	 * @return luminance 0 to 15
	 */
	int luminance(ItemStack stack, boolean wet) {
		Entry entry = this.entry(stack);
		return entry == null ? 0 : entry.luminance(stack, wet);
	}

	/** Number of items that can emit. */
	int size() {
		return this.entries.size();
	}

	/**
	 * Everything known about one item.
	 */
	static final class Entry {
		/** Light from the item-only rules, or from the block if there is none. */
		private final int dry;
		private final int wet;
		/** An item-only rule exists, so the block fallback never applies. */
		private final boolean ruled;
		/** Rules that have to look at the stack; {@code null} if there are none. */
		@Nullable
		private final StackRule[] stackRules;
		/** Set if the block fallback has to honour a {@code block_state} component. */
		@Nullable
		private final Block stateBlock;
		private final boolean fixed;
		private final boolean waterDependent;

		private Entry(int dry, int wet, boolean ruled, @Nullable StackRule[] stackRules, @Nullable Block stateBlock) {
			this.dry = dry;
			this.wet = wet;
			this.ruled = ruled;
			this.stackRules = stackRules;
			this.stateBlock = stateBlock;
			this.fixed = stackRules == null && stateBlock == null;

			boolean waterDependent = dry != wet;

			if (stackRules != null) {
				for (StackRule rule : stackRules) {
					waterDependent |= rule.waterSensitive;
				}
			}

			this.waterDependent = waterDependent;
		}

		@Nullable
		private static Entry of(Item item, List<ItemRule> forItem, List<ItemRule> forAnyItem) {
			int dry = 0;
			int wet = 0;
			boolean ruled = false;
			List<StackRule> stackRules = new ArrayList<>();

			for (int pass = 0; pass < 2; pass++) {
				for (ItemRule rule : pass == 0 ? forItem : forAnyItem) {
					int value = rule.luminanceOf(item);

					if (rule.itemOnly()) {
						ruled = true;
						dry = Math.max(dry, value);

						if (!rule.waterSensitive()) {
							wet = Math.max(wet, value);
						}
					} else {
						stackRules.add(new StackRule(rule.predicate(), value, rule.waterSensitive()));
					}
				}
			}

			Block stateBlock = null;

			if (!ruled && item instanceof BlockItem blockItem) {
				Block block = blockItem.getBlock();
				dry = wet = emission(block.defaultBlockState());

				if (emissionVaries(block)) {
					stateBlock = block;
				}
			}

			if (dry == 0 && wet == 0 && stackRules.isEmpty() && stateBlock == null) {
				return null;
			}

			return new Entry(dry, wet, ruled, stackRules.isEmpty() ? null : stackRules.toArray(StackRule[]::new), stateBlock);
		}

		private static boolean emissionVaries(Block block) {
			int emission = block.defaultBlockState().getLightEmission();

			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (state.getLightEmission() != emission) {
					return true;
				}
			}

			return false;
		}

		private static int emission(BlockState state) {
			return Math.min(15, state.getLightEmission());
		}

		/** Whether water can change the result, so that callers only test for water when it matters. */
		boolean waterDependent() {
			return this.waterDependent;
		}

		int luminance(ItemStack stack, boolean wet) {
			if (this.fixed) {
				return wet ? this.wet : this.dry;
			}

			boolean matched = this.ruled;
			int value = 0;

			if (matched) {
				value = wet ? this.wet : this.dry;
			}

			if (this.stackRules != null) {
				for (StackRule rule : this.stackRules) {
					if (rule.predicate.test(stack)) {
						matched = true;

						if (!(wet && rule.waterSensitive)) {
							value = Math.max(value, rule.luminance);
						}
					}
				}
			}

			if (matched) {
				// A matching rule replaces the block fallback, also when water switched it off.
				return value;
			}

			if (this.stateBlock != null) {
				BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);

				if (properties != null && !properties.isEmpty()) {
					return emission(properties.apply(this.stateBlock.defaultBlockState()));
				}
			}

			return this.dry;
		}
	}

	private record StackRule(ItemPredicate predicate, int luminance, boolean waterSensitive) {
	}
}
