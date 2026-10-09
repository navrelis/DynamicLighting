package navrelis.dynamiclighting.luminance;

import static navrelis.dynamiclighting.luminance.TestGame.compile;
import static navrelis.dynamiclighting.luminance.TestGame.entity;
import static navrelis.dynamiclighting.luminance.TestGame.fixture;
import static navrelis.dynamiclighting.luminance.TestGame.item;
import static navrelis.dynamiclighting.luminance.TestGame.onlyEntityRule;
import static navrelis.dynamiclighting.luminance.TestGame.onlyItemRule;
import static navrelis.dynamiclighting.luminance.TestGame.onlySkip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The data file format: every shape the pack's jars use, and every form the format allows.
 */
class DataFormatTest {
	/** Luminance types without parameters and what they must decode to. */
	private static final Map<String, EntityLight> PLAIN_TYPES = Map.ofEntries(
		Map.entry("item_entity", EntityLights.ITEM_ENTITY),
		Map.entry("item_frame", EntityLights.ITEM_FRAME),
		Map.entry("arrow/derived_from_self_item", EntityLights.ARROW_ITEM),
		Map.entry("projectile/throwable_item", EntityLights.THROWN_ITEM),
		Map.entry("falling_block", EntityLights.FALLING_BLOCK),
		Map.entry("minecart/display_block", EntityLights.MINECART),
		Map.entry("enderman", EntityLights.ENDERMAN),
		Map.entry("glow_squid", EntityLights.GLOW_SQUID),
		Map.entry("magma_cube", EntityLights.MAGMA_CUBE),
		Map.entry("creeper", EntityLights.CREEPER),
		Map.entry("display/block", EntityLights.BLOCK_DISPLAY),
		Map.entry("display/item", EntityLights.ITEM_DISPLAY)
	);
	private static final List<String> NAMESPACES = List.of("lambdynlights", "dynamiclighting");

	@BeforeAll
	static void boot() {
		TestGame.registries();
	}

	private static Set<Item> items(ItemRule rule) {
		Set<Item> items = new HashSet<>();

		for (Holder<Item> holder : rule.predicate().items().orElseThrow()) {
			items.add(holder.value());
		}

		return items;
	}

	private static EntityLight entityLuminance(String luminanceJson) {
		return onlyEntityRule(compile(entity("case", """
			{ "match": { "type": "minecraft:pig" }, "luminance": %s }
			""".formatted(luminanceJson)))).light();
	}

	// Shapes found in the pack's jars

	@Test
	void singleIdWithIntegerLuminance() {
		ItemRule rule = onlyItemRule(compile(fixture(DataKind.ITEM, "single_id")));

		assertEquals(Set.of(Items.GOLDEN_SWORD), items(rule));
		assertEquals(9, rule.luminance());
		assertFalse(rule.waterSensitive());
		assertTrue(rule.itemOnly());
	}

	@Test
	void modLoadedConditionIsUnderstoodAndNeoforgeConditionsAreIgnored() {
		ItemRule rule = onlyItemRule(compile(fixture(DataKind.ITEM, "conditions_met")));

		assertEquals(Set.of(Items.CANDLE), items(rule));
		assertEquals(8, rule.luminance());
		assertTrue(rule.waterSensitive());
	}

	@Test
	void fileForAMissingModIsSkippedQuietly() {
		CompiledData.Skip skip = onlySkip(compile(fixture(DataKind.ITEM, "conditions_not_met")));

		assertEquals(SkipReason.CONDITIONS_NOT_MET, skip.reason());
		assertTrue(skip.quiet());
	}

	@Test
	void legacyItemShapeIsSkippedQuietly() {
		CompiledData.Skip skip = onlySkip(compile(fixture(DataKind.ITEM, "legacy")));

		assertEquals(SkipReason.LEGACY_SHAPE, skip.reason());
		assertTrue(skip.quiet());
	}

	@Test
	void wetSensitiveEntityFile() {
		EntityRule rule = onlyEntityRule(compile(fixture(DataKind.ENTITY, "wet_sensitive")));

		assertEquals(List.of(EntityType.BLAZE), rule.types());
		assertEquals(new EntityLights.Wet(new EntityLights.Constant(10), new EntityLights.Constant(4)), rule.light());
	}

