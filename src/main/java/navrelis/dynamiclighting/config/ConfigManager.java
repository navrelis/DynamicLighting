package navrelis.dynamiclighting.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import navrelis.dynamiclighting.DynamicLighting;
import navrelis.dynamiclighting.gui.UiHooks;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads and saves the config file {@code dynamiclighting.json} and registers the UI hooks (key binding, command).
 * <p>
 * Nothing here throws out to the caller: a broken or unreadable file leads to defaults and a log line.
 */
public final class ConfigManager {
	public static final String FILE_NAME = "dynamiclighting.json";
	static final String BROKEN_SUFFIX = ".broken";
	static final String TEMP_SUFFIX = ".tmp";

	private ConfigManager() {
	}

	/**
	 * Reads the config file into {@link Options}, writes it if it does not exist yet, and registers the UI hooks.
	 */
	public static void init() {
		try {
			Options.set(load(configFile()));
		} catch (RuntimeException e) {
			DynamicLighting.LOGGER.error("Could not load {}, using defaults.", FILE_NAME, e);
			Options.set(Options.DEFAULT);
		}
		try {
			UiHooks.register();
		} catch (RuntimeException e) {
			DynamicLighting.LOGGER.error("Could not register the key binding and the command.", e);
		}
	}

	/**
	 * Writes the current options to the config file on the calling thread.
	 */
	public static void save() {
		try {
			write(configFile(), Options.get());
		} catch (IOException | RuntimeException e) {
			DynamicLighting.LOGGER.warn("Could not save {}.", FILE_NAME, e);
		}
	}

	/**
	 * Makes the given options current and saves them.
	 */
	public static void apply(Options options) {
		Options.set(options);
		save();
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * Reads the file, falling back per key. A missing file is created with the defaults.
	 * A file that is not a JSON object is renamed to {@code <name>.broken} and replaced by the defaults.
	 */
	static Options load(Path file) {
		if (Files.notExists(file)) {
			saveQuietly(file, Options.DEFAULT);
			return Options.DEFAULT;
		}

		String text;
		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (CharacterCodingException e) {
			quarantine(file, "it is not valid UTF-8 text");
			saveQuietly(file, Options.DEFAULT);
			return Options.DEFAULT;
		} catch (IOException e) {
			DynamicLighting.LOGGER.warn("Could not read {}, using defaults.", file, e);
			return Options.DEFAULT;
		}

		Optional<OptionsCodec.Result> parsed = OptionsCodec.parse(text);
		if (parsed.isEmpty()) {
			quarantine(file, "it is not a valid JSON object");
			saveQuietly(file, Options.DEFAULT);
			return Options.DEFAULT;
		}
		for (String warning : parsed.get().warnings()) {
			DynamicLighting.LOGGER.warn("Config {}: {}", FILE_NAME, warning);
		}
		return parsed.get().options();
	}

	/**
	 * Writes the file through a temporary file in the same folder, then moves it into place, atomically where supported.
	 */
	static synchronized void write(Path file, Options options) throws IOException {
		Path folder = file.toAbsolutePath().getParent();
		Files.createDirectories(folder);
		Path temp = folder.resolve(file.getFileName() + TEMP_SUFFIX);
		try {
			ByteBuffer data = ByteBuffer.wrap(OptionsCodec.toText(options).getBytes(StandardCharsets.UTF_8));
			try (FileChannel channel = FileChannel.open(
				temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
			)) {
				while (data.hasRemaining()) {
					channel.write(data);
				}
				channel.force(true);
			}
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw e;
		}
	}

	private static void saveQuietly(Path file, Options options) {
		try {
			write(file, options);
		} catch (IOException | RuntimeException e) {
			DynamicLighting.LOGGER.warn("Could not write {}.", file, e);
		}
	}

	private static void quarantine(Path file, String reason) {
		Path broken = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX);
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			DynamicLighting.LOGGER.warn("{} was ignored because {}; it was renamed to {}. Using defaults.", file.getFileName(), reason, broken.getFileName());
		} catch (IOException e) {
			DynamicLighting.LOGGER.warn("{} was ignored because {}, and it could not be renamed. Using defaults.", file.getFileName(), reason, e);
		}
	}
}
