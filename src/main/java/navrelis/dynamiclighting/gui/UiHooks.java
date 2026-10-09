package navrelis.dynamiclighting.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import navrelis.dynamiclighting.config.ConfigManager;
import navrelis.dynamiclighting.config.Options;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/**
 * The key binding that toggles the own light and the {@code /dynamiclighting} command that opens the options screen.
 * Both are driven from the end of the client tick on the game thread.
 */
public final class UiHooks {
	public static final String COMMAND = "dynamiclighting";

	private static KeyMapping toggleSelfLight;
	private static boolean screenRequested;

	private UiHooks() {
	}

	/**
	 * Registers the key binding (unbound by default), the tick handler and the command.
	 * Must run while the client starts, before the game options are loaded.
	 */
	public static void register() {
		toggleSelfLight = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			Texts.KEY_TOGGLE_SELF_LIGHT, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), Texts.KEY_CATEGORY
		));
		ClientTickEvents.END_CLIENT_TICK.register(UiHooks::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommandManager.literal(COMMAND).executes(context -> {
				screenRequested = true;
				return Command.SINGLE_SUCCESS;
			}))
		);
	}

	private static void tick(Minecraft client) {
		boolean pressed = false;
		while (toggleSelfLight.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.level != null) {
			toggleSelfLight(client);
		}

		if (screenRequested) {
			// The chat screen closes itself right after running a command and would discard a screen opened by it.
			if (client.screen instanceof ChatScreen) {
				return;
			}
			screenRequested = false;
			if (client.level != null) {
				client.setScreen(new ConfigScreen(client.screen));
			}
		}
	}

	private static void toggleSelfLight(Minecraft client) {
		boolean enabled = !Options.get().selfLight();
		ConfigManager.apply(Options.get().withSelfLight(enabled));
		client.gui.setOverlayMessage(
			Component.translatable(enabled ? Texts.MESSAGE_SELF_LIGHT_ON : Texts.MESSAGE_SELF_LIGHT_OFF), false
		);
	}
}