	@Test
	void theWholePackCompilesTogether() {
		CompiledData data = compile(
			fixture(DataKind.ITEM, "single_id"),
			fixture(DataKind.ITEM, "conditions_met"),
			fixture(DataKind.ITEM, "conditions_not_met"),
			fixture(DataKind.ITEM, "legacy"),
			fixture(DataKind.ENTITY, "wet_sensitive")
		);

		assertEquals(2, data.itemRules().size());
		assertEquals(1, data.entityRules().size());
		assertEquals(2, data.skips().size());
		assertEquals(1, data.skipped(SkipReason.CONDITIONS_NOT_MET));
		assertEquals(1, data.skipped(SkipReason.LEGACY_SHAPE));
		assertEquals(0, data.warnings());
	}

	// Item match forms

	@Test
	void itemIdList() {
		ItemRule rule = onlyItemRule(compile(item("list", """
			{ "match": { "items": ["minecraft:torch", "minecraft:lantern"] }, "luminance": 13, "water_sensitive": true }
			""")));

		assertEquals(Set.of(Items.TORCH, Items.LANTERN), items(rule));
		assertEquals(13, rule.luminance());
		assertTrue(rule.waterSensitive());
	}

	@Test
	void itemTag() {
		ItemRule rule = onlyItemRule(compile(item("tag", """
			{ "match": { "items": "#lighttest:glowing" }, "luminance": 6 }
			""")));

		assertEquals(Set.of(Items.GLOWSTONE_DUST, Items.BLAZE_ROD), items(rule));
		assertFalse(rule.waterSensitive());
	}

	@Test
	void itemMatchWithCountComponentsAndPredicatesIsNotItemOnly() {
		CompiledData data = compile(
			item("count", """
				{ "match": { "items": "minecraft:torch", "count": { "min": 2 } }, "luminance": 3 }
				"""),
			item("components", """
				{ "match": { "items": "minecraft:golden_sword", "components": { "minecraft:damage": 3 } }, "luminance": 3 }
				"""),
			item("predicates", """
				{ "match": { "predicates": { "minecraft:damage": { "damage": { "min": 1 } } } }, "luminance": 3 }
				""")
		);

		assertEquals(List.of(), data.skips());
		assertEquals(3, data.itemRules().size());

		for (ItemRule rule : data.itemRules()) {
			assertFalse(rule.itemOnly(), rule.file().toString());
		}
	}

	// Item luminance forms

