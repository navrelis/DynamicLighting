# Dynamic Lighting

Dynamic Lighting is a client-side Fabric mod for Minecraft 1.21.1. Items you hold or wear, dropped items, burning entities and many mobs and projectiles give off light, and that light moves with them. A torch in your hand lights the cave around you, a blaze lights the room it flies through, and a lit TNT block brightens as its fuse burns down.

The mod is only needed on the client. It works in single player and on servers that do not have it. It is written from scratch under the MIT licence and is not a fork of another dynamic lights mod.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0 or newer
- Fabric API
- Java 21

Optional, detected at start-up. Nothing is bundled:

- Sodium 0.8.x: adds a "Dynamic Lighting" page to Sodium's settings. See [Known limits](#known-limits) for how light behaves under Sodium.
- Mod Menu: adds a Configure button for the mod.
- Accessories and Trinkets: items worn in their slots light up the player who wears them.

## Features

All light is client-side and cosmetic. It changes how the world is drawn on your screen and nothing else. It does not change the light level the game uses for mob spawning, crops or anything else.

How light behaves:

- Light falls off linearly with distance, measured from the centre of each block to the entity's eye height. By default it loses 2 levels per block, so a level 14 torch reaches 7 blocks. With the "Long" range it loses 1 level per block, like a placed light.
- Where several lights overlap, the brightest one wins. Lights do not add up.
- Dynamic light never lowers the light that is already there, never goes above 15, and does not light solid blocks.
- Invisible entities and spectators emit nothing. The exception is your own player: your light keeps working while you are invisible.

### What emits light

Items (held in either hand, worn in an armour slot, in a body slot, or in an Accessories or Trinkets slot; also dropped items, items in item frames, items on arrows and thrown items):

- Every item that places a block gives the light of that block's default state, up to 15. This covers torches, lanterns, glowstone, sea lanterns, jack o'lanterns, end rods, shroomlights and so on.
- These items have their own values:

| Item | Light | Goes out in water |
| --- | --- | --- |
| Torch | 14 | yes |
| Soul torch | 10 | yes |
| Campfire | 15 | yes |
| Soul campfire | 10 | yes |
| Fire charge | 10 | yes |
| Lava bucket | 15 | no |
| Nether star | 12 | no |
| Blaze rod | 10 | no |
| Glow berries | 10 | no |
| Blaze powder | 8 | no |
| Glow ink sac | 8 | no |
| Glowstone dust | 8 | no |
| Spectral arrow | 8 | no |
| Glow lichen | 7 | no |
| Magma cream | 6 | no |
| Prismarine crystals | 6 | no |

Entities:

| What | Light |
| --- | --- |
| Any burning entity | 15 |
| Living entity with the glowing outline | 10 (option "Glowing Entities") |
| Blaze | 10 |
| Magma cube | 8 |
| Allay | 6 |
| Glow squid | 10, off while it is dark after being hurt |
| Enderman | the light of the block it carries |
| Creeper that has been ignited | 10 in Simple mode, 2 to 12 in Fancy mode as it swells (option "Creeper Light") |
| Primed TNT | 10 in Simple mode, 4 to 14 in Fancy mode as the fuse burns down (option "TNT Light") |
| Fireball | 14, none in water |
| Small fireball | 12, none in water |
| Dragon fireball | 12 |
| Wither skull | 8 |
| Shulker bullet | 6 |
| Firework rocket | 12 |
| Lightning bolt | 15 |
| End crystal | 12 |
| Dropped item, item in a frame, arrow, thrown item | the light of that item |
| Falling block | the light of the block |
| Minecart | the light of the block it shows |
| Block display, item display | the light of the block or item, unless the display has a brightness override |
| Friends and Foes wildfire | 10, 4 when wet |

Items that go out in water stay dark while the holder's eyes are under water (for dropped items: while the item is in water), if the option "Water Sensitivity" is on.

