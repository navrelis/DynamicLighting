package navrelis.dynamiclighting.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

/**
 * Turns the config file's JSON into {@link Options} and back. Pure functions, no file or game access.
 * <p>
 * Reading is forgiving per key: a missing key, a value of the wrong JSON type, an unknown enum name
 * or an invalid id gives the default for that key only, plus one warning naming the key.
 */
public final class OptionsCodec {
	public static final String KEY_MODE = "mode";
	public static final String KEY_RANGE = "range";
	public static final String KEY_ENTITY_LIGHTS = "entity_lights";
	public static final String KEY_SELF_LIGHT = "self_light";
	public static final String KEY_WATER_SENSITIVE = "water_sensitive";
	public static final String KEY_GLOWING_ENTITIES = "glowing_entities";
	public static final String KEY_CREEPER = "creeper";
	public static final String KEY_TNT = "tnt";
	public static final String KEY_DISABLED_ENTITY_TYPES = "disabled_entity_types";

	/**
	 * The outcome of reading a config object.
	 *
	 * @param options  the validated options
	 * @param warnings one human-readable line per key that was not usable, each starting with the key name
	 */
	public record Result(Options options, List<String> warnings) {
		public Result {
			warnings = List.copyOf(warnings);
		}
	}

	private OptionsCodec() {
	}

	/**
	 * Parses config file text.
	 *
	 * @return empty if the text is not valid JSON or not a JSON object
	 */
	public static Optional<Result> parse(String text) {
		JsonElement root;
		try {
			root = JsonParser.parseString(text);
		} catch (JsonParseException e) {
			return Optional.empty();
		}
		if (!root.isJsonObject()) {
			return Optional.empty();
		}
		return Optional.of(parse(root.getAsJsonObject()));
	}

	/**
	 * Reads every key of a config object, falling back per key. Never throws.
	 */
	public static Result parse(JsonObject root) {
		Options defaults = Options.DEFAULT;
		List<String> warnings = new ArrayList<>();

		Mode mode = readEnum(root, KEY_MODE, Mode.class, defaults.mode(), warnings);
		LightRange range = readEnum(root, KEY_RANGE, LightRange.class, defaults.range(), warnings);
		boolean entityLights = readBoolean(root, KEY_ENTITY_LIGHTS, defaults.entityLights(), warnings);
		boolean selfLight = readBoolean(root, KEY_SELF_LIGHT, defaults.selfLight(), warnings);
		boolean waterSensitive = readBoolean(root, KEY_WATER_SENSITIVE, defaults.waterSensitive(), warnings);
		boolean glowingEntities = readBoolean(root, KEY_GLOWING_ENTITIES, defaults.glowingEntities(), warnings);
		FuseMode creeper = readEnum(root, KEY_CREEPER, FuseMode.class, defaults.creeper(), warnings);
		FuseMode tnt = readEnum(root, KEY_TNT, FuseMode.class, defaults.tnt(), warnings);
		Set<ResourceLocation> disabled = readIds(root, KEY_DISABLED_ENTITY_TYPES, defaults.disabledEntityTypes(), warnings);

		return new Result(
			new Options(mode, range, entityLights, selfLight, waterSensitive, glowingEntities, creeper, tnt, disabled),
			warnings
		);
	}

	/**
	 * Builds the config object for the given options. Enum values are lower-case, ids are sorted.
	 */
	public static JsonObject toJson(Options options) {
		JsonObject root = new JsonObject();
		root.addProperty(KEY_MODE, name(options.mode()));
		root.addProperty(KEY_RANGE, name(options.range()));
		root.addProperty(KEY_ENTITY_LIGHTS, options.entityLights());
		root.addProperty(KEY_SELF_LIGHT, options.selfLight());
		root.addProperty(KEY_WATER_SENSITIVE, options.waterSensitive());
		root.addProperty(KEY_GLOWING_ENTITIES, options.glowingEntities());
		root.addProperty(KEY_CREEPER, name(options.creeper()));
		root.addProperty(KEY_TNT, name(options.tnt()));

		JsonArray ids = new JsonArray();
		options.disabledEntityTypes().stream()
			.map(ResourceLocation::toString)
			.sorted(Comparator.naturalOrder())
			.forEach(ids::add);
		root.add(KEY_DISABLED_ENTITY_TYPES, ids);
		return root;
	}

	/**
	 * The config file text for the given options: pretty-printed, ends with a newline.
	 */
	public static String toText(Options options) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(toJson(options)) + "\n";
	}

	private static String name(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback, List<String> warnings) {
		JsonElement element = root.get(key);
		if (element == null) {
			warnings.add(missing(key, fallback));
			return fallback;
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
			return element.getAsBoolean();
		}
		warnings.add(key + ": expected true or false, found " + describe(element) + "; using " + fallback);
		return fallback;
	}

	private static <E extends Enum<E>> E readEnum(
		JsonObject root, String key, Class<E> type, E fallback, List<String> warnings
	) {
		JsonElement element = root.get(key);
		if (element == null) {
			warnings.add(missing(key, name(fallback)));
			return fallback;
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			String text = element.getAsString();
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equalsIgnoreCase(text)) {
					return constant;
				}
			}
			warnings.add(key + ": unknown value \"" + text + "\", expected one of " + allowed(type) + "; using " + name(fallback));
			return fallback;
		}
		warnings.add(key + ": expected a string (" + allowed(type) + "), found " + describe(element) + "; using " + name(fallback));
		return fallback;
	}

	private static Set<ResourceLocation> readIds(
		JsonObject root, String key, Set<ResourceLocation> fallback, List<String> warnings
	) {
		JsonElement element = root.get(key);
		if (element == null) {
			warnings.add(missing(key, "none"));
			return fallback;
		}
		if (!element.isJsonArray()) {
			warnings.add(key + ": expected an array of ids, found " + describe(element) + "; using none");
			return fallback;
		}
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		List<String> rejected = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			ResourceLocation id = null;
			if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				id = ResourceLocation.tryParse(entry.getAsString());
			}
			if (id == null) {
				rejected.add(entry.isJsonPrimitive() ? entry.getAsJsonPrimitive().toString() : describe(entry));
			} else {
				ids.add(id);
			}
		}
		if (!rejected.isEmpty()) {
			warnings.add(key + ": ignored " + rejected.size() + " invalid id(s): " + String.join(", ", rejected));
		}
		return ids;
	}

	private static String missing(String key, Object fallback) {
		return key + ": missing; using " + fallback;
	}

	private static String allowed(Class<? extends Enum<?>> type) {
		return Arrays.stream(type.getEnumConstants()).map(OptionsCodec::name).collect(Collectors.joining(", "));
	}

	private static String describe(JsonElement element) {
		if (element.isJsonNull()) {
			return "null";
		}
		if (element.isJsonArray()) {
			return "an array";
		}
		if (element.isJsonObject()) {
			return "an object";
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isNumber()) {
			return "the number " + primitive.getAsString();
		}
		if (primitive.isBoolean()) {
			return "a boolean";
		}
		return "the string \"" + primitive.getAsString() + "\"";
	}
}
