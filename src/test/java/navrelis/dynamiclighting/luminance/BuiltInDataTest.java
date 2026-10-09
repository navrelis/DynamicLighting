package navrelis.dynamiclighting.luminance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import navrelis.dynamiclighting.DynamicLighting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The data files shipped in {@code assets/dynamiclighting/dynamiclights/}, read from the built
 * resources exactly as the game would find them.
 */
class BuiltInDataTest {
	private static final ResourceLocation WILDFIRE =
		ResourceLocation.fromNamespaceAndPath(DynamicLighting.MOD_ID, "dynamiclights/entity/compat/friendsandfoes_wildfire.json");

	private static List<RawFile> files;
	private static CompiledData data;
	private static ItemTable table;

	@BeforeAll
	static void load() throws IOException {
		TestGame.registries();
		files = TestGame.builtInFiles();
		data = TestGame.compile(files);
		table = ItemTable.build(data.itemRules());
	}

	private static void assertLight(int dry, int wet, Item item) {
		ItemStack stack = new ItemStack(item);
		assertEquals(dry, table.luminance(stack, false), "dry " + item);
		assertEquals(wet, table.luminance(stack, true), "wet " + item);
	}

	@Test
	void allFilesAreFoundAndParse() {
		assertEquals(17, files.size());

		for (RawFile file : files) {
			assertNull(file.problem(), file.id() + ": " + file.detail());
			assertTrue(file.json().isJsonObject(), file.id().toString());
		}

		assertEquals(16, files.stream().filter(file -> file.kind() == DataKind.ITEM).count());
		assertEquals(1, files.stream().filter(file -> file.kind() == DataKind.ENTITY).count());
	}

	@Test
	void everyFileLoadsExceptTheCompatFileForAnAbsentMod() {
		assertEquals(16, data.itemRules().size());
		assertEquals(0, data.entityRules().size());
		assertEquals(0, data.warnings());
		assertEquals(1, data.skips().size());

		CompiledData.Skip skip = data.skips().get(0);
		assertEquals(WILDFIRE, skip.file());
		assertEquals(SkipReason.UNKNOWN_ID, skip.reason());
		assertTrue(skip.quiet());
	}

	@Test
	void everyItemRuleCanBeFoldedIntoTheTable() {
		for (ItemRule rule : data.itemRules()) {
			assertTrue(rule.itemOnly(), rule.file().toString());
		}
	}

	@Test
	void fireGoesOutUnderWater() {
		assertLight(14, 0, Items.TORCH);
		assertLight(10, 0, Items.SOUL_TORCH);
		assertLight(15, 0, Items.CAMPFIRE);
		assertLight(10, 0, Items.SOUL_CAMPFIRE);
		assertLight(10, 0, Items.FIRE_CHARGE);
	}

	@Test
	void everythingElseKeepsItsLightUnderWater() {
		assertLight(15, 15, Items.LAVA_BUCKET);
		assertLight(10, 10, Items.BLAZE_ROD);
		assertLight(8, 8, Items.BLAZE_POWDER);
		assertLight(8, 8, Items.GLOWSTONE_DUST);
		assertLight(10, 10, Items.GLOW_BERRIES);
		assertLight(8, 8, Items.GLOW_INK_SAC);
		assertLight(7, 7, Items.GLOW_LICHEN);
		assertLight(12, 12, Items.NETHER_STAR);
		assertLight(6, 6, Items.PRISMARINE_CRYSTALS);
		assertLight(6, 6, Items.MAGMA_CREAM);
		assertLight(8, 8, Items.SPECTRAL_ARROW);
	}

	@Test
	void blockItemsWithoutAFileStillWork() {
		assertLight(7, 7, Items.REDSTONE_TORCH);
		assertLight(15, 15, Items.LANTERN);
		assertLight(15, 15, Items.GLOWSTONE);
		assertLight(0, 0, Items.STICK);
		assertFalse(table.entry(new ItemStack(Items.LANTERN)).waterDependent());
	}

	@Test
	void theCompatFileLoadsOnceItsEntityExists() {
		// Same content, with a type that exists in a plain game.
		RawFile wildfire = files.stream().filter(file -> file.id().equals(WILDFIRE)).findFirst().orElseThrow();
		String json = wildfire.json().toString().replace("friendsandfoes:wildfire", "minecraft:blaze");
		EntityRule rule = TestGame.onlyEntityRule(TestGame.compile(TestGame.entity("wildfire", json)));

		assertEquals(new EntityLights.Wet(new EntityLights.Constant(10), new EntityLights.Constant(4)), rule.light());
	}
}
