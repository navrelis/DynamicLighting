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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
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
		assertEquals(11, LightHooks.entityBlockLight(3, NEAR));
		assertEquals(13, LightHooks.entityBlockLight(13, NEAR));
		assertEquals(14, LightHooks.entityBlockLight(0, AT_LIGHT));
		assertEquals(15, LightHooks.entityBlockLight(15, AT_LIGHT));
		assertEquals(3, LightHooks.entityBlockLight(3, FAR));
		// 14 - 2 * sqrt(5) = 9.53: nearer to 10 than to 9.
		assertEquals(10, LightHooks.entityBlockLight(0, new BlockPos(1, 64, 2)));

		LightSnapshot.publish(LightSnapshot.EMPTY);
		assertEquals(3, LightHooks.entityBlockLight(3, NEAR));
		assertEquals(0, LightHooks.entityBlockLight(0, AT_LIGHT));
	}

	private static boolean hasHandler(Class<?> target) {
		return Arrays.stream(target.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains("dynamiclighting$addDynamicLight"));
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
