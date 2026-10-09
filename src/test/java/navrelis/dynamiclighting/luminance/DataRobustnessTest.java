package navrelis.dynamiclighting.luminance;

import static navrelis.dynamiclighting.luminance.TestGame.compile;
import static navrelis.dynamiclighting.luminance.TestGame.entity;
import static navrelis.dynamiclighting.luminance.TestGame.item;
import static navrelis.dynamiclighting.luminance.TestGame.onlySkip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Broken, foreign and hostile files: each one is skipped on its own, for the right reason and at
 * the right log level, and never takes the reload or another file with it.
 */
class DataRobustnessTest {
	@BeforeAll
	static void boot() {
		TestGame.registries();
	}

	private static void assertSkipped(SkipReason reason, boolean quiet, RawFile file) {
		CompiledData.Skip skip = onlySkip(compile(file));

		assertEquals(reason, skip.reason(), skip.detail());
		assertEquals(quiet, skip.quiet(), skip.detail());
		assertEquals(file.id(), skip.file());
		assertFalse(skip.detail().isBlank());
	}

	private static void assertWarned(SkipReason reason, RawFile file) {
		assertSkipped(reason, false, file);
	}

	private static void assertQuiet(SkipReason reason, RawFile file) {
		assertSkipped(reason, true, file);
	}

	private static RawFile itemWithLuminance(String luminanceJson) {
		return item("luminance", "{ \"match\": { \"items\": \"minecraft:stick\" }, \"luminance\": " + luminanceJson + " }");
	}

	private static RawFile entityWithLuminance(String luminanceJson) {
		return entity("luminance", "{ \"match\": { \"type\": \"minecraft:pig\" }, \"luminance\": " + luminanceJson + " }");
	}

	// Reading

