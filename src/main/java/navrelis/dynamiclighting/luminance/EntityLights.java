package navrelis.dynamiclighting.luminance;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.advancements.critereon.EntityEquipmentPredicate;
import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * All the ways an entity can emit light: the rules built into the mod and the building blocks
 * that data files name in their {@code luminance}.
 */
final class EntityLights {
	static final EntityLight NONE = new Constant(0);

	static final int BLAZE = 10;
	static final int MAGMA_CUBE_LIGHT = 8;
	static final int ALLAY = 6;
	static final int GLOW_SQUID_LIGHT = 10;
	static final int FIREBALL = 14;
	static final int SMALL_FIREBALL = 12;
	static final int DRAGON_FIREBALL = 12;
	static final int WITHER_SKULL = 8;
	static final int SHULKER_BULLET = 6;
	static final int FIREWORK_ROCKET = 12;
	static final int LIGHTNING_BOLT = 15;
	static final int END_CRYSTAL = 12;

	/** Dropped item: its stack, wet while the item is in water. */
	static final EntityLight ITEM_ENTITY = (entity, rules) ->
		entity instanceof ItemEntity item ? rules.stackLight(item.getItem(), entity, Water.TOUCHING) : 0;

	/** Item frame: the framed stack, wet if there is water at the frame's block. Frames do not track water themselves. */
	static final EntityLight ITEM_FRAME = (entity, rules) ->
		entity instanceof ItemFrame frame ? rules.stackLight(frame.getItem(), entity, Water.AT_BLOCK) : 0;

	/** Arrow: the item it is picked up as. */
	static final EntityLight ARROW_ITEM = (entity, rules) ->
		entity instanceof AbstractArrow arrow ? rules.stackLight(arrow.getPickupItemStackOrigin(), entity, Water.TOUCHING) : 0;

	/** Thrown item such as a snowball or a potion: the thrown stack. */
	static final EntityLight THROWN_ITEM = (entity, rules) ->
		entity instanceof ThrowableItemProjectile thrown ? rules.stackLight(thrown.getItem(), entity, Water.TOUCHING) : 0;

	static final EntityLight FALLING_BLOCK = (entity, rules) ->
		entity instanceof FallingBlockEntity falling ? emission(falling.getBlockState()) : 0;

	static final EntityLight MINECART = (entity, rules) ->
		entity instanceof AbstractMinecart minecart ? emission(minecart.getDisplayBlockState()) : 0;

	static final EntityLight ENDERMAN = (entity, rules) ->
		entity instanceof EnderMan enderman ? emission(enderman.getCarriedBlock()) : 0;

	/** Dark while the squid's dark ticks run (after it was hurt). */
	static final EntityLight GLOW_SQUID = (entity, rules) ->
		entity instanceof GlowSquid squid && squid.getDarkTicksRemaining() <= 0 ? GLOW_SQUID_LIGHT : 0;

	static final EntityLight MAGMA_CUBE = (entity, rules) -> entity instanceof MagmaCube ? MAGMA_CUBE_LIGHT : 0;

	static final EntityLight CREEPER = (entity, rules) ->
		entity instanceof Creeper creeper ? FuseLight.creeper(rules.options().creeper(), creeper.getSwelling(1.0F)) : 0;

	static final EntityLight PRIMED_TNT = (entity, rules) ->
		entity instanceof PrimedTnt tnt ? FuseLight.tnt(rules.options().tnt(), tnt.getFuse()) : 0;

	/** The plain data type: no look at the brightness override. */
	static final EntityLight BLOCK_DISPLAY = (entity, rules) -> {
		if (entity instanceof Display.BlockDisplay display) {
			Display.BlockDisplay.BlockRenderState state = display.blockRenderState();
			return state == null ? 0 : emission(state.blockState());
		}

		return 0;
	};

	/** The plain data type: no look at the brightness override. Displays do not track water, so the block is asked. */
	static final EntityLight ITEM_DISPLAY = (entity, rules) ->
		entity instanceof Display.ItemDisplay display ? rules.stackLight(display.getSlot(0).get(), entity, Water.AT_BLOCK) : 0;

