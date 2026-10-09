package navrelis.dynamiclighting.luminance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import navrelis.dynamiclighting.luminance.CompiledData.Skip;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.advancements.critereon.EntityEquipmentPredicate;
import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the JSON trees of the data files into rules. Needs registries with tags, so it runs on
 * the client thread once a world is being joined, not during the resource reload.
 * <p>
 * Every file is decoded on its own. Whatever is wrong with one file only removes that file, and
 * the reason is recorded so that the caller can decide what to log.
 */
final class DataCompiler {
	/** Not a Fabric API condition type; other mods ship files with it, so it is evaluated here. */
	private static final String MOD_LOADED = "fabric:mod_loaded";
	/** The entity predicate keys this mod evaluates. A file with any other key is not applied at all. */
	private static final Set<String> ENTITY_MATCH_KEYS = Set.of("type", "flags", "equipment");
	private static final String[] EQUIPMENT_KEYS = {"head", "chest", "legs", "feet", "body", "mainhand", "offhand"};
	private static final Set<String> TYPE_NAMESPACES = Set.of("lambdynlights", "dynamiclighting");
	private static final int MAX_NESTING = 8;

	private final HolderLookup.Provider registries;
	private final RegistryOps<JsonElement> ops;
	private final Predicate<String> modLoaded;

	private DataCompiler(HolderLookup.Provider registries, Predicate<String> modLoaded) {
		this.registries = registries;
		this.ops = registries.createSerializationContext(JsonOps.INSTANCE);
		this.modLoaded = modLoaded;
	}

	/**
	 * @param files      the files as read by {@link DataReader}
	 * @param registries registries with their tags bound
	 * @param modLoaded  answers "is this mod installed"
	 */
	static CompiledData compile(List<RawFile> files, HolderLookup.Provider registries, Predicate<String> modLoaded) {
		DataCompiler compiler = new DataCompiler(registries, modLoaded);
		List<ItemRule> itemRules = new ArrayList<>();
		List<EntityRule> entityRules = new ArrayList<>();
		List<Skip> skips = new ArrayList<>();

		for (RawFile file : files) {
			boolean silenced = false;

			try {
				if (file.problem() != null) {
					throw new Rejected(file.problem(), file.detail());
				} else if (file.json() == null || !file.json().isJsonObject()) {
					throw new Rejected(SkipReason.NOT_AN_OBJECT, "the file is not a JSON object");
				}

				JsonObject root = file.json().getAsJsonObject();
				silenced = isTrue(root.get("silence_error"));

				if (file.kind() == DataKind.ITEM && !root.has("match") && root.has("item")) {
					throw new Rejected(SkipReason.LEGACY_SHAPE, "old format with a top-level item instead of match");
				}

				compiler.checkConditions(root);
				JsonObject match = match(root);

				if (file.kind() == DataKind.ITEM) {
					itemRules.add(compiler.itemRule(file.id(), root, match));
				} else {
					entityRules.add(compiler.entityRule(file.id(), root, match));
				}
			} catch (Rejected rejected) {
				skips.add(new Skip(file.id(), rejected.reason, rejected.getMessage(), rejected.reason.quiet() || silenced));
			} catch (RuntimeException | StackOverflowError | LinkageError e) {
				skips.add(new Skip(file.id(), SkipReason.INVALID, e.toString(), silenced));
			}
		}

		return new CompiledData(itemRules, entityRules, skips);
	}

	private static JsonObject match(JsonObject root) throws Rejected {
		JsonElement match = root.get("match");

		if (match == null) {
			throw new Rejected(SkipReason.NO_MATCH, "there is no match");
		} else if (!match.isJsonObject()) {
			throw new Rejected(SkipReason.NO_MATCH, "match is not an object");
		}

		return match.getAsJsonObject();
	}

	// Conditions

	private void checkConditions(JsonObject root) throws Rejected {
		JsonElement conditions = root.get(ResourceConditions.CONDITIONS_KEY);

		if (conditions == null) {
			return;
		}

		Iterable<JsonElement> all = conditions.isJsonArray() ? conditions.getAsJsonArray() : List.of(conditions);

		for (JsonElement condition : all) {
			if (!condition.isJsonObject()) {
				throw new Rejected(SkipReason.BAD_CONDITIONS, "a load condition is not an object");
			} else if (!this.holds(condition.getAsJsonObject())) {
				throw new Rejected(SkipReason.CONDITIONS_NOT_MET, "the load conditions are not met");
			}
		}
	}

