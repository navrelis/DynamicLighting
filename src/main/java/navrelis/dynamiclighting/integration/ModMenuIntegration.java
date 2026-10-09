package navrelis.dynamiclighting.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import navrelis.dynamiclighting.gui.ConfigScreen;

/**
 * Mod Menu entry point: the "Configure" button of the mod opens the options screen.
 * Only loaded by Mod Menu itself, so the mod runs without it.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
