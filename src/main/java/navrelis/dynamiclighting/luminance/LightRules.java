package navrelis.dynamiclighting.luminance;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import navrelis.dynamiclighting.config.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Everything {@code Luminance.ofEntity} needs for one combination of applied data and options:
 * the item table and, per entity type, the lights that apply to it.
 * <p>
 * The per-type decision is made once, the first time an entity of that type is seen, and then
 * found again with one identity-map probe. A new instance is made when data or options change.
 * Used on the client thread only.
 */
final class LightRules {
	/** Plan of a type the user switched off. */
	static final EntityLight[] DISABLED = new EntityLight[0];
	/** Plan of a type without any light of its own. */
	static final EntityLight[] NOTHING = new EntityLight[0];

	private final Options options;
	private final ItemTable items;
	private final Map<EntityType<?>, List<EntityLight>> fromData;
	private final Reference2ObjectOpenHashMap<EntityType<?>, EntityLight[]> plans = new Reference2ObjectOpenHashMap<>();

	private LightRules(Options options, ItemTable items, Map<EntityType<?>, List<EntityLight>> fromData) {
		this.options = options;
		this.items = items;
		this.fromData = fromData;
	}

	static LightRules of(Options options, ItemTable items, List<EntityRule> entityRules) {
		Map<EntityType<?>, List<EntityLight>> fromData = new IdentityHashMap<>();

		for (EntityRule rule : entityRules) {
			for (EntityType<?> type : rule.types()) {
				fromData.computeIfAbsent(type, key -> new ArrayList<>()).add(rule.light());
			}
		}

		return new LightRules(options, items, fromData);
	}

	/** The same data under other options: the item table is kept, the per-type plans are made anew. */
	LightRules withOptions(Options options) {
		return new LightRules(options, this.items, this.fromData);
	}

	Options options() {
		return this.options;
	}

	ItemTable items() {
		return this.items;
	}

	/**
	 * @return the lights for entities of this entity's type, {@link #DISABLED} or {@link #NOTHING}
	 */
	EntityLight[] plan(Entity entity) {
		EntityLight[] plan = this.plans.get(entity.getType());
		return plan != null ? plan : this.makePlan(entity);
	}

	private EntityLight[] makePlan(Entity entity) {
		EntityType<?> type = entity.getType();
		EntityLight[] plan;

		if (this.options.disabledEntityTypes().contains(EntityType.getKey(type))) {
			plan = DISABLED;
		} else {
			List<EntityLight> lights = new ArrayList<>();
			EntityLight builtIn = EntityLights.builtIn(entity);

			if (builtIn != null) {
				lights.add(builtIn);
			}

			lights.addAll(this.fromData.getOrDefault(type, List.of()));
			plan = lights.isEmpty() ? NOTHING : lights.toArray(EntityLight[]::new);
		}

		this.plans.put(type, plan);
		return plan;
	}

	/**
	 * Light of a stack that an entity carries or shows. Water is only looked for when it can
	 * change the result and the user wants water-sensitive items to go dark.
	 */
	int stackLight(ItemStack stack, Entity entity, EntityLights.Water water) {
		ItemTable.Entry entry = this.items.entry(stack);

		if (entry == null) {
			return 0;
		}

		boolean wet = entry.waterDependent() && this.options.waterSensitive() && water.test(entity);
		return entry.luminance(stack, wet);
	}
}