Items and entities from other mods are covered through data files, see [For pack and mod authors](#for-pack-and-mod-authors).

### Modes

The "Mode" option controls how much work the mod does:

- Off: no dynamic light at all. Light that was raised is cleared.
- Fastest, Fast, Fancy: the same light, with different update rates.

| Mode | Entities within 32 blocks of the camera are checked | Chunk sections marked for rebuild per tick |
| --- | --- | --- |
| Fancy | every tick | 64 |
| Fast | every 2nd tick | 32 |
| Fastest | every 4th tick | 16 |

Between 32 and 64 blocks an entity is checked half as often, beyond 64 blocks a quarter as often. A light is only published again after it has moved by `0.5 / (levels lost per block) * (1, 2 or 4 for Fancy, Fast, Fastest) * (1 + distance to camera / 32)` blocks, or when its brightness changed. Fancy without Sodium halves that distance.

## Options

| Option | Config key | Values | Default | What it does |
| --- | --- | --- | --- | --- |
| Mode | `mode` | `off`, `fastest`, `fast`, `fancy` | `fancy` | Master switch and update rate. |
| Light Range | `range` | `short`, `long` | `short` | Short loses 2 light levels per block, long loses 1. Long reaches further and rebuilds more chunk sections. |
| Own Light | `self_light` | `true`, `false` | `true` | The items you hold and wear, and your own player's other lights, light up your surroundings. |
| Entity Lights | `entity_lights` | `true`, `false` | `true` | Other players, mobs, dropped items and projectiles give off light. |
| Water Sensitivity | `water_sensitive` | `true`, `false` | `true` | Torches and other fire-based items go out under water. |
| Glowing Entities | `glowing_entities` | `true`, `false` | `true` | Living entities with the glowing outline give off light. |
| Creeper Light | `creeper` | `off`, `simple`, `fancy` | `simple` | Light of a creeper that is about to explode. Simple shines steadily, Fancy grows brighter as it swells. |
| TNT Light | `tnt` | `off`, `simple`, `fancy` | `simple` | Light of lit TNT. Simple shines steadily, Fancy grows brighter as the fuse burns down. |
| (config file only) | `disabled_entity_types` | list of entity type ids | `[]` | Entity types that never emit light. See below. |

Option changes apply at once.

### Opening the settings

- Mod Menu: select Dynamic Lighting and press Configure.
- Sodium: open Video Settings. The page "Dynamic Lighting" lists all options except `disabled_entity_types`. Changes apply with Sodium's Apply button.
- Command: `/dynamiclighting` opens the settings screen while you are in a world. If you type it in chat, the screen opens when the chat closes.
- Key binding: "Toggle Own Light" in the "Dynamic Lighting" category of the controls screen. It is not bound by default. It switches the Own Light option on or off, saves it and shows a short message above the hotbar.

The settings screen has a "Reset to Defaults" button. It does not touch `disabled_entity_types`.

### Config file

The options are stored in `config/dynamiclighting.json` in the game folder. The file is created with the defaults on the first start and written again when you change an option. Edits made to the file by hand are read at the next start of the game.

Each key is checked on its own. A missing key, a value of the wrong type or an unknown value falls back to the default for that key only, and a line in the log names the key.

If the file cannot be read as a JSON object (broken syntax, or text that is not valid UTF-8), it is renamed to `dynamiclighting.json.broken`, defaults are used and a new `dynamiclighting.json` is written. Your old file stays next to it, so you can repair it and rename it back.

Example:

```json
{
  "mode": "fancy",
  "range": "short",
  "entity_lights": true,
  "self_light": true,
  "water_sensitive": true,
  "glowing_entities": true,
  "creeper": "simple",
  "tnt": "simple",
  "disabled_entity_types": []
}
```

## For pack and mod authors

### Where the files go

The mod reads light data from resource packs and mod jars, from the folder `dynamiclights` in any namespace:

```
assets/<namespace>/dynamiclights/item/<name>.json
assets/<namespace>/dynamiclights/entity/<name>.json
```

Sub-folders are allowed (`entity/compat/wildfire.json`). These are client resources, not data pack files. A file under `data/` is not read. Resource packs are read on every resource reload (F3 + T) and the files are applied once you are in a world. If two packs ship the same path, the pack with the higher priority wins, as with any asset.

This is the same location and the same file shape that other mods already use for LambDynamicLights. Those files work as they are. The mod does not provide LambDynamicLights' API: a mod that ships only data files is fine, code that calls that API has nothing to call.

A file can carry `fabric:load_conditions`. The condition `fabric:mod_loaded` is understood directly; other Fabric API conditions are decoded by Fabric API. A `neoforge:conditions` key is ignored. A file with `"silence_error": true` logs a problem at debug level only.

### Item files

An item file gives the light of items that match a predicate.

```json
{
  "match": {
    "items": "#minecraft:candles"
  },
  "luminance": 4,
  "water_sensitive": true
}
```

- `match` is a vanilla item predicate: `items` (one id, a list of ids, or a `#tag`), `count`, `components` and `predicates`. At least one of them must be given.
- `luminance` is a whole number from 0 to 15, or an object with a `type`:
  - `{"type": "value", "value": 12}` is the same as writing 12.
  - `{"type": "block", "block": "minecraft:lantern"}` takes the light of that block's default state.
  - `{"type": "block_self"}` takes the light of the default state of the block the item places.
- `water_sensitive` (optional, default `false`): the rule gives no light while the stack is wet and the option "Water Sensitivity" is on.

A matching rule replaces the block-light fallback of block items, so a rule can also make a block item darker. If several rules match one item, the brightest wins. A rule with `count`, `components` or `predicates` is tested against each stack, which is a little more work than a rule on the item alone.

### Entity files

An entity file gives the light of entities of one or more types.

```json
{
  "match": {
    "type": "minecraft:strider",
    "flags": { "is_baby": true }
  },
  "luminance": {
    "type": "dynamiclighting:water_sensitive",
    "out_of_water": 8,
    "in_water": 0
  }
}
```

- `match` supports exactly three keys: `type` (required: one id, a list of ids or a `#tag`), `flags` and `equipment` (both vanilla entity predicates; `equipment` takes the slots `head`, `chest`, `legs`, `feet`, `body`, `mainhand` and `offhand`).
- `luminance` is a number from 0 to 15, an object with a `type`, or a list of these. With a list, the brightest entry counts.
- Entity lights from files add to the rules built into the mod: for an entity type, the brightest applicable light wins. A file cannot make a built-in light darker. To turn an entity type off, use `disabled_entity_types` (below).
- Entity files only apply to entities that are drawn, and only while the option "Entity Lights" is on (for the local player: "Own Light").

### Luminance types

The type name may be written with the namespace `dynamiclighting:` or `lambdynlights:`, or without any namespace.

For items: `value`, `block`, `block_self` (see above).

For entities:

| Type | Fields | Meaning |
| --- | --- | --- |
| `value` | `value` | A fixed light. |
| `water_sensitive` | `out_of_water`, `in_water` | One light with the entity's eyes under water, another one otherwise. A missing field means 0. |
| `wet_sensitive` | `dry`, `wet` | Like `water_sensitive`, and rain counts as wet. |
| `item` | `item` (a stack: `id`, optional `count` and `components`), optional `always` (`"dry"` or `"wet"`), optional `include_rain` | The light of a stack written into the file. Without `always`, the stack is wet when the entity's eyes are under water (or in rain with `include_rain`). |
| `item_entity` | none | The light of a dropped item stack. |
| `item_frame` | none | The light of the item in an item frame. |
| `arrow/derived_from_self_item` | none | The light of the item an arrow is picked up as. |
| `projectile/throwable_item` | none | The light of the stack a thrown projectile carries. |
| `falling_block` | none | The light of the falling block. |
| `minecart/display_block` | none | The light of the block shown in a minecart. |
| `enderman` | none | The light of the block an enderman carries. |
| `glow_squid` | none | 10, off while the squid is dark after being hurt. |
| `magma_cube` | none | 8. |
| `creeper` | none | The fuse light of a creeper, following the option "Creeper Light". |
| `display` | `luminance` | The wrapped light, unless the display entity has a brightness override. |
| `display/block` | none | The light of a block display. |
| `display/item` | none | The light of an item display. |

`water_sensitive` and `wet_sensitive` always react to water. They are not tied to the option "Water Sensitivity". The option only affects item rules and stacks.

### What is rejected, and why

A file with a problem is skipped as a whole, and the other files are not affected. A warning with the file's id and the reason is written to the log (`Skipped light data file ...`). Reasons that are normal in a mod pack are logged at debug level only: an item, entity type, block or tag that does not exist (the mod is not installed), load conditions that are not met, and the old item format with a top-level `item` instead of `match`.

Rejected with a warning:

- A `match` without any constraint. An item `match` that has no `items`, `count`, `components` or `predicates`, an `items` list that is empty, and an entity `match` that is empty would apply to every item or every entity, which is almost never intended.
- An entity `match` with a key other than `type`, `flags` and `equipment`, or without `type`. Other predicate keys cannot be evaluated here, and applying only part of a predicate would light entities that the author meant to leave dark, so the whole file is skipped.
- A `luminance` that is missing, not a whole number from 0 to 15, an unknown type, an empty list, or nested more than 8 levels deep.
- A file without a `match` object.
- A predicate or item stack that the vanilla decoder refuses, for example a `flags` value that is not `true` or `false`.
- Anything that is not valid JSON, or whose root is not an object.

### Switching an entity type off

Put the entity type ids into `disabled_entity_types` in `config/dynamiclighting.json`:

```json
{
  "disabled_entity_types": ["minecraft:blaze", "minecraft:glow_squid"]
}
```

A disabled type never emits, not even while burning or glowing. There is no screen for this list. Ids that are not valid are ignored with a log line. `minecraft:player` switches off the light of every player, including yours.

## Known limits

- Under Sodium, light moves in whole levels. Sodium keeps four bits of block light per position, so the mod rounds the light to the nearest whole level there. Without Sodium, light is kept in sixteenths of a level and changes more smoothly.
- There is no coloured light. Dynamic light is added as ordinary block light, so with a shader pack that has coloured lighting it has no colour of its own and is coloured like any block light.
- Some shader packs have a handheld light of their own. Then your held light is counted twice: once by the pack and once by this mod. Switch the pack's handheld light off, or switch off Own Light (option or key binding).
- Beacon beams, guardian lasers and particles do not emit light in 1.0.0.
- Only entities your client has loaded and draws can emit light. For other players, held and worn items depend on what the server sends your client.
- Mods that call the LambDynamicLights API directly find nothing to call. Only their data files are used.

## How it works

This is a short description of the design, not a measurement.

- One hook on the game's block light lookup serves vanilla chunk meshes, Sodium, block entities and particles. A second hook raises the light entities are drawn with. There are no mixins into entities or particles, and an optional hook for Fabric's Indigo renderer for installs without Sodium.
- The lights are published as one immutable snapshot of plain numbers behind a volatile reference. Looking up the light of a block takes no locks and holds no entity references. A lookup first asks whether any light exists, then whether the 16-block section is touched by one, and only then looks at the lights of that section.
- Entities are scanned once per client tick. A light is published again only when it moved far enough to change the picture, or when its brightness changed. A light that stands still costs nothing.
- Chunk sections that need a rebuild are collected into one set per tick, nearest first, with a per-tick budget and carry-over. Rebuilds are never flagged as important, so meshing stays off the render thread.
- Everything is dropped when the level changes or you disconnect.

## Building from source

You need Java 21. The Gradle wrapper downloads the rest.

```
.\gradlew.bat build
```

On Linux and macOS use `./gradlew build`. The jar is written to `build/libs/dynamiclighting-1.0.0.jar`. The build also runs the unit tests. To start a development client, use `gradlew runClient`.

## Licence

MIT. See [LICENSE](LICENSE).

Source code and issue tracker: <https://github.com/navrelis/DynamicLighting>