	private static final EntityLight UNLIT_BLOCK_DISPLAY = display(BLOCK_DISPLAY);
	private static final EntityLight UNLIT_ITEM_DISPLAY = display(ITEM_DISPLAY);

	private static final Map<EntityType<?>, EntityLight> BY_TYPE = new IdentityHashMap<>();

	static {
		BY_TYPE.put(EntityType.TNT, PRIMED_TNT);
		BY_TYPE.put(EntityType.CREEPER, CREEPER);
		BY_TYPE.put(EntityType.BLAZE, constant(BLAZE));
		BY_TYPE.put(EntityType.MAGMA_CUBE, MAGMA_CUBE);
		BY_TYPE.put(EntityType.ALLAY, constant(ALLAY));
		BY_TYPE.put(EntityType.GLOW_SQUID, GLOW_SQUID);
		BY_TYPE.put(EntityType.ENDERMAN, ENDERMAN);
		BY_TYPE.put(EntityType.FIREBALL, inWater(constant(FIREBALL), NONE));
		BY_TYPE.put(EntityType.SMALL_FIREBALL, inWater(constant(SMALL_FIREBALL), NONE));
		BY_TYPE.put(EntityType.DRAGON_FIREBALL, constant(DRAGON_FIREBALL));
		BY_TYPE.put(EntityType.WITHER_SKULL, constant(WITHER_SKULL));
		BY_TYPE.put(EntityType.SHULKER_BULLET, constant(SHULKER_BULLET));
		BY_TYPE.put(EntityType.SPECTRAL_ARROW, ARROW_ITEM);
		BY_TYPE.put(EntityType.FIREWORK_ROCKET, constant(FIREWORK_ROCKET));
		BY_TYPE.put(EntityType.LIGHTNING_BOLT, constant(LIGHTNING_BOLT));
		BY_TYPE.put(EntityType.END_CRYSTAL, constant(END_CRYSTAL));
	}

	private EntityLights() {
	}

	/**
	 * The rule built into the mod for this kind of entity. Entities with a fixed light are known by
	 * type; carriers of a block or an item are known by class, so that modded minecarts, thrown
	 * items and frames are covered too.
	 *
	 * @return the rule, or {@code null} if entities like this one only emit while burning
	 */
	@Nullable
	static EntityLight builtIn(Entity entity) {
		EntityLight byType = BY_TYPE.get(entity.getType());

		if (byType != null) {
			return byType;
		} else if (entity instanceof ItemEntity) {
			return ITEM_ENTITY;
		} else if (entity instanceof ItemFrame) {
			return ITEM_FRAME;
		} else if (entity instanceof ThrowableItemProjectile) {
			return THROWN_ITEM;
		} else if (entity instanceof FallingBlockEntity) {
			return FALLING_BLOCK;
		} else if (entity instanceof AbstractMinecart) {
			return MINECART;
		} else if (entity instanceof Display.BlockDisplay) {
			return UNLIT_BLOCK_DISPLAY;
		} else if (entity instanceof Display.ItemDisplay) {
			return UNLIT_ITEM_DISPLAY;
		}

		return null;
	}

	static EntityLight constant(int luminance) {
		return luminance <= 0 ? NONE : new Constant(luminance);
	}

	/** The brightest of several lights. */
	static EntityLight max(List<EntityLight> lights) {
		if (lights.isEmpty()) {
			return NONE;
		} else if (lights.size() == 1) {
			return lights.get(0);
		}

		return new Brightest(lights);
	}

	static int max(EntityLight[] lights, Entity entity, LightRules rules) {
		int best = 0;

		for (EntityLight light : lights) {
			best = Math.max(best, light.luminance(entity, rules));

			if (best >= 15) {
				return 15;
			}
		}

		return best;
	}

	static EntityLight inWater(EntityLight outOfWater, EntityLight inWater) {
		return new InWater(outOfWater, inWater);
	}

