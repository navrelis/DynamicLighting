package navrelis.dynamiclighting.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
	private static final Options CUSTOM = new Options(
		Mode.FAST, LightRange.LONG, true, false, true, false, FuseMode.OFF, FuseMode.FANCY,
		Set.of(ResourceLocation.parse("minecraft:bat"))
	);

	@TempDir
	Path folder;

	private Path file() {
		return folder.resolve(ConfigManager.FILE_NAME);
	}

	private Path broken() {
		return folder.resolve(ConfigManager.FILE_NAME + ConfigManager.BROKEN_SUFFIX);
	}

	@Test
	void firstStartWritesTheDefaultsAndReturnsThem() throws IOException {
		assertEquals(Options.DEFAULT, ConfigManager.load(file()));
		assertTrue(Files.isRegularFile(file()));
		assertEquals(Options.DEFAULT, OptionsCodec.parse(Files.readString(file())).orElseThrow().options());
		assertTrue(Files.readString(file()).contains("\"disabled_entity_types\""));
	}

	@Test
	void writtenOptionsAreReadBack() throws IOException {
		ConfigManager.write(file(), CUSTOM);
		assertEquals(CUSTOM, ConfigManager.load(file()));
	}

	@Test
	void writingReplacesAnExistingFileAndLeavesNoTempFile() throws IOException {
		ConfigManager.write(file(), Options.DEFAULT);
		ConfigManager.write(file(), CUSTOM);
		assertEquals(CUSTOM, ConfigManager.load(file()));
		try (Stream<Path> files = Files.list(folder)) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void writingCreatesMissingFolders() throws IOException {
		Path nested = folder.resolve("a").resolve("b").resolve(ConfigManager.FILE_NAME);
		ConfigManager.write(nested, CUSTOM);
		assertEquals(CUSTOM, ConfigManager.load(nested));
	}

	@Test
	void invalidJsonIsRenamedAndDefaultsAreWritten() throws IOException {
		Files.writeString(file(), "{ \"mode\": \"fancy\", ");
		assertEquals(Options.DEFAULT, ConfigManager.load(file()));
		assertEquals("{ \"mode\": \"fancy\", ", Files.readString(broken()));
		assertEquals(Options.DEFAULT, OptionsCodec.parse(Files.readString(file())).orElseThrow().options());
	}

	@Test
	void aFileThatIsNotAnObjectIsRenamed() throws IOException {
		Files.writeString(file(), "[\"fancy\"]");
		assertEquals(Options.DEFAULT, ConfigManager.load(file()));
		assertEquals("[\"fancy\"]", Files.readString(broken()));
	}

	@Test
	void anOlderBrokenFileIsReplaced() throws IOException {
		Files.writeString(broken(), "old");
		Files.writeString(file(), "garbage");
		ConfigManager.load(file());
		assertEquals("garbage", Files.readString(broken()));
	}

	@Test
	void textThatIsNotUtf8IsRenamed() throws IOException {
		Files.write(file(), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
		assertEquals(Options.DEFAULT, ConfigManager.load(file()));
		assertTrue(Files.exists(broken()));
		assertTrue(Files.isRegularFile(file()));
	}

	@Test
	void validFileWithBadKeysKeepsTheGoodOnesAndIsNotRenamed() throws IOException {
		Files.writeString(file(), """
			{
			  "mode": "fastest",
			  "range": 12,
			  "self_light": false,
			  "tnt": "FANCY"
			}
			""", StandardCharsets.UTF_8);
		Options loaded = ConfigManager.load(file());
		assertEquals(Mode.FASTEST, loaded.mode());
		assertEquals(Options.DEFAULT.range(), loaded.range());
		assertFalse(loaded.selfLight());
		assertEquals(FuseMode.FANCY, loaded.tnt());
		assertFalse(Files.exists(broken()));
	}
}