	@Test
	void syntaxErrors() {
		assertWarned(SkipReason.SYNTAX, item("missing_comma", """
			{ "match": { "items": "minecraft:stick" } "luminance": 4 }
			"""));
		assertWarned(SkipReason.SYNTAX, item("unclosed", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 4
			"""));
		assertWarned(SkipReason.SYNTAX, entity("trailing_garbage", """
			{ "match": { "type": "minecraft:pig" }, "luminance": 4 } }
			"""));
		assertWarned(SkipReason.SYNTAX, item("deep", "[".repeat(200_000)));
	}

	@Test
	void rootsThatAreNotObjects() {
		assertWarned(SkipReason.NOT_AN_OBJECT, item("array", "[]"));
		assertWarned(SkipReason.NOT_AN_OBJECT, item("number", "12"));
		assertWarned(SkipReason.NOT_AN_OBJECT, item("string", "\"minecraft:torch\""));
		assertWarned(SkipReason.NOT_AN_OBJECT, item("null", "null"));
		assertWarned(SkipReason.NOT_AN_OBJECT, entity("empty_file", ""));
	}

	@Test
	void aReaderThatFailsIsReportedNotThrown() {
		Reader broken = new Reader() {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				throw new IOException("disk on fire");
			}

			@Override
			public void close() {
			}
		};
		RawFile file = DataReader.parse(ResourceLocation.parse("lighttest:dynamiclights/item/broken.json"), DataKind.ITEM, broken);

		assertNull(file.json());
		assertWarned(SkipReason.UNREADABLE, file);
	}

	@Test
	void readAllSurvivesUnreadableResourcesAndReturnsAnImmutableList() {
		Map<ResourceLocation, byte[]> contents = new LinkedHashMap<>();
		contents.put(ResourceLocation.parse("a:dynamiclights/item/good.json"), """
			{ "match": { "items": "minecraft:stick" }, "luminance": 4 }
			""".getBytes(StandardCharsets.UTF_8));
		contents.put(ResourceLocation.parse("a:dynamiclights/item/sub/folder/bad.json"), "{ nope".getBytes(StandardCharsets.UTF_8));
		contents.put(ResourceLocation.parse("a:dynamiclights/item/unreadable.json"), null);
		contents.put(ResourceLocation.parse("a:dynamiclights/item/readme.txt"), "not json".getBytes(StandardCharsets.UTF_8));
		contents.put(ResourceLocation.parse("b:dynamiclights/entity/pig.json"), """
			{ "match": { "type": "minecraft:pig" }, "luminance": 4 }
			""".getBytes(StandardCharsets.UTF_8));
		contents.put(ResourceLocation.parse("b:ryoamiclights/dynamiclights/other.json"), "{}".getBytes(StandardCharsets.UTF_8));

		List<RawFile> files = DataReader.readAll(new FakeResources(contents));

		assertEquals(4, files.size());
		assertThrows(UnsupportedOperationException.class, () -> files.add(files.get(0)));

		CompiledData data = compile(files);
		assertEquals(1, data.itemRules().size());
		assertEquals(1, data.entityRules().size());
		assertEquals(1, data.skipped(SkipReason.SYNTAX));
		assertEquals(1, data.skipped(SkipReason.UNREADABLE));
		assertEquals(2, data.warnings());
	}

	@Test
	void aResourceManagerThatFailsToListGivesNoFiles() {
		ResourceManager failing = new FakeResources(Map.of()) {
			@Override
			public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
				throw new IllegalStateException("pack gone");
			}
		};

		assertEquals(List.of(), DataReader.readAll(failing));
	}

	// Match

	@Test
	void missingOrMalformedMatch() {
		assertWarned(SkipReason.NO_MATCH, item("none", """
			{ "luminance": 4 }
			"""));
		assertWarned(SkipReason.NO_MATCH, item("string", """
			{ "match": "minecraft:stick", "luminance": 4 }
			"""));
		assertWarned(SkipReason.NO_MATCH, entity("none", """
			{ "luminance": 4 }
			"""));
		// The old item shape means nothing in an entity file.
		assertWarned(SkipReason.NO_MATCH, entity("item_key", """
			{ "item": "minecraft:stick", "luminance": 4 }
			"""));
	}

	@Test
	void matchWithoutAnyConstraintIsRejected() {
		assertWarned(SkipReason.EMPTY_MATCH, item("empty", """
			{ "match": {}, "luminance": 4 }
			"""));
		assertWarned(SkipReason.EMPTY_MATCH, item("misspelled", """
			{ "match": { "item": "minecraft:torch" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.EMPTY_MATCH, item("empty_parts", """
			{ "match": { "count": {}, "components": {}, "predicates": {} }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.EMPTY_MATCH, item("empty_list", """
			{ "match": { "items": [] }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.EMPTY_MATCH, entity("empty", """
			{ "match": {}, "luminance": 4 }
			"""));
		assertWarned(SkipReason.EMPTY_MATCH, entity("empty_list", """
			{ "match": { "type": [] }, "luminance": 4 }
			"""));
	}

	@Test
	void entityMatchKeysWeDoNotEvaluateSkipTheWholeFile() {
		for (String key : List.of("location", "effects", "vehicle", "passenger", "slots", "typ", "nbt")) {
			assertWarned(SkipReason.UNSUPPORTED_MATCH, entity(key, """
				{ "match": { "type": "minecraft:pig", "%s": {} }, "luminance": 4 }
				""".formatted(key)));
			assertWarned(SkipReason.UNSUPPORTED_MATCH, entity(key + "_alone", """
				{ "match": { "%s": {} }, "luminance": 4 }
				""".formatted(key)));
		}

		// Without a type the rule would have to be tested against every entity in the world.
		assertWarned(SkipReason.UNSUPPORTED_MATCH, entity("no_type", """
			{ "match": { "flags": { "is_on_fire": true } }, "luminance": 4 }
			"""));
	}

	@Test
	void malformedPredicates() {
		assertWarned(SkipReason.INVALID, item("items_number", """
			{ "match": { "items": 5 }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.INVALID, item("items_bad_id", """
			{ "match": { "items": "Not An Id" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.INVALID, item("tag_in_list", """
			{ "match": { "items": ["minecraft:torch", 3] }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.INVALID, item("count", """
			{ "match": { "items": "minecraft:torch", "count": "many" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.INVALID, item("water", """
			{ "match": { "items": "minecraft:torch" }, "luminance": 4, "water_sensitive": "yes" }
			"""));
		assertWarned(SkipReason.INVALID, entity("flags", """
			{ "match": { "type": "minecraft:pig", "flags": { "is_baby": "maybe" } }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.INVALID, entity("equipment", """
			{ "match": { "type": "minecraft:pig", "equipment": { "head": 3 } }, "luminance": 4 }
			"""));
	}

	// Things that are simply not installed

	@Test
	void unknownIdsAndTagsAreQuiet() {
		assertQuiet(SkipReason.UNKNOWN_ID, item("item", """
			{ "match": { "items": "absentmod:lamp" }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, item("item_in_list", """
			{ "match": { "items": ["minecraft:torch", "absentmod:lamp"] }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, item("tag", """
			{ "match": { "items": "#absentmod:lamps" }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, item("empty_tag", """
			{ "match": { "items": "#lighttest:nothing" }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, item("block", """
			{ "match": { "items": "minecraft:stick" }, "luminance": { "type": "block", "block": "absentmod:lamp" } }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, entity("entity", """
			{ "match": { "type": "absentmod:wisp" }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, entity("entity_in_list", """
			{ "match": { "type": ["minecraft:pig", "absentmod:wisp"] }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, entity("entity_tag", """
			{ "match": { "type": "#absentmod:wisps" }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, entity("equipment_item", """
			{ "match": { "type": "minecraft:pig", "equipment": { "head": { "items": "absentmod:helmet" } } }, "luminance": 4 }
			"""));
		assertQuiet(SkipReason.UNKNOWN_ID, entityWithLuminance("""
			{ "type": "lambdynlights:item", "item": { "id": "absentmod:lamp" } }
			"""));
	}

	// Luminance

	@Test
	void luminanceOutOfRangeOrOfTheWrongKind() {
		for (String bad : List.of("16", "-1", "7.5", "\"8\"", "true", "null", "1e9")) {
			assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance(bad));
			assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance(bad));
			assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"type\": \"value\", \"value\": " + bad + " }"));
			assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:value\", \"value\": " + bad + " }"));
			assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("[3, " + bad + "]"));
			assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:wet_sensitive\", \"dry\": " + bad + " }"));
		}

		assertWarned(SkipReason.BAD_LUMINANCE, item("missing", """
			{ "match": { "items": "minecraft:stick" } }
			"""));
		assertWarned(SkipReason.BAD_LUMINANCE, entity("missing", """
			{ "match": { "type": "minecraft:pig" } }
			"""));
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("[4]"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("[]"));
	}

	@Test
	void unknownLuminanceTypes() {
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"type\": \"sparkle\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"type\": 3 }"));
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"value\": 3 }"));
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"type\": \"block\" }"));
		// An entity type name is not an item type name.
		assertWarned(SkipReason.BAD_LUMINANCE, itemWithLuminance("{ \"type\": \"lambdynlights:creeper\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:sparkle\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:block_self\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:display\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance("{ \"type\": \"lambdynlights:item\" }"));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance(
			"{ \"type\": \"lambdynlights:item\", \"item\": { \"id\": \"minecraft:torch\" }, \"always\": \"damp\" }"
		));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance(
			"{ \"type\": \"lambdynlights:item\", \"item\": { \"count\": 2 } }"
		));
		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance(
			"{ \"type\": \"lambdynlights:item\", \"item\": { \"id\": \"minecraft:air\" } }"
		));
	}

	@Test
	void absurdlyNestedLuminanceIsRejectedNotOverflowed() {
		String nested = "4";

		for (int i = 0; i < 40; i++) {
			nested = "{ \"type\": \"lambdynlights:display\", \"luminance\": " + nested + " }";
		}

		assertWarned(SkipReason.BAD_LUMINANCE, entityWithLuminance(nested));
	}

	// Conditions

	@Test
	void conditionsThatCannotBeDecodedSkipTheFile() {
		assertWarned(SkipReason.BAD_CONDITIONS, item("unknown_type", """
			{
				"fabric:load_conditions": [{ "condition": "somemod:phase_of_the_moon", "phase": "full" }],
				"match": { "items": "minecraft:stick" },
				"luminance": 4
			}
			"""));
		assertWarned(SkipReason.BAD_CONDITIONS, item("no_type", """
			{ "fabric:load_conditions": [{ "modid": "present_mod" }], "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.BAD_CONDITIONS, item("not_objects", """
			{ "fabric:load_conditions": ["present_mod"], "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.BAD_CONDITIONS, item("string", """
			{ "fabric:load_conditions": "present_mod", "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.BAD_CONDITIONS, item("no_modid", """
			{ "fabric:load_conditions": [{ "condition": "fabric:mod_loaded" }], "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		assertWarned(SkipReason.BAD_CONDITIONS, entity("unknown_type", """
			{
				"fabric:load_conditions": { "condition": "somemod:phase_of_the_moon" },
				"match": { "type": "minecraft:pig" },
				"luminance": 4
			}
			"""));
	}

	// Isolation

	@Test
	void oddSilenceErrorValuesDoNotBreakAnything() {
		assertNotNull(TestGame.onlyItemRule(compile(item("object", """
			{ "silence_error": { "really": true }, "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""))));
		assertWarned(SkipReason.BAD_LUMINANCE, item("string", """
			{ "silence_error": "true", "match": { "items": "minecraft:stick" }, "luminance": 99 }
			"""));
	}

	@Test
	void everyBadFileIsSkippedOnItsOwn() {
		List<RawFile> files = new ArrayList<>();
		files.add(item("good_first", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		files.add(item("syntax", "{ \"match\": "));
		files.add(item("root", "[]"));
		files.add(item("empty_match", """
			{ "match": {}, "luminance": 4 }
			"""));
		files.add(item("misspelled", """
			{ "match": { "item": "minecraft:torch" }, "luminance": 4 }
			"""));
		files.add(item("unknown_id", """
			{ "match": { "items": "absentmod:lamp" }, "luminance": 4 }
			"""));
		files.add(item("unknown_type", """
			{ "match": { "items": "minecraft:stick" }, "luminance": { "type": "sparkle" } }
			"""));
		files.add(item("range", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 16 }
			"""));
		files.add(item("conditions", """
			{ "fabric:load_conditions": [{ "condition": "somemod:x" }], "match": { "items": "minecraft:stick" }, "luminance": 4 }
			"""));
		files.add(entity("unsupported", """
			{ "match": { "type": "minecraft:pig", "location": {} }, "luminance": 4 }
			"""));
		files.add(RawFile.failed(ResourceLocation.parse("lighttest:dynamiclights/item/unreadable.json"), DataKind.ITEM, SkipReason.UNREADABLE, "gone"));
		files.add(entity("good_last", """
			{ "match": { "type": "minecraft:pig" }, "luminance": 4 }
			"""));

		CompiledData data = compile(files);

		assertEquals(1, data.itemRules().size());
		assertEquals(1, data.entityRules().size());
		assertEquals(10, data.skips().size());
		assertEquals(1, data.skipped(SkipReason.SYNTAX));
		assertEquals(1, data.skipped(SkipReason.NOT_AN_OBJECT));
		assertEquals(2, data.skipped(SkipReason.EMPTY_MATCH));
		assertEquals(1, data.skipped(SkipReason.UNKNOWN_ID));
		assertEquals(2, data.skipped(SkipReason.BAD_LUMINANCE));
		assertEquals(1, data.skipped(SkipReason.BAD_CONDITIONS));
		assertEquals(1, data.skipped(SkipReason.UNSUPPORTED_MATCH));
		assertEquals(1, data.skipped(SkipReason.UNREADABLE));
		assertEquals(9, data.warnings());
		assertEquals(
			Set.copyOf(files.subList(1, 11).stream().map(RawFile::id).toList()),
			Set.copyOf(data.skips().stream().map(CompiledData.Skip::file).toList())
		);
		assertTrue(data.skips().stream().allMatch(skip -> skip.quiet() == skip.reason().quiet()));
	}

	/** Just enough of a resource manager for {@link DataReader#readAll}. A {@code null} content fails on open. */
	private static class FakeResources implements ResourceManager {
		private final Map<ResourceLocation, byte[]> contents;

		FakeResources(Map<ResourceLocation, byte[]> contents) {
			this.contents = contents;
		}

		@Override
		public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
			Map<ResourceLocation, Resource> found = new LinkedHashMap<>();

			for (Map.Entry<ResourceLocation, byte[]> entry : this.contents.entrySet()) {
				if (entry.getKey().getPath().startsWith(path + "/") && filter.test(entry.getKey())) {
					byte[] bytes = entry.getValue();
					found.put(entry.getKey(), new Resource(null, () -> {
						if (bytes == null) {
							throw new IOException("cannot open");
						}

						return new ByteArrayInputStream(bytes);
					}));
				}
			}

			return found;
		}

		@Override
		public Optional<Resource> getResource(ResourceLocation id) {
			return Optional.empty();
		}

		@Override
		public Set<String> getNamespaces() {
			return Set.of();
		}

		@Override
		public List<Resource> getResourceStack(ResourceLocation id) {
			return List.of();
		}

		@Override
		public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
			return Map.of();
		}

		@Override
		public Stream<PackResources> listPacks() {
			return Stream.empty();
		}
	}
}
