package navrelis.dynamiclighting.luminance;

import static navrelis.dynamiclighting.luminance.TestGame.compile;
import static navrelis.dynamiclighting.luminance.TestGame.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemTableTest {
	private static ItemTable fallbackOnly;

	@BeforeAll
	static void boot() {
		TestGame.registries();
		fallbackOnly = ItemTable.build(List.of());
	}

	private static ItemTable table(RawFile... files) {
		CompiledData data = compile(files);
		assertEquals(List.of(), data.skips());
		return ItemTable.build(data.itemRules());
	}

	private static RawFile rule(String name, String match, String luminance, boolean waterSensitive) {
		return item(name, "{ \"match\": " + match + ", \"luminance\": " + luminance + ", \"water_sensitive\": " + waterSensitive + " }");
	}

	private static RawFile rule(String name, Item target, int luminance, boolean waterSensitive) {
		return rule(name, "{ \"items\": \"" + BuiltInRegistries.ITEM.getKey(target) + "\" }", Integer.toString(luminance), waterSensitive);
	}

	private static void assertLight(int dry, int wet, ItemTable table, ItemStack stack) {
		assertEquals(dry, table.luminance(stack, false), "dry " + stack);
		assertEquals(wet, table.luminance(stack, true), "wet " + stack);
	}

	// Block fallback

	@Test
	void blockItemsGlowLikeTheirBlockWithoutAnyFile() {
		assertLight(15, 15, fallbackOnly, new ItemStack(Items.LANTERN));
		assertLight(15, 15, fallbackOnly, new ItemStack(Items.GLOWSTONE));
		assertLight(14, 14, fallbackOnly, new ItemStack(Items.TORCH));
		assertLight(10, 10, fallbackOnly, new ItemStack(Items.SOUL_LANTERN));
		assertLight(15, 15, fallbackOnly, new ItemStack(Items.SEA_LANTERN));
		assertLight(15, 15, fallbackOnly, new ItemStack(Items.SHROOMLIGHT));
		assertLight(14, 14, fallbackOnly, new ItemStack(Items.END_ROD));
		assertLight(7, 7, fallbackOnly, new ItemStack(Items.REDSTONE_TORCH));
	}

	@Test
	void itemsThatEmitNothingHaveNoEntry() {
		assertNull(fallbackOnly.entry(new ItemStack(Items.STICK)));
		assertNull(fallbackOnly.entry(new ItemStack(Items.STONE)));
		assertNull(fallbackOnly.entry(new ItemStack(Items.DIAMOND_SWORD)));
		assertNull(fallbackOnly.entry(ItemStack.EMPTY));
		assertNull(fallbackOnly.entry(new ItemStack(Items.TORCH, 0)));
		assertLight(0, 0, fallbackOnly, ItemStack.EMPTY);
		assertLight(0, 0, fallbackOnly, new ItemStack(Items.STICK));
		// Far fewer entries than items: most lookups end at the map.
		assertTrue(fallbackOnly.size() > 20 && fallbackOnly.size() < 300, "entries: " + fallbackOnly.size());
	}

	@Test
	void blocksThatAreDarkByDefaultAreDarkAsItems() {
		assertLight(0, 0, fallbackOnly, new ItemStack(Items.REDSTONE_LAMP));
		assertLight(0, 0, fallbackOnly, new ItemStack(Items.CANDLE));
		assertLight(0, 0, fallbackOnly, new ItemStack(Items.FURNACE));
	}

	// Rules

	@Test
	void aRuleReplacesTheBlockFallback() {
		ItemTable table = table(rule("dim", Items.GLOWSTONE, 3, false), rule("off", Items.LANTERN, 0, false));

		assertLight(3, 3, table, new ItemStack(Items.GLOWSTONE));
		assertLight(0, 0, table, new ItemStack(Items.LANTERN));
		assertNull(table.entry(new ItemStack(Items.LANTERN)));
		// Untouched items keep their fallback.
		assertLight(15, 15, table, new ItemStack(Items.SEA_LANTERN));
	}

	@Test
	void aRuleLightsItemsThatAreNotBlocks() {
		ItemTable table = table(rule("rod", Items.BLAZE_ROD, 10, false));

		assertLight(10, 10, table, new ItemStack(Items.BLAZE_ROD));
		assertFalse(table.entry(new ItemStack(Items.BLAZE_ROD)).waterDependent());
	}

	@Test
	void aWetTorchIsDark() {
		ItemTable table = table(rule("torch", "{ \"items\": \"minecraft:torch\" }", "{ \"type\": \"block_self\" }", true));

		assertLight(14, 0, table, new ItemStack(Items.TORCH));
		assertTrue(table.entry(new ItemStack(Items.TORCH)).waterDependent());
		// The redstone torch has no rule: it is not fire and stays lit under water.
		assertLight(7, 7, table, new ItemStack(Items.REDSTONE_TORCH));
		assertFalse(table.entry(new ItemStack(Items.REDSTONE_TORCH)).waterDependent());
	}

	@Test
	void severalRulesGiveTheBrightestThatWaterDoesNotSwitchOff() {
		ItemTable table = table(
			rule("low", Items.STICK, 5, false),
			rule("high", Items.STICK, 9, true),
			rule("mid", "{ \"items\": [\"minecraft:stick\", \"minecraft:bone\"] }", "7", true)
		);

		assertLight(9, 5, table, new ItemStack(Items.STICK));
		assertLight(7, 0, table, new ItemStack(Items.BONE));
	}

	@Test
	void tagRulesReachEveryItemOfTheTag() {
		ItemTable table = table(rule("tag", "{ \"items\": \"#lighttest:glowing\" }", "6", false));

		assertLight(6, 6, table, new ItemStack(Items.GLOWSTONE_DUST));
		assertLight(6, 6, table, new ItemStack(Items.BLAZE_ROD));
		assertLight(0, 0, table, new ItemStack(Items.BLAZE_POWDER));
	}

	// Rules that look at the stack

	@Test
	void countPredicateIsTestedPerStack() {
		ItemTable table = table(rule("bundle", "{ \"items\": \"minecraft:torch\", \"count\": { \"min\": 2 } }", "3", false));

		// One torch: the rule does not match, so the block fallback applies.
		assertLight(14, 14, table, new ItemStack(Items.TORCH, 1));
		// Two torches: the rule matches and replaces the fallback.
		assertLight(3, 3, table, new ItemStack(Items.TORCH, 2));
		assertLight(3, 3, table, new ItemStack(Items.TORCH, 64));
		// Other items never see the rule.
		assertLight(10, 10, table, new ItemStack(Items.SOUL_TORCH, 2));
	}

	@Test
	void componentPredicateIsTestedPerStack() {
		ItemTable table = table(rule("worn", "{ \"items\": \"minecraft:golden_sword\", \"components\": { \"minecraft:damage\": 3 } }", "11", true));
		ItemStack fresh = new ItemStack(Items.GOLDEN_SWORD);
		ItemStack worn = new ItemStack(Items.GOLDEN_SWORD);
		worn.set(DataComponents.DAMAGE, 3);

		assertLight(0, 0, table, fresh);
		assertLight(11, 0, table, worn);
		assertTrue(table.entry(fresh).waterDependent());
	}

	@Test
	void subPredicateWithoutItemsAppliesToEveryItem() {
		ItemTable table = table(rule("damaged", "{ \"predicates\": { \"minecraft:damage\": { \"damage\": { \"min\": 1 } } } }", "4", false));
		ItemStack damagedSword = new ItemStack(Items.IRON_SWORD);
		damagedSword.set(DataComponents.DAMAGE, 20);
		ItemStack damagedPickaxe = new ItemStack(Items.STONE_PICKAXE);
		damagedPickaxe.set(DataComponents.DAMAGE, 1);

		assertLight(4, 4, table, damagedSword);
		assertLight(4, 4, table, damagedPickaxe);
		assertLight(0, 0, table, new ItemStack(Items.IRON_SWORD));
		// Still the fallback for a block item the rule does not match.
		assertLight(15, 15, table, new ItemStack(Items.GLOWSTONE));
	}

	@Test
	void itemOnlyAndPerStackRulesCombine() {
		ItemTable table = table(
			rule("base", Items.GOLDEN_SWORD, 5, true),
			rule("worn", "{ \"items\": \"minecraft:golden_sword\", \"components\": { \"minecraft:damage\": 3 } }", "11", false),
			rule("pair", "{ \"items\": \"minecraft:golden_sword\", \"count\": 2 }", "2", false)
		);
		ItemStack worn = new ItemStack(Items.GOLDEN_SWORD);
		worn.set(DataComponents.DAMAGE, 3);

		assertLight(5, 0, table, new ItemStack(Items.GOLDEN_SWORD));
		assertLight(11, 11, table, worn);
		assertLight(5, 2, table, new ItemStack(Items.GOLDEN_SWORD, 2));
	}

	// The block_state component

	@Test
	void blockStateComponentChangesTheFallback() {
		ItemStack light = new ItemStack(Items.LIGHT);
		assertLight(15, 15, fallbackOnly, light);

		light.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LightBlock.LEVEL, 4));
		assertLight(4, 4, fallbackOnly, light);

		ItemStack lamp = new ItemStack(Items.REDSTONE_LAMP);
		lamp.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RedstoneLampBlock.LIT, true));
		assertLight(15, 15, fallbackOnly, lamp);

		ItemStack candles = new ItemStack(Items.CANDLE);
		candles.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CandleBlock.LIT, true).with(CandleBlock.CANDLES, 4));
		assertLight(12, 12, fallbackOnly, candles);
	}

	@Test
	void emptyOrForeignBlockStateComponentLeavesTheDefault() {
		ItemStack lamp = new ItemStack(Items.REDSTONE_LAMP);
		lamp.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
		assertLight(0, 0, fallbackOnly, lamp);

		ItemStack glowstone = new ItemStack(Items.GLOWSTONE);
		glowstone.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RedstoneLampBlock.LIT, false));
		assertLight(15, 15, fallbackOnly, glowstone);
	}

	@Test
	void aMatchingRuleAlsoReplacesTheBlockStateFallback() {
		ItemTable table = table(rule("lamp", Items.REDSTONE_LAMP, 2, false));
		ItemStack lamp = new ItemStack(Items.REDSTONE_LAMP);
		lamp.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RedstoneLampBlock.LIT, true));

		assertLight(2, 2, table, lamp);
		assertNotNull(table.entry(lamp));
	}
}
