package navrelis.dynamiclighting.luminance;

import static navrelis.dynamiclighting.luminance.TestGame.compile;
import static navrelis.dynamiclighting.luminance.TestGame.entity;
import static navrelis.dynamiclighting.luminance.TestGame.item;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import navrelis.dynamiclighting.config.FuseMode;
import navrelis.dynamiclighting.config.LightRange;
import navrelis.dynamiclighting.config.Mode;
import navrelis.dynamiclighting.config.Options;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link Luminance#ofEntity} from the outside, with entities that can exist without a world.
 * Mobs cannot, and water needs a world too; their parts are covered by the table and format tests.
 */
class LuminanceTest {
	@BeforeAll
	static void boot() {
		TestGame.registries();
	}

	@BeforeEach
	@AfterEach
	void reset() {
		Options.set(Options.DEFAULT);
		Luminance.install(CompiledData.EMPTY);
	}

	/** The glowing option is off by default here: asking a living entity for its outline needs a world. */
	private static Options options(boolean entityLights, FuseMode tnt, String... disabledTypes) {
		Set<ResourceLocation> disabled = Set.copyOf(List.of(disabledTypes).stream().map(ResourceLocation::parse).toList());
		return new Options(Mode.FANCY, LightRange.SHORT, entityLights, true, true, false, FuseMode.SIMPLE, tnt, disabled);
	}

	private static ItemEntity dropped(ItemStack stack) {
		ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
		entity.setItem(stack);
		return entity;
	}

	private static ArmorStand stand() {
		Options.set(options(true, FuseMode.SIMPLE));
		return new ArmorStand(EntityType.ARMOR_STAND, null);
	}

	private static void burn(Entity entity) {
		entity.setRemainingFireTicks(100);
	}

	// Before and after data

	@Test
	void beforeAnyDataOnlyBuiltInRulesAndBlockItemsGlow() {
		assertEquals(10, Luminance.ofEntity(new PrimedTnt(EntityType.TNT, null)));
		assertEquals(15, Luminance.ofEntity(dropped(new ItemStack(Items.GLOWSTONE))));
		assertEquals(14, Luminance.ofEntity(dropped(new ItemStack(Items.TORCH))));
		assertEquals(0, Luminance.ofEntity(dropped(new ItemStack(Items.BLAZE_ROD))));
		assertEquals(0, Luminance.ofEntity(new SpectralArrow(EntityType.SPECTRAL_ARROW, null)));
	}

	@Test
	void builtInDataLightsItemsThatAreNotBlocks() throws IOException {
		Luminance.install(compile(TestGame.builtInFiles()));

		assertEquals(10, Luminance.ofEntity(dropped(new ItemStack(Items.BLAZE_ROD))));
		assertEquals(12, Luminance.ofEntity(dropped(new ItemStack(Items.NETHER_STAR))));
		assertEquals(8, Luminance.ofEntity(new SpectralArrow(EntityType.SPECTRAL_ARROW, null)));
		assertEquals(0, Luminance.ofEntity(dropped(new ItemStack(Items.STICK))));
	}

	@Test
	void newDataReplacesTheOldRulesAtOnce() {
		ItemEntity stick = dropped(new ItemStack(Items.STICK));
		assertEquals(0, Luminance.ofEntity(stick));

		Luminance.install(compile(item("stick", """
			{ "match": { "items": "minecraft:stick" }, "luminance": 5 }
			""")));
		assertEquals(5, Luminance.ofEntity(stick));

		Luminance.install(CompiledData.EMPTY);
		assertEquals(0, Luminance.ofEntity(stick));
	}

	// Order of the rules

	@Test
	void entitiesWithoutALightOnlyGlowWhileBurning() {
		EyeOfEnder eye = new EyeOfEnder(EntityType.EYE_OF_ENDER, null);
		assertEquals(0, Luminance.ofEntity(eye));

		burn(eye);
		assertEquals(15, Luminance.ofEntity(eye));

		eye.setInvisible(true);
		assertEquals(0, Luminance.ofEntity(eye));
	}

