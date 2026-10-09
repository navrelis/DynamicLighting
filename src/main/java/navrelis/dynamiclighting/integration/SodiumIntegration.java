package navrelis.dynamiclighting.integration;

import java.util.function.BiFunction;
import java.util.function.Function;

import navrelis.dynamiclighting.DynamicLighting;
import navrelis.dynamiclighting.config.ConfigManager;
import navrelis.dynamiclighting.config.FuseMode;
import navrelis.dynamiclighting.config.LightRange;
import navrelis.dynamiclighting.config.Mode;
import navrelis.dynamiclighting.config.Options;
import navrelis.dynamiclighting.gui.Texts;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers a "Dynamic Lighting" page with native Sodium options. Declared as the {@code sodium:config_api_user}
 * entry point in {@code fabric.mod.json}; Sodium loads it, so no other class may reference this one.
 * <p>
 * Sodium keeps a pending value per option and calls the binding's save function when the player presses Apply.
 * The save functions only change {@link Options}; the file is written once afterwards by the shared storage handler.
 */
public class SodiumIntegration implements ConfigEntryPoint {
	/**
	 * One instance for all options: Sodium collects handlers in a set and calls each one once per Apply.
	 */
	private static final StorageEventHandler STORAGE = ConfigManager::save;

	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		OptionGroupBuilder general = builder.createOptionGroup()
			.setName(Component.translatable(Texts.SODIUM_GROUP_GENERAL))
			.addOption(mode(builder))
			.addOption(range(builder))
			.addOption(toggle(builder, "self_light", Texts.OPTION_SELF_LIGHT, Options::selfLight, Options::withSelfLight))
			.addOption(toggle(builder, "entity_lights", Texts.OPTION_ENTITY_LIGHTS, Options::entityLights, Options::withEntityLights))
			.addOption(toggle(builder, "water_sensitive", Texts.OPTION_WATER_SENSITIVE, Options::waterSensitive, Options::withWaterSensitive));

		OptionGroupBuilder special = builder.createOptionGroup()
			.setName(Component.translatable(Texts.SODIUM_GROUP_SPECIAL))
			.addOption(toggle(builder, "glowing_entities", Texts.OPTION_GLOWING_ENTITIES, Options::glowingEntities, Options::withGlowingEntities))
			.addOption(fuse(builder, "creeper", Texts.OPTION_CREEPER, Options::creeper, Options::withCreeper))
			.addOption(fuse(builder, "tnt", Texts.OPTION_TNT, Options::tnt, Options::withTnt));

		builder.registerOwnModOptions()
			.addPage(builder.createOptionPage()
				.setName(Component.translatable(Texts.SODIUM_PAGE))
				.addOptionGroup(general)
				.addOptionGroup(special));
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(DynamicLighting.MOD_ID, path);
	}

	private static EnumOptionBuilder<Mode> mode(ConfigBuilder builder) {
		return builder.createEnumOption(id("mode"), Mode.class)
			.setName(Component.translatable(Texts.OPTION_MODE))
			.setTooltip(Component.translatable(Texts.tooltip(Texts.OPTION_MODE)))
			.setElementNameProvider(value -> Component.translatable(Texts.value(value)))
			.setImpact(OptionImpact.VARIES)
			.setStorageHandler(STORAGE)
			.setDefaultValue(Options.DEFAULT.mode())
			.setBinding(value -> Options.set(Options.get().withMode(value)), () -> Options.get().mode());
	}

	private static EnumOptionBuilder<LightRange> range(ConfigBuilder builder) {
		return builder.createEnumOption(id("range"), LightRange.class)
			.setName(Component.translatable(Texts.OPTION_RANGE))
			.setTooltip(Component.translatable(Texts.tooltip(Texts.OPTION_RANGE)))
			.setElementNameProvider(value -> Component.translatable(Texts.value(value)))
			.setImpact(OptionImpact.MEDIUM)
			.setStorageHandler(STORAGE)
			.setDefaultValue(Options.DEFAULT.range())
			.setBinding(value -> Options.set(Options.get().withRange(value)), () -> Options.get().range());
	}

	private static EnumOptionBuilder<FuseMode> fuse(
		ConfigBuilder builder, String path, String nameKey,
		Function<Options, FuseMode> read, BiFunction<Options, FuseMode, Options> write
	) {
		return builder.createEnumOption(id(path), FuseMode.class)
			.setName(Component.translatable(nameKey))
			.setTooltip(Component.translatable(Texts.tooltip(nameKey)))
			.setElementNameProvider(value -> Component.translatable(Texts.value(value)))
			.setStorageHandler(STORAGE)
			.setDefaultValue(read.apply(Options.DEFAULT))
			.setBinding(value -> Options.set(write.apply(Options.get(), value)), () -> read.apply(Options.get()));
	}

	private static BooleanOptionBuilder toggle(
		ConfigBuilder builder, String path, String nameKey,
		Function<Options, Boolean> read, BiFunction<Options, Boolean, Options> write
	) {
		return builder.createBooleanOption(id(path))
			.setName(Component.translatable(nameKey))
			.setTooltip(Component.translatable(Texts.tooltip(nameKey)))
			.setStorageHandler(STORAGE)
			.setDefaultValue(read.apply(Options.DEFAULT))
			.setBinding(value -> Options.set(write.apply(Options.get(), value)), () -> read.apply(Options.get()));
	}
}
