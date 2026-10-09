package navrelis.dynamiclighting.luminance;

import java.util.List;

import navrelis.dynamiclighting.DynamicLighting;
import navrelis.dynamiclighting.config.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Decides what emits light. The engine asks {@link #ofEntity(Entity)} and does nothing else.
 */
public final class Luminance {
	/** Light of anything that burns. */
	static final int BURNING = 15;
	/** Light of a living entity with the glowing outline. */
	static final int GLOWING = 10;

	private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

	/** The applied data under the current options. Client thread only, like everything below. */
	private static LightRules rules;
	private static boolean failureLogged;
	/** What {@link #ofEntity} last swallowed, for tests: to them "emits nothing" and "failed" look the same. */
	private static Throwable lastFailure;

	private Luminance() {
	}

	/**
	 * Returns what {@link #ofEntity} swallowed since the last call, and forgets it.
	 *
	 * @return the most recent failure, {@code null} if there was none
	 */
	static Throwable takeFailure() {
		Throwable failure = lastFailure;
		lastFailure = null;
		return failure;
	}

	/**
	 * Registers the resource reload listener for the data files and the handler that applies them
	 * when the client receives tags. Called once from the mod entry point.
	 */
	public static void init() {
		LightDataLoader.register();
	}

	/**
	 * Returns the luminance 0..15 this entity emits right now.
	 * <p>
	 * Called on the client thread only, once per evaluated entity. Already applies every option in
	 * {@code Options} (entity lights, self light, disabled types, glowing, fuse modes, water) and the
	 * rules for invisible entities and spectators, so callers do no filtering of their own. Must be
	 * cheap for entities that emit nothing. Never throws.
	 *
	 * @param entity the entity to evaluate
	 * @return the emitted luminance, 0 to 15
	 */
	public static int ofEntity(Entity entity) {
		try {
			return compute(entity);
		} catch (Throwable t) {
			lastFailure = t;

			if (!failureLogged) {
				failureLogged = true;
				DynamicLighting.LOGGER.error(
					"Could not work out the light of an entity of type {}; further failures are not logged",
					entity == null ? null : entity.getType(), t
				);
			}

			return 0;
		}
	}

	/**
	 * Replaces the applied data. Before the first call only the built-in entity rules and the
	 * light of block items are in effect.
	 * <p>
	 * The item table is built here, while a world is being joined or resources are reloaded, and
	 * not later in the middle of a tick.
	 */
	static void install(CompiledData compiled) {
		Options options = Options.get();
		rules = LightRules.of(options, ItemTable.build(compiled.itemRules()), compiled.entityRules());
		failureLogged = false;
		WornItems.clear();
	}

	private static LightRules rules(Options options) {
		LightRules current = rules;

		if (current == null) {
			// Nothing was applied yet: block items and the built-in entity rules only.
			current = LightRules.of(options, ItemTable.build(List.of()), List.of());
			rules = current;
		} else if (current.options() != options) {
			// The options are replaced as a whole, so comparing the reference is enough.
			current = current.withOptions(options);
			rules = current;
		}

		return current;
	}

	private static int compute(Entity entity) {
		Options options = Options.get();
		LightRules current = rules(options);
		EntityLight[] plan = current.plan(entity);

		if (plan == LightRules.DISABLED) {
			return 0;
		} else if (entity instanceof LivingEntity living) {
			return ofLiving(living, plan, current);
		} else if (!options.entityLights()) {
			return 0;
		} else if (plan == LightRules.NOTHING) {
			// The common case: this is all an entity without a light of its own costs.
			return entity.isOnFire() && !entity.isInvisible() ? BURNING : 0;
		} else if (entity.isInvisible()) {
			return 0;
		} else if (entity.isOnFire()) {
			return BURNING;
		}

		return EntityLights.max(plan, entity, current);
	}

	private static int ofLiving(LivingEntity living, EntityLight[] plan, LightRules rules) {
		Options options = rules.options();
		Minecraft minecraft = Minecraft.getInstance();
		boolean self = minecraft != null && living == minecraft.player;

		if (self ? !options.selfLight() : !options.entityLights()) {
			return 0;
		} else if (living.isSpectator()) {
			return 0;
		} else if (!self && living.isInvisible()) {
			// The local player keeps the light: a torch still works under invisibility, and nobody else is given away.
			return 0;
		} else if (living.isOnFire()) {
			return BURNING;
		}

		int luminance = options.glowingEntities() && living.isCurrentlyGlowing() ? GLOWING : 0;
		luminance = Math.max(luminance, equipment(living, rules));

		if (luminance < 15 && living instanceof Player player) {
			luminance = Math.max(luminance, WornItems.luminance(player, rules));
		}

		if (luminance < 15 && plan != LightRules.NOTHING) {
			luminance = Math.max(luminance, EntityLights.max(plan, living, rules));
		}

		return luminance;
	}

	/**
	 * Brightest item in the hands, armour and body slots. Held and worn items are wet when the
	 * entity's eyes are in water; that is only looked up if an item is found that cares.
	 */
	private static int equipment(LivingEntity living, LightRules rules) {
		ItemTable items = rules.items();
		int best = 0;
		int wetness = -1;

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = living.getItemBySlot(slot);
			ItemTable.Entry entry = items.entry(stack);

			if (entry == null) {
				continue;
			}

			boolean wet = false;

			if (entry.waterDependent()) {
				if (wetness < 0) {
					wetness = rules.options().waterSensitive() && living.isEyeInFluid(FluidTags.WATER) ? 1 : 0;
				}

				wet = wetness == 1;
			}

			best = Math.max(best, entry.luminance(stack, wet));

			if (best >= 15) {
				break;
			}
		}

		return best;
	}
}