	@Test
	void fireBeatsEveryOtherLightAndInvisibilityBeatsFire() {
		ItemEntity torch = dropped(new ItemStack(Items.TORCH));
		burn(torch);
		assertEquals(15, Luminance.ofEntity(torch));

		torch.setInvisible(true);
		assertEquals(0, Luminance.ofEntity(torch));

		torch.clearFire();
		assertEquals(0, Luminance.ofEntity(torch));
	}

	@Test
	void disabledTypesNeverEmit() {
		PrimedTnt tnt = new PrimedTnt(EntityType.TNT, null);
		ItemEntity glowstone = dropped(new ItemStack(Items.GLOWSTONE));
		EyeOfEnder burning = new EyeOfEnder(EntityType.EYE_OF_ENDER, null);
		burn(burning);
		ArmorStand burningStand = new ArmorStand(EntityType.ARMOR_STAND, null);
		burn(burningStand);

		Options.set(options(true, FuseMode.SIMPLE, "minecraft:tnt", "minecraft:eye_of_ender"));
		assertEquals(0, Luminance.ofEntity(tnt));
		assertEquals(0, Luminance.ofEntity(burning));
		assertEquals(15, Luminance.ofEntity(glowstone));
		assertEquals(15, Luminance.ofEntity(burningStand));

		Options.set(options(true, FuseMode.SIMPLE, "minecraft:item", "minecraft:armor_stand"));
		assertEquals(10, Luminance.ofEntity(tnt));
		assertEquals(15, Luminance.ofEntity(burning));
		assertEquals(0, Luminance.ofEntity(glowstone));
		assertEquals(0, Luminance.ofEntity(burningStand));
	}