	@Test
	void itemLuminanceForms() {
		assertEquals(0, onlyItemRule(compile(item("zero", """
			{ "match": { "items": "minecraft:glowstone" }, "luminance": 0 }
			"""))).luminance());
		assertEquals(15, onlyItemRule(compile(item("fifteen", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 15 }
			"""))).luminance());
		assertEquals(7, onlyItemRule(compile(item("value", """
			{ "match": { "items": "minecraft:stick" }, "luminance": { "type": "value", "value": 7 } }
			"""))).luminance());
		assertEquals(15, onlyItemRule(compile(item("block", """
			{ "match": { "items": "minecraft:stick" }, "luminance": { "type": "block", "block": "minecraft:sea_lantern" } }
			"""))).luminance());
		assertEquals(10, onlyItemRule(compile(item("block_soul", """
			{ "match": { "items": "minecraft:stick" }, "luminance": { "type": "block", "block": "minecraft:soul_torch" } }
			"""))).luminance());
	}

	@Test
	void blockSelfTakesTheLightOfTheItemsOwnBlock() {
		ItemRule rule = onlyItemRule(compile(item("self", """
			{ "match": { "items": ["minecraft:torch", "minecraft:soul_torch", "minecraft:stick"] }, "luminance": { "type": "block_self" } }
			""")));

		assertEquals(ItemRule.BLOCK_SELF, rule.luminance());
		assertEquals(14, rule.luminanceOf(Items.TORCH));
		assertEquals(10, rule.luminanceOf(Items.SOUL_TORCH));
		assertEquals(0, rule.luminanceOf(Items.STICK));
	}

	// Entity match forms

	@Test
	void entityTypeAsIdListAndTag() {
		assertEquals(List.of(EntityType.PIG), onlyEntityRule(compile(entity("one", """
			{ "match": { "type": "minecraft:pig" }, "luminance": 5 }
			"""))).types());
		assertEquals(List.of(EntityType.PIG, EntityType.COW), onlyEntityRule(compile(entity("list", """
			{ "match": { "type": ["minecraft:pig", "minecraft:cow"] }, "luminance": 5 }
			"""))).types());
		assertEquals(Set.of(EntityType.BLAZE, EntityType.MAGMA_CUBE), Set.copyOf(onlyEntityRule(compile(entity("tag", """
			{ "match": { "type": "#lighttest:fiery" }, "luminance": 5 }
			"""))).types()));
	}

	@Test
	void flagsAndEquipmentAreDecodedWithTheVanillaCodecs() {
		EntityRule rule = onlyEntityRule(compile(entity("conditional", """
			{
				"match": {
					"type": "minecraft:zombie",
					"flags": { "is_baby": true, "is_on_ground": false, "is_sneaking": false },
					"equipment": { "mainhand": { "items": "minecraft:torch" }, "head": { "items": "#lighttest:glowing" } }
				},
				"luminance": 9
			}
			""")));

		EntityLights.When when = assertInstanceOf(EntityLights.When.class, rule.light());
		assertNotNull(when.flags());
		assertEquals(Boolean.TRUE, when.flags().isBaby().orElseThrow());
		assertEquals(Boolean.FALSE, when.flags().isOnGround().orElseThrow());
		assertEquals(Boolean.FALSE, when.flags().isCrouching().orElseThrow());
		assertTrue(when.flags().isOnFire().isEmpty());
		assertNotNull(when.equipment());
		assertTrue(when.equipment().mainhand().orElseThrow().test(new ItemStack(Items.TORCH)));
		assertFalse(when.equipment().mainhand().orElseThrow().test(new ItemStack(Items.STICK)));
		assertTrue(when.equipment().head().orElseThrow().test(new ItemStack(Items.BLAZE_ROD)));
		assertTrue(when.equipment().offhand().isEmpty());
		assertEquals(new EntityLights.Constant(9), when.light());
	}

	@Test
	void matchWithOnlyATypeNeedsNoPredicateAtRuntime() {
		EntityRule rule = onlyEntityRule(compile(entity("plain", """
			{ "match": { "type": "minecraft:pig" }, "luminance": 9 }
			""")));

		assertEquals(new EntityLights.Constant(9), rule.light());
	}

	// Entity luminance forms

	@Test
	void entityLuminanceAsIntegerValueAndList() {
		assertEquals(new EntityLights.Constant(5), entityLuminance("5"));
		assertEquals(EntityLights.NONE, entityLuminance("0"));
		assertEquals(new EntityLights.Constant(15), entityLuminance("[15]"));
		assertEquals(
			new EntityLights.Brightest(List.of(
				new EntityLights.Constant(5),
				new EntityLights.Wet(new EntityLights.Constant(12), new EntityLights.Constant(2))
			)),
			entityLuminance("""
				[5, { "type": "lambdynlights:wet_sensitive", "dry": 12, "wet": 2 }]
				""")
		);
	}

	@Test
	void everyPlainTypeInBothNamespaces() {
		for (String namespace : NAMESPACES) {
			for (Map.Entry<String, EntityLight> type : PLAIN_TYPES.entrySet()) {
				String id = namespace + ":" + type.getKey();
				assertSame(type.getValue(), entityLuminance("{ \"type\": \"" + id + "\" }"), id);
			}
		}
	}

	@Test
	void everyParameterisedTypeInBothNamespaces() {
		for (String namespace : NAMESPACES) {
			assertEquals(new EntityLights.Constant(7), entityLuminance("""
				{ "type": "%s:value", "value": 7 }
				""".formatted(namespace)));
			assertEquals(
				new EntityLights.InWater(new EntityLights.Constant(14), EntityLights.NONE),
				entityLuminance("""
					{ "type": "%s:water_sensitive", "out_of_water": 14 }
					""".formatted(namespace))
			);
			assertEquals(
				new EntityLights.InWater(new EntityLights.Constant(14), new EntityLights.Constant(3)),
				entityLuminance("""
					{ "type": "%s:water_sensitive", "out_of_water": [14], "in_water": 3 }
					""".formatted(namespace))
			);
			assertEquals(
				new EntityLights.Wet(new EntityLights.Constant(10), new EntityLights.Constant(4)),
				entityLuminance("""
					{ "type": "%s:wet_sensitive", "dry": 10, "wet": 4 }
					""".formatted(namespace))
			);
			assertEquals(
				new EntityLights.UnlitDisplay(new EntityLights.Brightest(List.of(EntityLights.BLOCK_DISPLAY, new EntityLights.Constant(2)))),
				entityLuminance("""
					{ "type": "%1$s:display", "luminance": [{ "type": "%1$s:display/block" }, 2] }
					""".formatted(namespace))
			);

			EntityLights.Stack stack = assertInstanceOf(EntityLights.Stack.class, entityLuminance("""
				{ "type": "%s:item", "item": { "id": "minecraft:torch", "count": 2 } }
				""".formatted(namespace)));
			assertSame(Items.TORCH, stack.stack().getItem());
			assertEquals(2, stack.stack().getCount());
			assertSame(EntityLights.Water.SUBMERGED, stack.water());
		}
	}

	@Test
	void itemTypeWaterOptions() {
		assertSame(EntityLights.Water.OR_RAIN, assertInstanceOf(EntityLights.Stack.class, entityLuminance("""
			{ "type": "lambdynlights:item", "item": { "id": "minecraft:torch" }, "include_rain": true }
			""")).water());
		assertSame(EntityLights.Water.SUBMERGED, assertInstanceOf(EntityLights.Stack.class, entityLuminance("""
			{ "type": "lambdynlights:item", "item": { "id": "minecraft:torch" }, "include_rain": false }
			""")).water());
		assertSame(EntityLights.Water.NEVER, assertInstanceOf(EntityLights.Stack.class, entityLuminance("""
			{ "type": "lambdynlights:item", "item": { "id": "minecraft:torch" }, "always": "dry", "include_rain": true }
			""")).water());
		assertSame(EntityLights.Water.ALWAYS, assertInstanceOf(EntityLights.Stack.class, entityLuminance("""
			{ "type": "lambdynlights:item", "item": { "id": "minecraft:torch" }, "always": "wet" }
			""")).water());
	}

	@Test
	void typeIdsOfOtherNamespacesAreNotOurs() {
		CompiledData.Skip skip = onlySkip(compile(entity("foreign", """
			{ "match": { "type": "minecraft:pig" }, "luminance": { "type": "othermod:value", "value": 7 } }
			""")));

		assertEquals(SkipReason.BAD_LUMINANCE, skip.reason());
	}

	// Conditions

	@Test
	void fabricApiConditionsAreEvaluated() {
		assertNotNull(onlyItemRule(compile(item("met", """
			{
				"fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["fabricloader"] }],
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""))));
		assertEquals(SkipReason.CONDITIONS_NOT_MET, onlySkip(compile(item("unmet", """
			{
				"fabric:load_conditions": [{ "condition": "fabric:all_mods_loaded", "values": ["surely_not_installed"] }],
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""))).reason());
		assertEquals(SkipReason.CONDITIONS_NOT_MET, onlySkip(compile(item("not", """
			{
				"fabric:load_conditions": { "condition": "fabric:not", "value": { "condition": "fabric:true" } },
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""))).reason());
	}

	@Test
	void aSingleConditionObjectAndMixedListsWork() {
		assertNotNull(onlyItemRule(compile(item("single", """
			{
				"fabric:load_conditions": { "condition": "fabric:mod_loaded", "modid": "present_mod" },
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""))));
		assertNotNull(onlyItemRule(compile(item("mixed", """
			{
				"fabric:load_conditions": [
					{ "condition": "fabric:mod_loaded", "modid": "present_mod" },
					{ "condition": "fabric:true" }
				],
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""))));
		assertNotNull(onlyEntityRule(compile(entity("entity", """
			{
				"fabric:load_conditions": [{ "condition": "fabric:mod_loaded", "modid": "present_mod" }],
				"match": { "type": "minecraft:pig" },
				"luminance": 4
			}
			"""))));
	}

	// Leftovers of the format

	@Test
	void silenceErrorKeepsTheReasonButLowersTheLogLevel() {
		CompiledData.Skip loud = onlySkip(compile(item("loud", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 99 }
			""")));
		CompiledData.Skip silenced = onlySkip(compile(item("silenced", """
			{ "silence_error": true, "match": { "items": "minecraft:stick" }, "luminance": 99 }
			""")));

		assertEquals(SkipReason.BAD_LUMINANCE, loud.reason());
		assertFalse(loud.quiet());
		assertEquals(SkipReason.BAD_LUMINANCE, silenced.reason());
		assertTrue(silenced.quiet());
	}

	@Test
	void unknownTopLevelKeysAreIgnored() {
		ItemRule rule = onlyItemRule(compile(item("extra", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 4, "comment": "anything", "silence_error": false }
			""")));

		assertEquals(4, rule.luminance());
		assertNull(TestGame.item("x", "{}").problem());
	}
}
