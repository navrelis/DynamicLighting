package navrelis.dynamiclighting.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import navrelis.dynamiclighting.gui.Texts;
import org.junit.jupiter.api.Test;

class LangFilesTest {
	private static JsonObject load(String language) throws IOException {
		String path = "/assets/dynamiclighting/lang/" + language + ".json";
		try (InputStream stream = LangFilesTest.class.getResourceAsStream(path)) {
			assertNotNull(stream, path);
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}

	@Test
	void bothLanguagesHaveExactlyTheSameKeys() throws IOException {
		Set<String> english = new TreeSet<>(load("en_us").keySet());
		Set<String> german = new TreeSet<>(load("de_de").keySet());
		assertEquals(english, german);
	}

	@Test
	void everyKeyTheCodeUsesIsTranslatedInBothLanguages() throws IOException {
		for (String language : new String[] {"en_us", "de_de"}) {
			JsonObject texts = load(language);
			for (String key : Texts.allKeys()) {
				assertTrue(texts.has(key), language + " is missing " + key);
			}
		}
	}

	@Test
	void noKeyIsUnusedOrDuplicated() throws IOException {
		Set<String> used = new HashSet<>(Texts.allKeys());
		assertEquals(Texts.allKeys().size(), used.size(), "Texts lists a key twice");
		assertEquals(used, load("en_us").keySet());
	}

	@Test
	void everyTextIsFilledIn() throws IOException {
		for (String language : new String[] {"en_us", "de_de"}) {
			for (Map.Entry<String, JsonElement> entry : load(language).entrySet()) {
				assertTrue(entry.getValue().getAsJsonPrimitive().isString(), language + " " + entry.getKey());
				assertFalse(entry.getValue().getAsString().isBlank(), language + " " + entry.getKey());
			}
		}
	}

	@Test
	void everyOptionHasAValueTextPerConstant() {
		assertEquals(8, Texts.OPTIONS.size());
		for (Mode mode : Mode.values()) {
			assertTrue(Texts.allKeys().contains(Texts.value(mode)));
		}
		for (LightRange range : LightRange.values()) {
			assertTrue(Texts.allKeys().contains(Texts.value(range)));
		}
		for (FuseMode fuse : FuseMode.values()) {
			assertTrue(Texts.allKeys().contains(Texts.value(fuse)));
		}
	}
}
