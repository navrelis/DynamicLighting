package navrelis.dynamiclighting;

import navrelis.dynamiclighting.config.ConfigManager;
import navrelis.dynamiclighting.engine.LightEngine;
import navrelis.dynamiclighting.luminance.Luminance;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Feature wiring is added later.
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