	@Test
	void entityLightsOffSilencesEverythingButTheLocalPlayer() {
		PrimedTnt tnt = new PrimedTnt(EntityType.TNT, null);
		EyeOfEnder burning = new EyeOfEnder(EntityType.EYE_OF_ENDER, null);
		burn(burning);
		ArmorStand stand = stand();
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));

		Options.set(options(false, FuseMode.SIMPLE));

		assertEquals(0, Luminance.ofEntity(tnt));
		assertEquals(0, Luminance.ofEntity(burning));
		assertEquals(0, Luminance.ofEntity(dropped(new ItemStack(Items.GLOWSTONE))));
		assertEquals(0, Luminance.ofEntity(stand));
	}

	// Living entities

	@Test
	void livingEntitiesGlowWithWhatTheyHoldAndWear() {
		ArmorStand stand = stand();
		assertEquals(0, Luminance.ofEntity(stand));

		stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SOUL_TORCH));
		assertEquals(10, Luminance.ofEntity(stand));

		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));
		assertEquals(14, Luminance.ofEntity(stand));

		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GLOWSTONE));
		assertEquals(15, Luminance.ofEntity(stand));
	}

	@Test
	void invisibleLivingEntitiesAreDarkAndBurningOnesAreBright() {
		ArmorStand stand = stand();
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));

		stand.setInvisible(true);
		assertEquals(0, Luminance.ofEntity(stand));

		stand.setInvisible(false);
		burn(stand);
		assertEquals(15, Luminance.ofEntity(stand));
	}

	@Test
	void equipmentPredicateOfAnEntityFileIsEvaluated() {
		Luminance.install(compile(entity("torch_bearer", """
			{
				"match": { "type": "minecraft:armor_stand", "equipment": { "feet": { "items": "minecraft:golden_boots" } } },
				"luminance": 9
			}
			""")));
		ArmorStand stand = stand();
		assertEquals(0, Luminance.ofEntity(stand));

		stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
		assertEquals(9, Luminance.ofEntity(stand));
	}

	// Primed TNT

	@Test
	void tntFollowsItsOption() {
		PrimedTnt tnt = new PrimedTnt(EntityType.TNT, null);

		Options.set(options(true, FuseMode.OFF));
		assertEquals(0, Luminance.ofEntity(tnt));

		Options.set(options(true, FuseMode.SIMPLE));
		assertEquals(10, Luminance.ofEntity(tnt));

		Options.set(options(true, FuseMode.FANCY));
		tnt.setFuse(80);
		assertEquals(4, Luminance.ofEntity(tnt));
		tnt.setFuse(400);
		assertEquals(4, Luminance.ofEntity(tnt));
		tnt.setFuse(40);
		assertEquals(9, Luminance.ofEntity(tnt));
		tnt.setFuse(0);
		assertEquals(14, Luminance.ofEntity(tnt));
	}

	// Entity files

	@Test
	void entityFilesAddToTheBuiltInRule() {
		Luminance.install(compile(
			entity("tnt", """
				{ "match": { "type": "minecraft:tnt" }, "luminance": [3, 12] }
				"""),
			entity("eye", """
				{ "match": { "type": ["minecraft:eye_of_ender"], "flags": { "is_sprinting": false } }, "luminance": 9 }
				"""),
			entity("sprinting", """
				{ "match": { "type": "minecraft:eye_of_ender", "flags": { "is_sprinting": true } }, "luminance": 13 }
				""")
		));
		PrimedTnt tnt = new PrimedTnt(EntityType.TNT, null);
		EyeOfEnder eye = new EyeOfEnder(EntityType.EYE_OF_ENDER, null);

		assertEquals(12, Luminance.ofEntity(tnt));
		assertEquals(9, Luminance.ofEntity(eye));

		Options.set(options(true, FuseMode.OFF));
		assertEquals(12, Luminance.ofEntity(tnt));

		eye.setSprinting(true);
		assertEquals(13, Luminance.ofEntity(eye));

		eye.setInvisible(true);
		assertEquals(0, Luminance.ofEntity(eye));
	}

	// Built-in rules for entities that need no world

	@Test
	void projectilesAndEffects() {
		assertEquals(14, Luminance.ofEntity(new LargeFireball(EntityType.FIREBALL, null)));
		assertEquals(12, Luminance.ofEntity(new SmallFireball(EntityType.SMALL_FIREBALL, null)));
		assertEquals(12, Luminance.ofEntity(new DragonFireball(EntityType.DRAGON_FIREBALL, null)));
		assertEquals(8, Luminance.ofEntity(new WitherSkull(EntityType.WITHER_SKULL, null)));
		assertEquals(6, Luminance.ofEntity(new ShulkerBullet(EntityType.SHULKER_BULLET, null)));
		assertEquals(12, Luminance.ofEntity(new FireworkRocketEntity(EntityType.FIREWORK_ROCKET, null)));
		assertEquals(15, Luminance.ofEntity(new LightningBolt(EntityType.LIGHTNING_BOLT, null)));
		assertEquals(12, Luminance.ofEntity(new EndCrystal(EntityType.END_CRYSTAL, null)));
	}

	@Test
	void thrownItemsGlowWithTheirStack() {
		Snowball snowball = new Snowball(EntityType.SNOWBALL, null);
		assertEquals(0, Luminance.ofEntity(snowball));

		Luminance.install(compile(item("snowball", """
			{ "match": { "items": "minecraft:snowball" }, "luminance": 5 }
			""")));
		assertEquals(5, Luminance.ofEntity(snowball));
	}

	@Test
	void minecartsGlowWithTheBlockTheyShow() {
		Minecart minecart = new Minecart(EntityType.MINECART, null);
		assertEquals(0, Luminance.ofEntity(minecart));

		minecart.setDisplayBlockState(Blocks.GLOWSTONE.defaultBlockState());
		assertEquals(15, Luminance.ofEntity(minecart));

		minecart.setDisplayBlockState(Blocks.SOUL_LANTERN.defaultBlockState());
		assertEquals(10, Luminance.ofEntity(minecart));
	}

	// Contract

	@Test
	void neverThrows() {
		assertEquals(0, Luminance.ofEntity(null));
		// Still works afterwards.
		assertEquals(15, Luminance.ofEntity(dropped(new ItemStack(Items.GLOWSTONE))));
	}
}