	private boolean holds(JsonObject condition) throws Rejected {
		JsonElement type = condition.get("condition");

		if (isString(type) && MOD_LOADED.equals(type.getAsString())) {
			JsonElement mod = condition.get("modid");

			if (!isString(mod)) {
				throw new Rejected(SkipReason.BAD_CONDITIONS, MOD_LOADED + " needs a modid");
			}

			return this.modLoaded.test(mod.getAsString());
		}

		try {
			return this.decode(ResourceCondition.CODEC, condition, SkipReason.BAD_CONDITIONS, "load condition").test(this.registries);
		} catch (RuntimeException e) {
			throw new Rejected(SkipReason.BAD_CONDITIONS, "load condition: " + e);
		}
	}

	// Item files

	private ItemRule itemRule(ResourceLocation file, JsonObject root, JsonObject match) throws Rejected {
		JsonElement items = match.get("items");

		if (items != null) {
			// Looked at first, so that an id of a mod that is not installed is told apart from a broken file.
			this.holders(items, Registries.ITEM, "match.items");
		}

		ItemPredicate predicate = this.decode(ItemPredicate.CODEC, match, SkipReason.INVALID, "match");

		if (predicate.items().isEmpty()
			&& predicate.count().isAny()
			&& predicate.components().alwaysMatches()
			&& predicate.subPredicates().isEmpty()) {
			throw new Rejected(SkipReason.EMPTY_MATCH, "match has no items, count, components or predicates and would apply to every item");
		}

		return new ItemRule(file, predicate, this.itemLuminance(root.get("luminance")), optionalBoolean(root, "water_sensitive"));
	}

