Summary (CurseForge summary field, at most 100 characters):

```
Client-side dynamic light from held, worn and dropped items and many entities. Fabric 1.21.1.
```

---

# Dynamic Lighting

Dynamic Lighting makes light sources move. Items you hold or wear, dropped items, burning entities and many mobs and projectiles give off light that follows them. A torch in your hand lights the cave around you. A blaze lights the room it flies through. Lit TNT gets brighter as its fuse burns down.

It is client-side only. You do not need it on the server, and it works in single player. The light is cosmetic: it changes how the world is drawn, not the real light level used by the game.

## What emits light

- Every item that places a block gives the light of that block, up to 15: torches, lanterns, glowstone, sea lanterns, end rods and so on. This works in either hand, in armour slots, and in Accessories and Trinkets slots.
- Some items have their own light: torch 14, soul torch 10, campfire 15, soul campfire 10, fire charge 10, lava bucket 15, nether star 12, blaze rod 10, glow berries 10, blaze powder, glow ink sac, glowstone dust and spectral arrow 8, glow lichen 7, magma cream and prismarine crystals 6. Torches, campfires and fire charges go out under water.
- Dropped items, items in item frames, arrows and thrown items emit the light of the item.
- Burning entities emit 15. Living entities with the glowing outline emit 10.
- Blaze 10, magma cube 8, allay 6, glow squid 10, enderman (the block it carries), fireballs 14 and 12, dragon fireball 12, wither skull 8, shulker bullet 6, firework rocket 12, lightning bolt 15, end crystal 12.
- Ignited creepers and primed TNT, either steady (10) or growing brighter as they are about to explode.
- Falling blocks, minecarts, block displays and item displays.

Light falls off with distance. Where lights overlap, the brightest wins. Invisible entities and spectators emit nothing, except your own player.

## Options

Open them with Mod Menu, from the "Dynamic Lighting" page in Sodium's video settings, or with the command `/dynamiclighting`.

- Mode: Off, Fastest, Fast or Fancy. Controls how often lights are updated. Default Fancy.
- Light Range: Short loses 2 light levels per block, Long loses 1 like a placed light. Default Short.
- Own Light: your held and worn items light your surroundings. Default on.
- Entity Lights: other players, mobs, dropped items and projectiles give off light. Default on.
- Water Sensitivity: torches and other fire-based items go out under water. Default on.
- Glowing Entities: entities with the glowing outline give off light. Default on.
- Creeper Light and TNT Light: Off, Simple or Fancy. Default Simple.

A key binding, "Toggle Own Light", is not bound by default. The options are stored in `config/dynamiclighting.json`. Entity types can be switched off in that file with `disabled_entity_types`. A damaged config file is set aside as `dynamiclighting.json.broken` and replaced by defaults.

## Compatibility

- Sodium 0.8.x: supported. Under Sodium light moves in whole levels, because Sodium stores block light in four bits. Without Sodium it changes in finer steps.
- Mod Menu, Accessories, Trinkets: used when installed.
- Shader packs: dynamic light is ordinary block light, so there is no coloured light. Packs with their own handheld light add it on top of this mod's light; switch the pack's light off or switch off Own Light.
- Light data files: the mod reads the files that other mods ship for LambDynamicLights (`assets/<namespace>/dynamiclights/item/*.json` and `.../entity/*.json`). It does not provide LambDynamicLights' API.
- Not in 1.0.0: beacon beams, guardian lasers and particles do not emit light.

Pack and mod authors: the file formats and the rules for rejected files are in the README on GitHub.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0 or newer
- Fabric API
- Java 21

Optional: Sodium, Mod Menu, Accessories, Trinkets.

## Licence and source

MIT. Source code, issues and full documentation: https://github.com/navrelis/DynamicLighting