	static EntityLight wet(EntityLight dry, EntityLight wet) {
		return new Wet(dry, wet);
	}

	static EntityLight stack(ItemStack stack, Water water) {
		return new Stack(stack, water);
	}

	static EntityLight display(EntityLight light) {
		return new UnlitDisplay(light);
	}

	/** Restricts a light to entities that pass the predicates of an entity file. */
	static EntityLight when(@Nullable EntityFlagsPredicate flags, @Nullable EntityEquipmentPredicate equipment, EntityLight light) {
		return flags == null && equipment == null ? light : new When(flags, equipment, light);
	}

	private static int emission(@Nullable BlockState state) {
		return state == null ? 0 : Math.min(15, state.getLightEmission());
	}

	/** A fixed light. */
	record Constant(int value) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			return this.value;
		}
	}

	/** A list of lights in a data file: the brightest counts. */
	record Brightest(List<EntityLight> lights) implements EntityLight {
		Brightest {
			lights = List.copyOf(lights);
		}

		@Override
		public int luminance(Entity entity, LightRules rules) {
			int best = 0;

			// Indexed on purpose: no iterator is allocated.
			for (int i = 0, size = this.lights.size(); i < size && best < 15; i++) {
				best = Math.max(best, this.lights.get(i).luminance(entity, rules));
			}

			return Math.min(15, best);
		}
	}

	/** Data type {@code water_sensitive}: one light under water, another one out of it. Not tied to the water option. */
	record InWater(EntityLight outOfWater, EntityLight inWater) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			return (Water.SUBMERGED.test(entity) ? this.inWater : this.outOfWater).luminance(entity, rules);
		}
	}

	/** Data type {@code wet_sensitive}: like {@link InWater}, and rain counts as wet. Not tied to the water option. */
	record Wet(EntityLight dry, EntityLight wet) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			return (Water.OR_RAIN.test(entity) ? this.wet : this.dry).luminance(entity, rules);
		}
	}

	/** Data type {@code item}: the light of a stack written into the file. */
	record Stack(ItemStack stack, Water water) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			return rules.stackLight(this.stack, entity, this.water);
		}
	}

	/** Data type {@code display}: the wrapped light, unless the display entity has a brightness override. */
	record UnlitDisplay(EntityLight light) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			if (entity instanceof Display display) {
				Display.RenderState state = display.renderState();

				if (state != null && state.brightnessOverride() == -1) {
					return this.light.luminance(entity, rules);
				}
			}

			return 0;
		}
	}

	/** A light behind the {@code flags} and {@code equipment} predicates of an entity file. */
	record When(@Nullable EntityFlagsPredicate flags, @Nullable EntityEquipmentPredicate equipment, EntityLight light) implements EntityLight {
		@Override
		public int luminance(Entity entity, LightRules rules) {
			if (this.flags != null && !this.flags.matches(entity)) {
				return 0;
			} else if (this.equipment != null && !this.equipment.matches(entity)) {
				return 0;
			}

			return this.light.luminance(entity, rules);
		}
	}

	/**
	 * How to find out whether an entity is in water. The tests are constants so that asking allocates nothing.
	 */
	@FunctionalInterface
	interface Water {
		/** The entity's own water tracking, kept up to date by its tick. */
		Water TOUCHING = Entity::isInWater;
		/** In water with the eyes below the surface. */
		Water SUBMERGED = Entity::isUnderWater;
		/** Water in the block the entity is in, for entities whose tick does not track water. */
		Water AT_BLOCK = entity -> entity.level().getFluidState(entity.blockPosition()).is(FluidTags.WATER);
		/** In water or a bubble column, or standing in the rain. The rain test is skipped while it does not rain. */
		Water OR_RAIN = entity -> entity.isInWaterOrBubble() || entity.level().isRaining() && entity.isInWaterRainOrBubble();
		Water ALWAYS = entity -> true;
		Water NEVER = entity -> false;

		boolean test(Entity entity);
	}
}
