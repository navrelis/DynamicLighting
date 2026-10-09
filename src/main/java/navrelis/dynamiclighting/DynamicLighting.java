package navrelis.dynamiclighting;

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
		LOGGER.info("Dynamic Lighting initialised.");
	}
}
