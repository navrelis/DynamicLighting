package navrelis.dynamiclighting.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import navrelis.dynamiclighting.config.ConfigManager;
import navrelis.dynamiclighting.config.FuseMode;
import navrelis.dynamiclighting.config.LightRange;
import navrelis.dynamiclighting.config.Mode;
import navrelis.dynamiclighting.config.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The options screen, built like the vanilla option screens: scrolling {@link net.minecraft.client.gui.components.OptionsList},
 * cycle buttons with tooltips, a footer with buttons.
 * <p>
 * A click applies at once through {@link Options#set}; the file is written when the screen closes.
 * The file-only list of disabled entity types is never touched here.
 */
public class ConfigScreen extends OptionsSubScreen {
	private final Options openedWith = Options.get();
	private final List<Row<?>> rows = new ArrayList<>();

	public ConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(Texts.TITLE));
	}

	@Override
	protected void addOptions() {
		rows.clear();
		Options now = Options.get();
		list.addSmall(
			cycle(Texts.OPTION_MODE, List.of(Mode.values()), Texts::value, Options::mode, Options::withMode, now),
			cycle(Texts.OPTION_RANGE, List.of(LightRange.values()), Texts::value, Options::range, Options::withRange, now)
		);
		list.addSmall(
			toggle(Texts.OPTION_SELF_LIGHT, Options::selfLight, Options::withSelfLight, now),
			toggle(Texts.OPTION_ENTITY_LIGHTS, Options::entityLights, Options::withEntityLights, now)
		);
		list.addSmall(
			toggle(Texts.OPTION_WATER_SENSITIVE, Options::waterSensitive, Options::withWaterSensitive, now),
			toggle(Texts.OPTION_GLOWING_ENTITIES, Options::glowingEntities, Options::withGlowingEntities, now)
		);
		list.addSmall(
			cycle(Texts.OPTION_CREEPER, List.of(FuseMode.values()), Texts::value, Options::creeper, Options::withCreeper, now),
			cycle(Texts.OPTION_TNT, List.of(FuseMode.values()), Texts::value, Options::tnt, Options::withTnt, now)
		);
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(Texts.RESET), button -> resetToDefaults()).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());
	}

	/**
	 * Writes the file if anything changed. Vanilla's options.txt is not touched.
	 */
	@Override
	public void removed() {
		if (!Options.get().equals(openedWith)) {
			ConfigManager.save();
		}
	}

	private void resetToDefaults() {
		Options.set(Options.get().withScreenDefaults());
		for (Row<?> row : rows) {
			row.refresh();
		}
	}

	private CycleButton<Boolean> toggle(
		String key, Function<Options, Boolean> read, BiFunction<Options, Boolean, Options> write, Options now
	) {
		CycleButton<Boolean> button = CycleButton.onOffBuilder(read.apply(now))
			.withTooltip(OptionInstance.cachedConstantTooltip(Component.translatable(Texts.tooltip(key))))
			.create(0, 0, 150, 20, Component.translatable(key), (self, value) -> Options.set(write.apply(Options.get(), value)));
		rows.add(new Row<>(button, read));
		return button;
	}

	private <T> CycleButton<T> cycle(
		String key, List<T> values, Function<T, String> valueKey,
		Function<Options, T> read, BiFunction<Options, T, Options> write, Options now
	) {
		CycleButton<T> button = CycleButton.<T>builder(value -> Component.translatable(valueKey.apply(value)))
			.withValues(values)
			.withInitialValue(read.apply(now))
			.withTooltip(OptionInstance.cachedConstantTooltip(Component.translatable(Texts.tooltip(key))))
			.create(0, 0, 150, 20, Component.translatable(key), (self, value) -> Options.set(write.apply(Options.get(), value)));
		rows.add(new Row<>(button, read));
		return button;
	}

	/**
	 * A button together with the way to read its value from the options, to show new values after a reset.
	 */
	private record Row<T>(CycleButton<T> button, Function<Options, T> read) {
		void refresh() {
			button.setValue(read.apply(Options.get()));
		}
	}
}
