package navrelis.dynamiclighting.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import navrelis.dynamiclighting.config.FuseMode;
import navrelis.dynamiclighting.config.LightRange;
import navrelis.dynamiclighting.config.Mode;

/**
 * Translation keys used by the screen, the key binding and the Sodium page. Strings only, so tests can read them.
 */
public final class Texts {
	public static final String TITLE = "dynamiclighting.screen.title";
	public static final String RESET = "dynamiclighting.screen.reset";

	public static final String OPTION_MODE = "dynamiclighting.option.mode";
	public static final String OPTION_RANGE = "dynamiclighting.option.range";
	public static final String OPTION_SELF_LIGHT = "dynamiclighting.option.self_light";
	public static final String OPTION_ENTITY_LIGHTS = "dynamiclighting.option.entity_lights";
	public static final String OPTION_WATER_SENSITIVE = "dynamiclighting.option.water_sensitive";
	public static final String OPTION_GLOWING_ENTITIES = "dynamiclighting.option.glowing_entities";
	public static final String OPTION_CREEPER = "dynamiclighting.option.creeper";
	public static final String OPTION_TNT = "dynamiclighting.option.tnt";

	public static final String KEY_TOGGLE_SELF_LIGHT = "key.dynamiclighting.toggle_self_light";
	public static final String KEY_CATEGORY = "key.categories.dynamiclighting";

	public static final String MESSAGE_SELF_LIGHT_ON = "dynamiclighting.message.self_light.on";
	public static final String MESSAGE_SELF_LIGHT_OFF = "dynamiclighting.message.self_light.off";

	public static final String SODIUM_PAGE = "dynamiclighting.sodium.page";
	public static final String SODIUM_GROUP_GENERAL = "dynamiclighting.sodium.group.general";
	public static final String SODIUM_GROUP_SPECIAL = "dynamiclighting.sodium.group.special";

	/**
	 * Every option name key, in the order the screen shows them.
	 */
	public static final List<String> OPTIONS = List.of(
		OPTION_MODE, OPTION_RANGE, OPTION_SELF_LIGHT, OPTION_ENTITY_LIGHTS,
		OPTION_WATER_SENSITIVE, OPTION_GLOWING_ENTITIES, OPTION_CREEPER, OPTION_TNT
	);

	private Texts() {
	}

	public static String tooltip(String optionKey) {
		return optionKey + ".tooltip";
	}

	public static String value(Mode mode) {
		return "dynamiclighting.value.mode." + lower(mode);
	}

	public static String value(LightRange range) {
		return "dynamiclighting.value.range." + lower(range);
	}

	public static String value(FuseMode fuse) {
		return "dynamiclighting.value.fuse." + lower(fuse);
	}

	/**
	 * Every translation key the mod's own code looks up. The language files must define all of them.
	 */
	public static List<String> allKeys() {
		List<String> keys = new ArrayList<>(List.of(
			TITLE, RESET, KEY_TOGGLE_SELF_LIGHT, KEY_CATEGORY, MESSAGE_SELF_LIGHT_ON, MESSAGE_SELF_LIGHT_OFF,
			SODIUM_PAGE, SODIUM_GROUP_GENERAL, SODIUM_GROUP_SPECIAL
		));
		for (String option : OPTIONS) {
			keys.add(option);
			keys.add(tooltip(option));
		}
		for (Mode mode : Mode.values()) {
			keys.add(value(mode));
		}
		for (LightRange range : LightRange.values()) {
			keys.add(value(range));
		}
		for (FuseMode fuse : FuseMode.values()) {
			keys.add(value(fuse));
		}
		return keys;
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
