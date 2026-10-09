package navrelis.dynamiclighting.luminance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import navrelis.dynamiclighting.DynamicLighting;
import navrelis.dynamiclighting.luminance.CompiledData.Skip;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Brings the data files from the resource packs into {@link Luminance}.
 * <p>
 * Reading happens in the prepare stage of a resource reload, on a worker thread, and produces one
 * immutable list. Everything after that runs on the client thread: the list is kept, and it is
 * decoded whenever registries and tags are known, which is at the end of a reload while in a
 * world and whenever the client receives tags (joining a world, a server-side reload).
 */
final class LightDataLoader implements SimpleResourceReloadListener<List<RawFile>> {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(DynamicLighting.MOD_ID, "light_data");

	/** The files of the last reload. Client thread only. */
	private List<RawFile> files = List.of();
	/** Files already warned about since the last reload, so that joining a world again stays quiet. */
	private final Set<ResourceLocation> warned = new HashSet<>();

	private LightDataLoader() {
	}

	static void register() {
		LightDataLoader loader = new LightDataLoader();
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(loader);
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			if (client) {
				loader.onTagsLoaded(registries);
			}
		});
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	@Override
	public CompletableFuture<List<RawFile>> load(ResourceManager manager, ProfilerFiller profiler, Executor executor) {
		return CompletableFuture.supplyAsync(() -> DataReader.readAll(manager), executor);
	}

	@Override
	public CompletableFuture<Void> apply(List<RawFile> data, ResourceManager manager, ProfilerFiller profiler, Executor executor) {
		return CompletableFuture.runAsync(() -> this.onReloaded(data), executor);
	}

	private void onReloaded(List<RawFile> data) {
		this.files = data;
		this.warned.clear();

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level != null) {
			this.apply(minecraft.level.registryAccess());
		}
	}

	private void onTagsLoaded(HolderLookup.Provider registries) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.isSameThread()) {
			this.apply(registries);
		} else {
			minecraft.execute(() -> this.apply(registries));
		}
	}

	private void apply(HolderLookup.Provider registries) {
		try {
			CompiledData data = DataCompiler.compile(this.files, registries, FabricLoader.getInstance()::isModLoaded);
			Luminance.install(data);
			this.report(data);
		} catch (RuntimeException | StackOverflowError | LinkageError e) {
			// Nothing here may fail a resource reload or a world join.
			DynamicLighting.LOGGER.error("Could not apply the light data, the previous rules stay in use", e);
		}
	}

	private void report(CompiledData data) {
		for (Skip skip : data.skips()) {
			if (skip.quiet()) {
				DynamicLighting.LOGGER.debug("Light data file {} is not used: {}", skip.file(), skip.detail());
			} else if (this.warned.add(skip.file())) {
				DynamicLighting.LOGGER.warn("Skipped light data file {}: {}", skip.file(), skip.detail());
			}
		}

		DynamicLighting.LOGGER.info(
			"Light data applied: {} item rules and {} entity rules active, {} files skipped ({} with problems).",
			data.itemRules().size(), data.entityRules().size(), data.skips().size(), data.warnings()
		);
	}
}
