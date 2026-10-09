package navrelis.dynamiclighting.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OptionsCodecTest {
	private static final Options CUSTOM = new Options(
		Mode.FASTEST, LightRange.LONG, false, false, false, false, FuseMode.FANCY, FuseMode.OFF,
		Set.of(ResourceLocation.parse("minecraft:bat"), ResourceLocation.parse("friendsandfoes:wildfire"))
	);

	private static OptionsCodec.Result parse(String json) {
		return OptionsCodec.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private static OptionsCodec.Result parseWith(Options base, String key, String rawValue) {
		JsonObject object = OptionsCodec.toJson(base);
		object.remove(key);
		object.add(key, JsonParser.parseString(rawValue));
		return OptionsCodec.parse(object);
	}

	private static boolean mentions(List<String> warnings, String key) {
		return warnings.stream().anyMatch(line -> line.startsWith(key + ":"));
	}

	@Test
	void emptyObjectGivesDefaultsAndOneWarningPerKey() {
		OptionsCodec.Result result = parse("{}");
		assertEquals(Options.DEFAULT, result.options());
		assertEquals(9, result.warnings().size());
		for (String key : ALL_KEYS) {
			assertTrue(mentions(result.warnings(), key), key);
		}
	}

	@Test
	void defaultsRoundTripWithoutWarnings() {
		OptionsCodec.Result result = OptionsCodec.parse(OptionsCodec.toJson(Options.DEFAULT));
		assertEquals(Options.DEFAULT, result.options());
		assertEquals(List.of(), result.warnings());
	}

	@Test
	void everyKeyRoundTrips() {
		OptionsCodec.Result result = OptionsCodec.parse(OptionsCodec.toJson(CUSTOM));
		assertEquals(CUSTOM, result.options());
		assertEquals(List.of(), result.warnings());
	}

	@Test
	void textRoundTripsThroughTheFileFormat() {
		Optional<OptionsCodec.Result> result = OptionsCodec.parse(OptionsCodec.toText(CUSTOM));
		assertTrue(result.isPresent());
		assertEquals(CUSTOM, result.get().options());
		assertEquals(List.of(), result.get().warnings());
	}

	@Test
	void serialisedEnumsAreLowerCaseAndIdsAreSorted() {
		JsonObject json = OptionsCodec.toJson(CUSTOM);
		assertEquals("fastest", json.get("mode").getAsString());
		assertEquals("long", json.get("range").getAsString());
		assertEquals("fancy", json.get("creeper").getAsString());
		assertEquals("off", json.get("tnt").getAsString());
		JsonArray ids = json.getAsJsonArray("disabled_entity_types");
		assertEquals("friendsandfoes:wildfire", ids.get(0).getAsString());
		assertEquals("minecraft:bat", ids.get(1).getAsString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"FANCY", "Fancy", "fAnCy", "fancy"})
	void enumNamesAreCaseInsensitive(String spelling) {
		OptionsCodec.Result result = parseWith(CUSTOM, "mode", "\"" + spelling + "\"");
		assertEquals(Mode.FANCY, result.options().mode());
		assertEquals(List.of(), result.warnings());
	}

	static List<Arguments> wrongTypes() {
		return List.of(
			Arguments.of("mode", "3"),
			Arguments.of("mode", "true"),
			Arguments.of("mode", "null"),
			Arguments.of("mode", "[]"),
			Arguments.of("range", "{}"),
			Arguments.of("entity_lights", "\"true\""),
			Arguments.of("self_light", "1"),
			Arguments.of("water_sensitive", "null"),
			Arguments.of("glowing_entities", "[true]"),
			Arguments.of("creeper", "2"),
			Arguments.of("tnt", "false"),
			Arguments.of("disabled_entity_types", "\"minecraft:bat\""),
			Arguments.of("disabled_entity_types", "{}"),
			Arguments.of("disabled_entity_types", "5")
		);
	}

	@ParameterizedTest
	@MethodSource("wrongTypes")
	void wrongJsonTypeFallsBackForThatKeyOnly(String key, String badValue) {
		OptionsCodec.Result result = parseWith(CUSTOM, key, badValue);
		Options defaults = Options.DEFAULT;
		Options expected = switch (key) {
			case "mode" -> CUSTOM.withMode(defaults.mode());
			case "range" -> CUSTOM.withRange(defaults.range());
			case "entity_lights" -> CUSTOM.withEntityLights(defaults.entityLights());
			case "self_light" -> CUSTOM.withSelfLight(defaults.selfLight());
			case "water_sensitive" -> CUSTOM.withWaterSensitive(defaults.waterSensitive());
			case "glowing_entities" -> CUSTOM.withGlowingEntities(defaults.glowingEntities());
			case "creeper" -> CUSTOM.withCreeper(defaults.creeper());
			case "tnt" -> CUSTOM.withTnt(defaults.tnt());
			case "disabled_entity_types" -> new Options(
				CUSTOM.mode(), CUSTOM.range(), CUSTOM.entityLights(), CUSTOM.selfLight(), CUSTOM.waterSensitive(),
				CUSTOM.glowingEntities(), CUSTOM.creeper(), CUSTOM.tnt(), Set.of()
			);
			default -> throw new AssertionError(key);
		};
		assertEquals(expected, result.options());
		assertEquals(1, result.warnings().size());
		assertTrue(mentions(result.warnings(), key), result.warnings().toString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"mode", "range", "creeper", "tnt"})
	void unknownEnumNameFallsBackForThatKeyOnly(String key) {
		OptionsCodec.Result result = parseWith(CUSTOM, key, "\"turbo\"");
		assertEquals(1, result.warnings().size());
		assertTrue(mentions(result.warnings(), key), result.warnings().toString());
		assertTrue(result.warnings().get(0).contains("turbo"));
		Options expected = switch (key) {
			case "mode" -> CUSTOM.withMode(Options.DEFAULT.mode());
			case "range" -> CUSTOM.withRange(Options.DEFAULT.range());
			case "creeper" -> CUSTOM.withCreeper(Options.DEFAULT.creeper());
			default -> CUSTOM.withTnt(Options.DEFAULT.tnt());
		};
		assertEquals(expected, result.options());
	}

	@ParameterizedTest
	@ValueSource(strings = {"mode", "range", "entity_lights", "self_light", "water_sensitive", "glowing_entities", "creeper", "tnt", "disabled_entity_types"})
	void missingKeyFallsBackForThatKeyOnly(String key) {
		JsonObject object = OptionsCodec.toJson(CUSTOM);
		object.remove(key);
		OptionsCodec.Result result = OptionsCodec.parse(object);
		assertEquals(1, result.warnings().size());
		assertTrue(mentions(result.warnings(), key), result.warnings().toString());
		assertFalse(result.options().equals(CUSTOM));
	}

	@Test
	void unknownKeysAreIgnoredWithoutWarning() {
		JsonObject object = OptionsCodec.toJson(CUSTOM);
		object.addProperty("future_option", "whatever");
		object.add("another", new JsonArray());
		OptionsCodec.Result result = OptionsCodec.parse(object);
		assertEquals(CUSTOM, result.options());
		assertEquals(List.of(), result.warnings());
	}

	@Test
	void disabledEntityTypesSurviveARoundTrip() {
		Set<ResourceLocation> ids = Set.of(
			ResourceLocation.parse("minecraft:zombie"), ResourceLocation.parse("modid:some_thing/deep")
		);
		Options options = Options.DEFAULT.withMode(Mode.FAST);
		options = new Options(
			options.mode(), options.range(), options.entityLights(), options.selfLight(), options.waterSensitive(),
			options.glowingEntities(), options.creeper(), options.tnt(), ids
		);
		Options back = OptionsCodec.parse(OptionsCodec.toJson(options)).options();
		assertEquals(ids, back.disabledEntityTypes());
		assertEquals(options, back);
	}

	@Test
	void invalidIdsAreDroppedAndValidOnesKeptWithOneWarning() {
		OptionsCodec.Result result = parseWith(
			Options.DEFAULT, "disabled_entity_types", "[\"minecraft:bat\", \"Not Valid!\", 7, \"minecraft:cow\", null]"
		);
		assertEquals(
			Set.of(ResourceLocation.parse("minecraft:bat"), ResourceLocation.parse("minecraft:cow")),
			result.options().disabledEntityTypes()
		);
		assertEquals(1, result.warnings().size());
		assertTrue(mentions(result.warnings(), "disabled_entity_types"));
	}

	@Test
	void anOnlyInvalidIdListGivesTheDefaultList() {
		OptionsCodec.Result result = parseWith(CUSTOM, "disabled_entity_types", "[\"UPPER:case\", \"a b\"]");
		assertEquals(Set.of(), result.options().disabledEntityTypes());
		assertEquals(1, result.warnings().size());
	}

	@Test
	void textThatIsNotAJsonObjectIsRejected() {
		assertTrue(OptionsCodec.parse("").isEmpty());
		assertTrue(OptionsCodec.parse("   ").isEmpty());
		assertTrue(OptionsCodec.parse("[1, 2]").isEmpty());
		assertTrue(OptionsCodec.parse("\"fancy\"").isEmpty());
		assertTrue(OptionsCodec.parse("42").isEmpty());
		assertTrue(OptionsCodec.parse("null").isEmpty());
		assertTrue(OptionsCodec.parse("{\"mode\": ").isEmpty());
		assertTrue(OptionsCodec.parse("{\"mode\": \"fancy\"} trailing").isEmpty());
		assertTrue(OptionsCodec.parse("not json at all").isEmpty());
	}

	@Test
	void emptyJsonObjectTextIsAccepted() {
		Optional<OptionsCodec.Result> result = OptionsCodec.parse("{}");
		assertTrue(result.isPresent());
		assertEquals(Options.DEFAULT, result.get().options());
	}

	private static final List<String> ALL_KEYS = List.of(
		"mode", "range", "entity_lights", "self_light", "water_sensitive", "glowing_entities", "creeper", "tnt",
		"disabled_entity_types"
	);
}
