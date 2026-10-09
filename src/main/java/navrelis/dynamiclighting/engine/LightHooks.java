package navrelis.dynamiclighting.engine;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the light mixins call. Runs on chunk meshing threads as well as on the client thread, so it
 * only reads the published snapshot.
 */
public final class LightHooks {
	/**
	 * Sodium keeps four bits of block light per position and cuts the rest off. With Sodium the
	 * dynamic value is therefore rounded to the nearest whole level; without it sixteenths are kept.
	 */
	static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

	private LightHooks() {
	}

	/**
	 * Raises the block light part of a packed light value to the dynamic light at {@code pos}.
	 *
	 * @param packed the value Minecraft computed for {@code pos}
	 * @param state  the block state at {@code pos}, as the caller already has it
	 */
	public static int lightColor(int packed, BlockAndTintGetter level, BlockState state, BlockPos pos) {
		if (PackedLight.block(packed) >= PackedLight.FULL_BLOCK_LIGHT) {
			return packed;
		}

		LightSnapshot snapshot = LightSnapshot.current();

		if (snapshot == LightSnapshot.EMPTY) {
			return packed;
		}

		int merged = PackedLight.merge(packed, snapshot.lightAt(pos.getX(), pos.getY(), pos.getZ()), SODIUM_LOADED);

		// Light raised inside a solid block would break ambient occlusion. Asked last: it is the
		// only step that needs the block state, and few positions get this far.
		if (merged == packed || state.isSolidRender(level, pos)) {
			return packed;
		}

		return merged;
	}

	/**
	 * Raises the block light level an entity is rendered with to the dynamic light at its eyes.
	 * <p>
	 * That is the point the engine places the entity's own light at, so an entity that emits light is
	 * rendered with what it emits, and a held light does not flicker as its holder walks through the
	 * block grid.
	 */
	public static int entityBlockLight(int level, Entity entity) {
		if (level >= LightSnapshot.MAX_LIGHT) {
			return level;
		}

		LightSnapshot snapshot = LightSnapshot.current();

		if (snapshot == LightSnapshot.EMPTY) {
			return level;
		}

		return Math.max(level, PackedLight.roundToLevel(snapshot.lightAtPoint(entity.getX(), entity.getEyeY(), entity.getZ())));
	}
}
