package navrelis.dynamiclighting.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs under {@code fabric-loader-junit}: Fabric Loader has loaded this mod and applies its mixins to
 * the real, mapped game classes as they load. Calling the hooked methods here therefore proves that
 * the mixins found their targets, not only that the merge arithmetic is right.
 */
class LightHooksTest {
	private static final int SKY = 11;
	private static final int BLOCK = 2;
	private static final int VANILLA = SKY << 20 | BLOCK << 4;

	/**
	 * One block diagonally from the light: 14 - 2 * sqrt(2) = 11.17 levels.
	 */
	private static final BlockPos NEAR = new BlockPos(1, 64, 1);
	private static final BlockPos AT_LIGHT = new BlockPos(0, 64, 0);
	private static final BlockPos FAR = new BlockPos(40, 64, 0);
	private static final double NEAR_LIGHT = 14.0 - 2.0 * Math.sqrt(2.0);
	private static final int NEAR_SIXTEENTHS = 178;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@BeforeEach
	void publishOneLight() {
		LightSnapshot.publish(new SnapshotBuilder().build(
			1, new double[] {0.5}, new double[] {64.5}, new double[] {0.5}, new int[] {14}, 2.0
		));
	}

	@AfterEach
	void publishNoLight() {
		LightSnapshot.publish(LightSnapshot.EMPTY);
	}

	@Test
	void testsRunWithoutSodium() {
		// The expected values below are the sixteenth variant.
		assertFalse(LightHooks.SODIUM_LOADED);
		assertEquals(NEAR_SIXTEENTHS, (int) (NEAR_LIGHT * 16.0));
	}

