Dynamic Lighting makes light sources move. Items you hold or wear, dropped items, burning entities and many mobs and projectiles give off light that follows them: a torch in your hand lights the cave around you, a blaze lights the room it flies through, and lit TNT gets brighter as its fuse burns down.

It is a client-side Fabric mod for Minecraft 1.21.1. You do not need it on the server, and it works in single player. The light is cosmetic: it changes how the world is drawn on your screen, not the light level the game uses for mob spawning, crops or anything else.

Light falls off with distance, and where lights overlap the brightest one wins. Invisible entities and spectators emit nothing, except your own player.

## What emits light

- Every item that places a block gives the light of that block, up to 15: torches, lanterns, glowstone, sea lanterns, jack o'lanterns, end rods and so on. This works in either hand, in armour and body slots, and in Accessories and Trinkets slots.
- Some items have their own value: torch 14, soul torch 10, campfire 15, soul campfire 10, fire charge 10, lava bucket 15, nether star 12, blaze rod 10, glow berries 10, blaze powder, glow ink sac, glowstone dust and spectral arrow 8, glow lichen 7, magma cream and prismarine crystals 6.
- Torches, soul torches, campfires, soul campfires and fire charges go out under water. This can be switched off.
- Dropped items, items in item frames, arrows and thrown items give the light of the item.
- Burning entities emit 15. Living entities with the glowing outline emit 10.
- Blaze 10, magma cube 8, allay 6, glow squid 10, end crystal 12, lightning bolt 15, firework rocket 12.
- Fireballs 14, small fireballs 12, dragon fireballs 12, wither skulls 8, shulker bullets 6.
- An enderman gives the light of the block it carries.
- Ignited creepers and primed TNT shine steadily, or grow brighter as the creeper swells and the fuse burns down.
- Falling blocks, minecarts, block displays and item displays give the light of the block or item they show.

## Options

Open the settings in any of these ways:

- Sodium: the "Dynamic Lighting" page in Video Settings.
- Mod Menu: select Dynamic Lighting and press Configure.
- Command: `/dynamiclighting` while you are in a world.
- Key binding: "Toggle Own Light" in the "Dynamic Lighting" controls category. It is not bound by default.

There are eight options. They apply at once, except on Sodium's page, where Sodium's Apply button applies them.

- Mode: Off, Fastest, Fast or Fancy. Sets how often lights are updated. Default Fancy.
- Light Range: Short loses 2 light levels per block, Long loses 1 like a placed light and reaches further. Long rebuilds more chunk sections. Default Short.
- Own Light: the items you hold and wear light up your surroundings. Default on.
- Entity Lights: other players, mobs, dropped items and projectiles give off light. Default on.
- Water Sensitivity: fire-based items go out under water. Default on.
- Glowing Entities: living entities with the glowing outline give off light. Default on.
- Creeper Light: Off, Simple or Fancy. Default Simple.
- TNT Light: Off, Simple or Fancy. Default Simple.

The options are stored in `config/dynamiclighting.json`. Entity types can be switched off there with `disabled_entity_types`. A config file that cannot be read is set aside as `dynamiclighting.json.broken` and replaced by defaults.

A light is only updated again when it has moved far enough to change the picture or its brightness changed. Chunk sections that hold only air are never rebuilt, and the number of sections rebuilt per tick is lowered when the frame rate is below 90 fps.

## Compatibility

- Sodium 0.8.x: supported. Under Sodium, light moves in whole levels, because Sodium stores block light in four bits. Without Sodium it changes in finer steps. Fabric's Indigo renderer is covered as well.
- Shader packs: dynamic light is ordinary block light, so it has no colour of its own. Some packs have a handheld light, and then your held light is counted twice. Switch the pack's handheld light off, or switch off Own Light.
- Accessories and Trinkets: items worn in their slots light up the player who wears them.
- Light data files of other mods: the mod reads the files that mods ship for LambDynamicLights (`assets/<namespace>/dynamiclights/item/*.json` and `assets/<namespace>/dynamiclights/entity/*.json`). It does not provide that mod's API, so code that calls the API finds nothing to call.
- Not in 1.0.0: beacon beams, guardian lasers and particles do not emit light, and there is no coloured light.

## For pack and mod authors

Add your own light values with JSON files in `assets/<namespace>/dynamiclights/item/` and `.../entity/` in a resource pack or mod jar. A file with a problem is skipped on its own and named in the log, and the others are not affected. The file shapes, the luminance types and the rules for rejected files are described in the [README on GitHub](https://github.com/navrelis/DynamicLighting#for-pack-and-mod-authors).

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0 or newer
- Fabric API
- Java 21

Optional, nothing is bundled: Sodium 0.8.x, Mod Menu, Accessories, Trinkets.

## Licence and source

MIT. Dynamic Lighting is original work and not a fork of another mod. Source code, issue tracker and full documentation: https://github.com/navrelis/DynamicLighting
