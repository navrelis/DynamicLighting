package navrelis.dynamiclighting;

import navrelis.dynamiclighting.config.ConfigManager;
import navrelis.dynamiclighting.engine.LightEngine;
import navrelis.dynamiclighting.luminance.Luminance;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Starts the three parts of the mod in the order they depend on each other:
 * the config (options, key binding and command), the light data (what emits) and the engine
 * (where the light goes).
 */
public class DynamicLighting implements ClientModInitializer {
	public static final String MOD_ID = "dynamiclighting";

	public static final Logger LOGGER = LoggerFactory.getLogger("Dynamic Lighting");

	@Override
	public void onInitializeClient() {
		ConfigManager.init();
		Luminance.init();
		LightEngine.init();
		LOGGER.info("Dynamic Lighting initialised.");
	}
}
