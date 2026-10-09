package navrelis.dynamiclighting.luminance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import navrelis.dynamiclighting.DynamicLighting;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Shared set-up for tests that need the game's registries: boots Minecraft once under
 * {@code fabric-loader-junit}, binds a few tags (a plain bootstrap has none) and offers short
 * ways to parse and compile data files.
 */
final class TestGame {
	/** Contains glowstone dust and blaze rods. */
	static final TagKey<Item> GLOWING_ITEMS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("lighttest", "glowing"));
	/** Exists, but has no entries. */
	static final TagKey<Item> NO_ITEMS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("lighttest", "nothing"));
	/** Contains blazes and magma cubes. */
	static final TagKey<EntityType<?>> FIERY = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("lighttest", "fiery"));

	/** The only mod {@code fabric:mod_loaded} sees as installed in tests. */
	static final Predicate<String> MODS = "present_mod"::equals;

	private static HolderLookup.Provider registries;

	private TestGame() {
	}

	static synchronized HolderLookup.Provider registries() {
		if (registries == null) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();

			// Fabric API registers its condition types in a mod initialiser, which a unit test does not run.
			if (ResourceConditions.getConditionType(ResourceLocation.fromNamespaceAndPath("fabric", "all_mods_loaded")) == null) {
				new ResourceConditionsImpl().onInitialize();
			}

			BuiltInRegistries.ITEM.bindTags(Map.of(
				GLOWING_ITEMS, List.of(Items.GLOWSTONE_DUST.builtInRegistryHolder(), Items.BLAZE_ROD.builtInRegistryHolder()),
				NO_ITEMS, List.of()
			));
			BuiltInRegistries.ENTITY_TYPE.bindTags(Map.of(
				FIERY, List.of(EntityType.BLAZE.builtInRegistryHolder(), EntityType.MAGMA_CUBE.builtInRegistryHolder())
			));
			registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		}

		return registries;
	}

	static RawFile item(String name, String json) {
		return DataReader.parse(id(DataKind.ITEM, name), DataKind.ITEM, json);
	}

	static RawFile entity(String name, String json) {
		return DataReader.parse(id(DataKind.ENTITY, name), DataKind.ENTITY, json);
	}

	/** Reads {@code src/test/resources/lightdata/<kind>/<name>.json}. */
	static RawFile fixture(DataKind kind, String name) {
		String path = "/lightdata/" + kind.folder() + "/" + name + ".json";

		try (InputStream stream = TestGame.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IllegalStateException("Missing fixture " + path);
			}

			return DataReader.parse(id(kind, name), kind, new InputStreamReader(stream, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static CompiledData compile(RawFile... files) {
		return compile(List.of(files));
	}

	static CompiledData compile(List<RawFile> files) {
		return DataCompiler.compile(files, registries(), MODS);
	}

	static ItemRule onlyItemRule(CompiledData data) {
		assertEquals(List.of(), data.skips());
		assertEquals(1, data.itemRules().size());
		return data.itemRules().get(0);
	}

	static EntityRule onlyEntityRule(CompiledData data) {
		assertEquals(List.of(), data.skips());
		assertEquals(1, data.entityRules().size());
		return data.entityRules().get(0);
	}

	/** Asserts that nothing was loaded and returns the single skip. */
	static CompiledData.Skip onlySkip(CompiledData data) {
		assertEquals(List.of(), data.itemRules());
		assertEquals(List.of(), data.entityRules());
		assertEquals(1, data.skips().size());
		return data.skips().get(0);
	}

	/** Every data file the mod itself ships, read from the built resources. */
	static List<RawFile> builtInFiles() throws IOException {
		Path root = FabricLoader.getInstance()
			.getModContainer(DynamicLighting.MOD_ID).orElseThrow()
			.findPath("assets/" + DynamicLighting.MOD_ID + "/" + DataReader.ROOT).orElseThrow();
		List<RawFile> files = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".json")).sorted().toList()) {
				String relative = root.relativize(path).toString().replace('\\', '/');
				DataKind kind = relative.startsWith(DataKind.ITEM.folder() + "/") ? DataKind.ITEM : DataKind.ENTITY;
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DynamicLighting.MOD_ID, DataReader.ROOT + "/" + relative);

				try (Reader reader = Files.newBufferedReader(path)) {
					files.add(DataReader.parse(id, kind, reader));
				}
			}
		}

		return files;
	}

	private static ResourceLocation id(DataKind kind, String name) {
		return ResourceLocation.fromNamespaceAndPath("lighttest", DataReader.ROOT + "/" + kind.folder() + "/" + name + ".json");
	}
}
