# Changelog

All notable changes to Dynamic Lighting are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.0 - 2026-10-09

First release. Minecraft 1.21.1, Fabric, client-side only.

### Added

- Dynamic light that moves with its source, drawn in vanilla chunk meshes, with Sodium 0.8.x, on block entities and particles, and on the entities themselves. Fabric's Indigo renderer is covered too, for installs without Sodium. Light falls off linearly with distance, the brightest light wins, and solid blocks are not lit.
- Held items in both hands, items in armour and body slots, and items worn in Accessories and Trinkets slots, when those mods are installed. Every item that places a block gives the light of its block, plus built-in values for torch, soul torch, campfire, soul campfire, fire charge, lava bucket, nether star, blaze rod, blaze powder, glow berries, glow ink sac, glow lichen, glowstone dust, magma cream, prismarine crystals and spectral arrow.
- Entity lights: dropped items, items in item frames, arrows, thrown items, burning entities, glowing living entities, blaze, magma cube, allay, glow squid, enderman, fireballs, small fireballs, dragon fireballs, wither skulls, shulker bullets, firework rockets, lightning bolts, end crystals, falling blocks, minecarts, block displays and item displays.
- Ignited creepers and primed TNT, each with the modes Off, Simple and Fancy. Fancy grows brighter as the creeper swells or the fuse burns down.
- Torches, campfires and fire charges go out under water, for held, worn and dropped items. Can be switched off.
- Modes Off, Fastest, Fast and Fancy that change how often entities are checked, how far a light must move before it is updated, and how many chunk sections are rebuilt per tick.
- Light range Short (2 levels lost per block) and Long (1 level lost per block).
- Options screen on vanilla widgets, opened from Mod Menu, from a "Dynamic Lighting" page in Sodium's video settings, or with `/dynamiclighting`. Key binding "Toggle Own Light", unbound by default. English and German.
- Config file `config/dynamiclighting.json`, written with defaults on first start. Each key is checked on its own and falls back to its default. A file that is not a JSON object is renamed to `dynamiclighting.json.broken` and replaced by defaults. `disabled_entity_types` switches entity types off and can only be set in the file.
- Light data files for other mods, in the same locations and shapes that other mods already ship for LambDynamicLights: `assets/<namespace>/dynamiclights/item/*.json` and `.../entity/*.json`. Supports `fabric:load_conditions`, `water_sensitive`, entity `type`, `flags` and `equipment` predicates, and the luminance types `value`, `block`, `block_self`, `water_sensitive`, `wet_sensitive`, `item`, `item_entity`, `item_frame`, `arrow/derived_from_self_item`, `projectile/throwable_item`, `falling_block`, `minecart/display_block`, `enderman`, `glow_squid`, `magma_cube`, `creeper`, `display`, `display/block` and `display/item`. A file with a problem is skipped on its own and reported in the log. A `match` without any constraint and an entity `match` with unsupported keys are rejected. A built-in file covers the Friends and Foes wildfire.
- Light lookups take no locks. A light is only published again when it has moved far enough to change the picture or its brightness changed.

### Known limits

- Under Sodium, light moves in whole levels.
- No coloured light with shader packs. Shader packs with a handheld light of their own add it to this mod's light.
- Beacon beams, guardian lasers and particles do not emit light.
- The LambDynamicLights API is not provided. Only its data files are read.
