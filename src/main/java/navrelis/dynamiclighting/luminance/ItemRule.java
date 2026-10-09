package navrelis.dynamiclighting.luminance;

import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * One decoded item file.
 *
 * @param file           where the rule comes from
 * @param predicate      which stacks it applies to
 * @param luminance      0 to 15, or {@link #BLOCK_SELF}
 * @param waterSensitive the rule gives no light while the stack is wet
 */
record ItemRule(ResourceLocation file, ItemPredicate predicate, int luminance, boolean waterSensitive) {
	/** Luminance marker: the light of the default state of the block the item places. */
	static final int BLOCK_SELF = -1;

	/**
	 * Whether the rule depends on the item alone. Such a rule is folded into the item table;
	 * every other rule has to look at each stack.
	 */
	boolean itemOnly() {
		return this.predicate.items().isPresent()
			&& this.predicate.count().isAny()
			&& this.predicate.components().alwaysMatches()
			&& this.predicate.subPredicates().isEmpty();
	}

	int luminanceOf(Item item) {
		if (this.luminance != BLOCK_SELF) {
			return this.luminance;
		}

		return Math.min(15, Block.byItem(item).defaultBlockState().getLightEmission());
	}
}