	@Test
	void levelRendererRaisesBlockLightInAir() {
		FakeLevel level = new FakeLevel(Blocks.AIR.defaultBlockState());

		assertEquals(SKY << 20 | NEAR_SIXTEENTHS, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), NEAR));
		assertEquals(SKY << 20 | 14 << 4, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), AT_LIGHT));
		// One block along an axis: 14 - 2.
		assertEquals(SKY << 20 | 12 << 4, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), new BlockPos(0, 65, 0)));
	}

	@Test
	void levelRendererTwoArgumentFormIsCoveredToo() {
		// Block entities and particles use this form; it fetches the state and calls the hooked one.
		assertEquals(SKY << 20 | NEAR_SIXTEENTHS, LevelRenderer.getLightColor(new FakeLevel(Blocks.AIR.defaultBlockState()), NEAR));
		assertEquals(VANILLA, LevelRenderer.getLightColor(new FakeLevel(Blocks.STONE.defaultBlockState()), NEAR));
	}

	@Test
	void levelRendererLeavesSolidBlocksAlone() {
		FakeLevel level = new FakeLevel(Blocks.STONE.defaultBlockState());

		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.STONE.defaultBlockState(), NEAR));
		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.STONE.defaultBlockState(), AT_LIGHT));
		// The state the caller passes in decides, not the one in the level.
		assertEquals(SKY << 20 | NEAR_SIXTEENTHS, LevelRenderer.getLightColor(level, Blocks.GLASS.defaultBlockState(), NEAR));
		assertEquals(0, level.blockStateFetches);
	}

	@Test
	void levelRendererIsUnchangedWithoutLights() {
		LightSnapshot.publish(LightSnapshot.EMPTY);
		FakeLevel level = new FakeLevel(Blocks.AIR.defaultBlockState());

		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), NEAR));
		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), AT_LIGHT));
	}

	@Test
	void levelRendererIsUnchangedOutOfReach() {
		FakeLevel level = new FakeLevel(Blocks.AIR.defaultBlockState());

		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), FAR));
		// Seven blocks away the light has just run out.
		assertEquals(VANILLA, LevelRenderer.getLightColor(level, Blocks.AIR.defaultBlockState(), new BlockPos(7, 64, 0)));
	}

	@Test
	void levelRendererNeverLowersLight() {
		FakeLevel bright = new FakeLevel(Blocks.AIR.defaultBlockState());
		bright.block = 13;

		// 11.17 dynamic against 13 from the level.
		assertEquals(SKY << 20 | 13 << 4, LevelRenderer.getLightColor(bright, Blocks.AIR.defaultBlockState(), NEAR));
		assertEquals(SKY << 20 | 14 << 4, LevelRenderer.getLightColor(bright, Blocks.AIR.defaultBlockState(), AT_LIGHT));

		bright.block = 15;
		assertEquals(SKY << 20 | 15 << 4, LevelRenderer.getLightColor(bright, Blocks.AIR.defaultBlockState(), AT_LIGHT));

		// A block that emits more than the dynamic light keeps its own value.
		assertEquals(SKY << 20 | 15 << 4, LevelRenderer.getLightColor(new FakeLevel(Blocks.TORCH.defaultBlockState()), Blocks.GLOWSTONE.defaultBlockState(), NEAR));
	}

	@Test
	void indigoLightPathIsCoveredToo() {
		FakeLevel level = new FakeLevel(Blocks.AIR.defaultBlockState());

		assertEquals(SKY << 20 | NEAR_SIXTEENTHS, AoCalculator.getLightmapCoordinates(level, Blocks.AIR.defaultBlockState(), NEAR));
		assertEquals(VANILLA, AoCalculator.getLightmapCoordinates(level, Blocks.STONE.defaultBlockState(), NEAR));
		assertEquals(VANILLA, AoCalculator.getLightmapCoordinates(level, Blocks.AIR.defaultBlockState(), FAR));

		LightSnapshot.publish(LightSnapshot.EMPTY);
		assertEquals(VANILLA, AoCalculator.getLightmapCoordinates(level, Blocks.AIR.defaultBlockState(), NEAR));
		assertTrue(hasHandler(AoCalculator.class));
	}

	@Test
	void mixinsAreAppliedToBothMinecraftTargets() throws ClassNotFoundException {
		// With "defaultRequire": 1 a hook that does not find its target fails the class load.
		Class<?> entityRenderer = Class.forName("net.minecraft.client.renderer.entity.EntityRenderer");

		assertEquals(EntityRenderer.class, entityRenderer);
		assertTrue(hasHandler(entityRenderer), "no handler merged into EntityRenderer");
		assertTrue(hasHandler(LevelRenderer.class), "no handler merged into LevelRenderer");
	}

	@Test
	void entityLightIsTheWholeLevelMaximum() {
		Entity near = entityWithEyesAt(1.5, 64.5, 1.5);
		Entity atLight = entityWithEyesAt(0.5, 64.5, 0.5);
		Entity far = entityWithEyesAt(40.5, 64.5, 0.5);

		assertEquals(11, LightHooks.entityBlockLight(3, near));
		assertEquals(13, LightHooks.entityBlockLight(13, near));
		assertEquals(14, LightHooks.entityBlockLight(0, atLight));
		assertEquals(15, LightHooks.entityBlockLight(15, atLight));
		assertEquals(3, LightHooks.entityBlockLight(3, far));
		// 14 - 2 * sqrt(5) = 9.53: nearer to 10 than to 9.
		assertEquals(10, LightHooks.entityBlockLight(0, entityWithEyesAt(1.5, 64.5, 2.5)));

		LightSnapshot.publish(LightSnapshot.EMPTY);
		assertEquals(3, LightHooks.entityBlockLight(3, near));
		assertEquals(0, LightHooks.entityBlockLight(0, atLight));
	}

	@Test
	void entityIsLitAtItsEyesNotAtTheCentreOfItsBlock() {
		// A light that is not in the middle of its block, as a walking holder's is most of the time.
		LightSnapshot.publish(new SnapshotBuilder().build(
			1, new double[] {0.9}, new double[] {64.9}, new double[] {0.9}, new int[] {14}, 2.0
		));

		// The centre of the block is 0.69 blocks from the light: 12.6, which is level 13.
		assertEquals(13, PackedLight.roundToLevel(LightSnapshot.current().lightAt(0, 64, 0)));

		// The holder's eyes are where the light is, so it is rendered with all it emits.
		assertEquals(14, LightHooks.entityBlockLight(0, entityWithEyesAt(0.9, 64.9, 0.9)));
		// At the other side of the same block, 0.8 blocks along one axis: 12.4.
		assertEquals(12, LightHooks.entityBlockLight(0, entityWithEyesAt(0.1, 64.9, 0.9)));
		// Someone standing one block further.
		assertEquals(12, LightHooks.entityBlockLight(0, entityWithEyesAt(1.9, 64.9, 0.9)));
		// Across the border of the section, at negative coordinates.
		assertEquals(12, LightHooks.entityBlockLight(0, entityWithEyesAt(-0.1, 64.9, 0.9)));
		assertEquals(12, LightHooks.entityBlockLight(0, entityWithEyesAt(0.9, 63.9, 0.9)));
	}

	@Test
	void entityRendererHandsItsEntityToTheHook() throws ReflectiveOperationException {
		TestRenderer renderer = new TestRenderer();
		Entity holder = entityWithEyesAt(0.5, 64.5, 0.5);
		Entity bystander = entityWithEyesAt(1.5, 64.5, 1.5);

		// The handler as it was merged into the real class, called on a real renderer. The block
		// position the renderer passes along is far from the light; only the entity counts.
		Method handler = Arrays.stream(EntityRenderer.class.getDeclaredMethods())
			.filter(method -> method.getName().contains("dynamiclighting$addDynamicLight"))
			.findFirst().orElseThrow();
		handler.setAccessible(true);

		assertEquals(14, handler.invoke(renderer, 3, holder));
		assertEquals(11, handler.invoke(renderer, 3, bystander));
		assertEquals(15, handler.invoke(renderer, 15, bystander));

		// The hooked method itself runs. Without a world only its burning branch can: 15, which the
		// hook leaves alone.
		bystander.setRemainingFireTicks(100);
		assertEquals(15, renderer.blockLight(bystander, FAR));
	}

	private static boolean hasHandler(Class<?> target) {
		return Arrays.stream(target.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains("dynamiclighting$addDynamicLight"));
	}

	/**
	 * An entity that can exist without a world, placed so that its eyes are at the given point.
	 */
	private static Entity entityWithEyesAt(double x, double eyeY, double z) {
		ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, null);
		stand.setPos(x, eyeY - stand.getEyeHeight(), z);

		assertEquals(x, stand.getX());
		assertEquals(eyeY, stand.getEyeY(), 1.0e-9);
		assertEquals(z, stand.getZ());
		return stand;
	}

	/**
	 * A real entity renderer. It needs no game behind it as long as nothing is drawn.
	 */
	private static final class TestRenderer extends EntityRenderer<Entity> {
		private TestRenderer() {
			super(new EntityRendererProvider.Context(null, null, null, null, null, null, null));
		}

		private int blockLight(Entity entity, BlockPos pos) {
			return this.getBlockLightLevel(entity, pos);
		}

		@Override
		public ResourceLocation getTextureLocation(Entity entity) {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * The least a caller of {@code getLightColor} needs: one block state everywhere and fixed light.
	 */
	private static final class FakeLevel implements BlockAndTintGetter {
		private final BlockState state;
		private int block = BLOCK;
		private int blockStateFetches;

		private FakeLevel(BlockState state) {
			this.state = state;
		}

		@Override
		public int getBrightness(LightLayer layer, BlockPos pos) {
			return layer == LightLayer.SKY ? SKY : this.block;
		}

		@Override
		public float getShade(Direction direction, boolean shade) {
			return 1.0f;
		}

		@Override
		public LevelLightEngine getLightEngine() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver resolver) {
			return -1;
		}

		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			this.blockStateFetches++;
			return this.state;
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return Fluids.EMPTY.defaultFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		@Override
		public int getMinBuildHeight() {
			return -64;
		}
	}
}