	private int itemLuminance(@Nullable JsonElement json) throws Rejected {
		if (json == null) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, "there is no luminance");
		} else if (!json.isJsonObject()) {
			return level(json, "luminance");
		}

		JsonObject object = json.getAsJsonObject();
		String type = typeName(object.get("type"));

		if (type == null) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, "luminance has no known type: " + object.get("type"));
		}

		return switch (type) {
			case "value" -> level(object.get("value"), "luminance.value");
			case "block" -> {
				JsonElement block = object.get("block");

				if (!isString(block)) {
					throw new Rejected(SkipReason.BAD_LUMINANCE, "luminance.block must be a block id");
				}

				Block value = this.holder(block.getAsString(), Registries.BLOCK, "luminance.block").value();
				yield Math.min(15, value.defaultBlockState().getLightEmission());
			}
			case "block_self" -> ItemRule.BLOCK_SELF;
			default -> throw new Rejected(SkipReason.BAD_LUMINANCE, "unknown item luminance type " + object.get("type"));
		};
	}

	// Entity files

	private EntityRule entityRule(ResourceLocation file, JsonObject root, JsonObject match) throws Rejected {
		if (match.size() == 0) {
			throw new Rejected(SkipReason.EMPTY_MATCH, "match is empty and would apply to every entity");
		}

		for (String key : match.keySet()) {
			if (!ENTITY_MATCH_KEYS.contains(key)) {
				throw new Rejected(SkipReason.UNSUPPORTED_MATCH, "match uses '" + key + "', only type, flags and equipment are supported");
			}
		}

		JsonElement type = match.get("type");

		if (type == null) {
			throw new Rejected(SkipReason.UNSUPPORTED_MATCH, "match needs a type");
		}

		List<EntityType<?>> types = new ArrayList<>();

		for (Holder<EntityType<?>> holder : this.holders(type, Registries.ENTITY_TYPE, "match.type")) {
			types.add(holder.value());
		}

		EntityFlagsPredicate flags = null;
		EntityEquipmentPredicate equipment = null;

		if (match.has("flags")) {
			flags = this.decode(EntityFlagsPredicate.CODEC, match.get("flags"), SkipReason.INVALID, "match.flags");
		}

		if (match.has("equipment")) {
			JsonElement json = match.get("equipment");
			this.checkEquipmentItems(json);
			equipment = this.decode(EntityEquipmentPredicate.CODEC, json, SkipReason.INVALID, "match.equipment");
		}

		JsonElement luminance = root.get("luminance");

		if (luminance != null && luminance.isJsonArray() && luminance.getAsJsonArray().isEmpty()) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, "luminance is an empty list");
		}

		return new EntityRule(file, types, EntityLights.when(flags, equipment, this.lights(luminance, "luminance", 0)));
	}

	private void checkEquipmentItems(JsonElement equipment) throws Rejected {
		if (!equipment.isJsonObject()) {
			return;
		}

		for (String slot : EQUIPMENT_KEYS) {
			JsonElement predicate = equipment.getAsJsonObject().get(slot);

			if (predicate != null && predicate.isJsonObject() && predicate.getAsJsonObject().has("items")) {
				this.holders(predicate.getAsJsonObject().get("items"), Registries.ITEM, "match.equipment." + slot + ".items");
			}
		}
	}

	/** One luminance value or a list of them; a list gives the brightest. */
	private EntityLight lights(@Nullable JsonElement json, String what, int depth) throws Rejected {
		if (json == null) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, "there is no " + what);
		} else if (!json.isJsonArray()) {
			return this.light(json, what, depth);
		}

		List<EntityLight> lights = new ArrayList<>();

		for (JsonElement element : json.getAsJsonArray()) {
			lights.add(this.light(element, what, depth));
		}

		return EntityLights.max(lights);
	}

	private EntityLight optionalLights(JsonObject object, String key, String what, int depth) throws Rejected {
		return object.has(key) ? this.lights(object.get(key), what + "." + key, depth) : EntityLights.NONE;
	}

	private EntityLight light(JsonElement json, String what, int depth) throws Rejected {
		if (!json.isJsonObject()) {
			return EntityLights.constant(level(json, what));
		} else if (depth >= MAX_NESTING) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, what + " is nested too deeply");
		}

		JsonObject object = json.getAsJsonObject();
		String type = typeName(object.get("type"));

		if (type == null) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, what + " has no known type: " + object.get("type"));
		}

		return switch (type) {
			case "value" -> EntityLights.constant(level(object.get("value"), what + ".value"));
			case "water_sensitive" -> EntityLights.inWater(
				this.optionalLights(object, "out_of_water", what, depth + 1),
				this.optionalLights(object, "in_water", what, depth + 1)
			);
			case "wet_sensitive" -> EntityLights.wet(
				this.optionalLights(object, "dry", what, depth + 1),
				this.optionalLights(object, "wet", what, depth + 1)
			);
			case "item" -> this.stackLight(object, what);
			case "item_entity" -> EntityLights.ITEM_ENTITY;
			case "item_frame" -> EntityLights.ITEM_FRAME;
			case "arrow/derived_from_self_item" -> EntityLights.ARROW_ITEM;
			case "projectile/throwable_item" -> EntityLights.THROWN_ITEM;
			case "falling_block" -> EntityLights.FALLING_BLOCK;
			case "minecart/display_block" -> EntityLights.MINECART;
			case "enderman" -> EntityLights.ENDERMAN;
			case "glow_squid" -> EntityLights.GLOW_SQUID;
			case "magma_cube" -> EntityLights.MAGMA_CUBE;
			case "creeper" -> EntityLights.CREEPER;
			case "display" -> EntityLights.display(this.lights(object.get("luminance"), what + ".luminance", depth + 1));
			case "display/block" -> EntityLights.BLOCK_DISPLAY;
			case "display/item" -> EntityLights.ITEM_DISPLAY;
			default -> throw new Rejected(SkipReason.BAD_LUMINANCE, "unknown entity luminance type " + object.get("type"));
		};
	}

	/** Luminance type {@code item}: a stack written into the file. */
	private EntityLight stackLight(JsonObject object, String what) throws Rejected {
		JsonElement json = object.get("item");

		if (json == null || !json.isJsonObject()) {
			throw new Rejected(SkipReason.BAD_LUMINANCE, what + ".item must be an item stack");
		}

		JsonElement id = json.getAsJsonObject().get("id");

		if (isString(id)) {
			this.holder(id.getAsString(), Registries.ITEM, what + ".item.id");
		}

		ItemStack stack = this.decode(ItemStack.CODEC, json, SkipReason.BAD_LUMINANCE, what + ".item");
		JsonElement always = object.get("always");

		if (always == null) {
			return EntityLights.stack(stack, optionalBoolean(object, "include_rain") ? EntityLights.Water.OR_RAIN : EntityLights.Water.SUBMERGED);
		} else if (isString(always) && always.getAsString().equals("dry")) {
			return EntityLights.stack(stack, EntityLights.Water.NEVER);
		} else if (isString(always) && always.getAsString().equals("wet")) {
			return EntityLights.stack(stack, EntityLights.Water.ALWAYS);
		}

		throw new Rejected(SkipReason.BAD_LUMINANCE, what + ".always must be \"dry\" or \"wet\"");
	}

	// Shared helpers

	/**
	 * Reads what vanilla accepts where a set of registry entries is expected: one id, a list of
	 * ids, or one tag written as {@code #id}.
	 */
	private <T> List<Holder<T>> holders(JsonElement json, ResourceKey<? extends Registry<T>> registry, String what) throws Rejected {
		if (json.isJsonArray()) {
			List<Holder<T>> holders = new ArrayList<>();

			for (JsonElement element : json.getAsJsonArray()) {
				if (!isString(element)) {
					throw new Rejected(SkipReason.INVALID, what + " must contain ids only");
				}

				holders.add(this.holder(element.getAsString(), registry, what));
			}

			if (holders.isEmpty()) {
				throw new Rejected(SkipReason.EMPTY_MATCH, what + " is an empty list and matches nothing");
			}

			return holders;
		} else if (!isString(json)) {
			throw new Rejected(SkipReason.INVALID, what + " must be an id, a list of ids or a #tag");
		}

		String text = json.getAsString();

		if (!text.startsWith("#")) {
			return List.of(this.holder(text, registry, what));
		}

		ResourceLocation id = ResourceLocation.tryParse(text.substring(1));

		if (id == null) {
			throw new Rejected(SkipReason.INVALID, what + ": '" + text + "' is not a tag");
		}

		HolderLookup.RegistryLookup<T> lookup = this.registries.lookupOrThrow(registry);
		Optional<HolderSet.Named<T>> tag = lookup.get(TagKey.create(registry, id));

		if (tag.isEmpty() || tag.get().size() == 0) {
			throw new Rejected(SkipReason.UNKNOWN_ID, what + ": tag " + text + " is unknown or empty");
		}

		return tag.get().stream().toList();
	}

	private <T> Holder.Reference<T> holder(String text, ResourceKey<? extends Registry<T>> registry, String what) throws Rejected {
		ResourceLocation id = ResourceLocation.tryParse(text);

		if (id == null) {
			throw new Rejected(SkipReason.INVALID, what + ": '" + text + "' is not an id");
		}

		HolderLookup.RegistryLookup<T> lookup = this.registries.lookupOrThrow(registry);
		Optional<Holder.Reference<T>> holder = lookup.get(ResourceKey.create(registry, id));

		if (holder.isEmpty()) {
			throw new Rejected(SkipReason.UNKNOWN_ID, what + ": " + id + " does not exist");
		}

		return holder.get();
	}

	private <T> T decode(Codec<T> codec, JsonElement json, SkipReason reason, String what) throws Rejected {
		DataResult<T> result = codec.parse(this.ops, json);
		Optional<T> value = result.result();

		if (result.isError() || value.isEmpty()) {
			throw new Rejected(reason, what + ": " + result.error().map(DataResult.Error::message).orElse("cannot be decoded"));
		}

		return value.get();
	}

	private static int level(@Nullable JsonElement json, String what) throws Rejected {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			double value = json.getAsDouble();

			if (value == Math.rint(value) && value >= 0.0 && value <= 15.0) {
				return (int) value;
			}
		}

		throw new Rejected(SkipReason.BAD_LUMINANCE, what + " must be a whole number from 0 to 15 or an object with a type");
	}

	/**
	 * @return the type name without its namespace, or {@code null} if it is not one of ours
	 */
	@Nullable
	private static String typeName(@Nullable JsonElement json) {
		if (!isString(json)) {
			return null;
		}

		String text = json.getAsString();
		int colon = text.indexOf(':');

		if (colon < 0) {
			return text;
		}

		return TYPE_NAMESPACES.contains(text.substring(0, colon)) ? text.substring(colon + 1) : null;
	}

	private static boolean optionalBoolean(JsonObject object, String key) throws Rejected {
		JsonElement json = object.get(key);

		if (json == null) {
			return false;
		} else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			return json.getAsBoolean();
		}

		throw new Rejected(SkipReason.INVALID, key + " must be true or false");
	}

	private static boolean isTrue(@Nullable JsonElement json) {
		return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean() && json.getAsBoolean();
	}

	private static boolean isString(@Nullable JsonElement json) {
		return json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString();
	}

	/** Ends the decoding of one file. Carries no stack trace: it is control flow, and expected. */
	private static final class Rejected extends Exception {
		private final SkipReason reason;

		Rejected(SkipReason reason, String message) {
			super(message, null, false, false);
			this.reason = reason;
		}
	}
}
