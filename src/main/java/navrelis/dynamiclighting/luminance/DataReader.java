package navrelis.dynamiclighting.luminance;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import navrelis.dynamiclighting.DynamicLighting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the data files of all namespaces and packs into JSON trees. Runs on a reload worker
 * thread and needs no registries. A file that cannot be read never stops the others.
 */
final class DataReader {
	/** Folder below {@code assets/<namespace>/}; the same one existing mods already ship. */
	static final String ROOT = "dynamiclights";

	private DataReader() {
	}

	/**
	 * Reads every item and entity file the resource manager offers. The manager already returns
	 * one resource per id, taken from the highest-priority pack.
	 *
	 * @return an immutable list, safe to hand to another thread
	 */
	static List<RawFile> readAll(ResourceManager manager) {
		List<RawFile> files = new ArrayList<>();

		for (DataKind kind : DataKind.values()) {
			Map<ResourceLocation, Resource> found;

			try {
				found = manager.listResources(ROOT + "/" + kind.folder(), id -> id.getPath().endsWith(".json"));
			} catch (RuntimeException e) {
				DynamicLighting.LOGGER.warn("Could not list the {} light data files: {}", kind.folder(), e.toString());
				continue;
			}

			for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
				files.add(read(entry.getKey(), kind, entry.getValue()));
			}
		}

		return List.copyOf(files);
	}

	private static RawFile read(ResourceLocation id, DataKind kind, Resource resource) {
		try (Reader reader = resource.openAsReader()) {
			return parse(id, kind, reader);
		} catch (IOException | RuntimeException e) {
			return RawFile.failed(id, kind, SkipReason.UNREADABLE, e.toString());
		}
	}

	static RawFile parse(ResourceLocation id, DataKind kind, String text) {
		return parse(id, kind, new StringReader(text));
	}

	/**
	 * Parses one file. Every failure ends up in the returned value, nothing is thrown.
	 */
	static RawFile parse(ResourceLocation id, DataKind kind, Reader reader) {
		try {
			return RawFile.of(id, kind, JsonParser.parseReader(reader));
		} catch (JsonIOException e) {
			return RawFile.failed(id, kind, SkipReason.UNREADABLE, e.toString());
		} catch (RuntimeException | StackOverflowError e) {
			// Gson reports bad syntax with several exception types and overflows the stack on absurd nesting.
			return RawFile.failed(id, kind, SkipReason.SYNTAX, e.toString());
		}
	}
}
