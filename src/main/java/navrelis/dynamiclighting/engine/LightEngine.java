package navrelis.dynamiclighting.engine;

import navrelis.dynamiclighting.DynamicLighting;
import navrelis.dynamiclighting.config.Mode;
import navrelis.dynamiclighting.config.Options;
import navrelis.dynamiclighting.luminance.Luminance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the entities that emit light, once per client tick, and hands them to the {@link LightTracker}.
 * <p>
 * This is the only class of the engine that touches the game. Everything here runs on the client
 * thread; other threads only ever see the snapshot the tracker publishes.
 */
public final class LightEngine {
	private static final boolean DEBUG = Boolean.getBoolean("dynamiclighting.debug");
	private static final int DEBUG_INTERVAL = 100;
	/**
	 * Vanilla's section grid can sit up to one section off-centre and follows the camera one frame
	 * late.
	 */
	private static final int GRID_SLACK = 2;

	private static final LightTracker TRACKER = new LightTracker();
	private static final RebuildQueue.SectionMarker MARKER = LightEngine::markDirty;

	private static ClientLevel trackedLevel;
	private static int tick;
	private static int gridCentreX;
	private static int gridCentreZ;
	private static int gridRadius;
	private static int markedDirty;

	private LightEngine() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LightEngine::onEndTick);
		// The level can be replaced between two ticks. Frames rendered in that gap must not mesh
		// the new level with the lights of the old one.
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> syncLevel(level));
	}

	private static void onEndTick(Minecraft client) {
		ClientLevel level = client.level;
		syncLevel(level);

		if (level == null) {
			return;
		}

		Options options = Options.get();
		Mode mode = options.mode();
		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();

		if (!mode.enabled()) {
			TRACKER.switchOff();
			flush(client, camera, cameraPos, Integer.MAX_VALUE);
			return;
		}

		tick++;
		TRACKER.setFalloff(options.range().falloffPerBlock());
		removeMissing(level);
		evaluate(level, mode, cameraPos);

		// Publish first: a section marked dirty must be meshed from the new lights.
		TRACKER.publishIfChanged();
		flush(client, camera, cameraPos, Tuning.budget(mode));

		if (DEBUG && tick % DEBUG_INTERVAL == 0) {
			DynamicLighting.LOGGER.info(
				"sources={} litSections={} markedDirty={} snapshots={} queued={}",
				TRACKER.size(), LightSnapshot.current().sectionCount(), markedDirty, TRACKER.takePublished(), TRACKER.queue().size()
			);
			markedDirty = 0;
		}
	}

	/**
	 * Drops everything when the level is not the one that was tracked, including on disconnect.
	 */
	private static void syncLevel(ClientLevel level) {
		if (level == trackedLevel) {
			return;
		}

		trackedLevel = level;
		TRACKER.reset();

		if (level != null) {
			TRACKER.setSectionBounds(level.getMinSection(), level.getMaxSection() - 1);
		}
	}

	/**
	 * Removes the sources whose entity left the level.
	 */
	private static void removeMissing(ClientLevel level) {
		for (int slot = TRACKER.size() - 1; slot >= 0; slot--) {
			if (level.getEntity(TRACKER.idAt(slot)) == null) {
				TRACKER.removeAt(slot);
			}
		}
	}

	private static void evaluate(ClientLevel level, Mode mode, Vec3 cameraPos) {
		double falloff = TRACKER.falloff();

		for (Entity entity : level.entitiesForRendering()) {
			double x = entity.getX();
			double y = entity.getEyeY();
			double z = entity.getZ();
			double dx = x - cameraPos.x;
			double dy = y - cameraPos.y;
			double dz = z - cameraPos.z;
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			int id = entity.getId();

			if (!Tuning.isDue(tick, id, Tuning.interval(mode, distanceSquared))) {
				continue;
			}

			int luminance = Luminance.ofEntity(entity);
			double threshold = luminance > 0
				? Tuning.moveThreshold(falloff, mode, Math.sqrt(distanceSquared), LightHooks.SODIUM_LOADED)
				: 0.0;
			TRACKER.update(id, x, y, z, luminance, threshold);
		}
	}

	private static void flush(Minecraft client, Camera camera, Vec3 cameraPos, int budget) {
		RebuildQueue queue = TRACKER.queue();

		if (queue.size() == 0) {
			return;
		}

		gridCentreX = SectionPos.posToSectionCoord(cameraPos.x);
		gridCentreZ = SectionPos.posToSectionCoord(cameraPos.z);
		// Vanilla wraps a section outside its grid onto an unrelated one inside it, which would then
		// be rebuilt for nothing. Sodium ignores sections it does not know.
		gridRadius = LightHooks.SODIUM_LOADED || !camera.isInitialized()
			? Integer.MAX_VALUE
			: client.options.getEffectiveRenderDistance() + GRID_SLACK;
		queue.flush(gridCentreX, SectionPos.posToSectionCoord(cameraPos.y), gridCentreZ, budget, MARKER);
	}

	private static void markDirty(int sectionX, int sectionY, int sectionZ) {
		if (Math.abs((long) sectionX - gridCentreX) > gridRadius || Math.abs((long) sectionZ - gridCentreZ) > gridRadius) {
			return;
		}

		Minecraft.getInstance().levelRenderer.setSectionDirty(sectionX, sectionY, sectionZ);
		markedDirty++;
	}
}
