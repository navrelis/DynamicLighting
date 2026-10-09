# Upstream study: LambDynamicLights 4.8.11 (branch `1.21`, Minecraft 1.21.1)

This document describes how the upstream mod works, where it is wrong and where it is slow. It is written so that an engineer who never sees the upstream source can build our own mod from it.

**Licence note.** Upstream is under the Lambda License. Nothing here is copied code. Behaviour is described in our own words. Class, method, field, config and JSON key names are used as identifiers only. All JSON examples are written fresh.

**What was studied.** The clone at `F:\Coding\_upstream\LambDynamicLights`, commit `c2e10b8` (version 4.8.11, the last 1.21 release, marked end-of-life by its author). All file and line references below point to that commit. Paths starting with `src/` are relative to the clone; `api/` is the API module.

**How names were checked.** Upstream uses "yalmm" mappings. Every Minecraft name in this document is the official Mojang name, checked with `javap` against a Mojang-mapped Minecraft 1.21.1 jar from the local Loom cache. Sodium, Iris, Fabric API (Indigo, resource conditions, lifecycle events), Accessories and Trinkets were checked against the jars in the pack's `mods` folder, also with `javap`. Where a statement could not be checked, it says so.

**Ratings used in section 5.** CONFIRMED means the path was traced in the code (upstream source, and where needed Minecraft or Sodium bytecode). It does not mean it was reproduced in a running game; nothing was run. PLAUSIBLE means a suspicion with a stated gap.

---

## 1. Behaviour inventory

### 1.1 The light model

- A light source is a point with an integer luminance from 0 to 15. Luminance below 1 means "no light".
- The light a source gives to a block position is computed from the distance `d` between the source point and the **centre of the block**:
  - if `d > 7.75`: nothing;
  - otherwise: `luminance - 15 * d / 7.75`.
- So the fall-off is about 1.94 light levels per block, almost twice as steep as vanilla block light (1 level per block). The reach is `luminance * 7.75 / 15` blocks: 7.75 blocks for luminance 15, 7.2 for 14 (torch), 5.2 for 10, 4.1 for 8. Upstream's own design note says the 7.75 limit exists to keep the number of chunk sections to rebuild small. (The formula in `HOW_DOES_IT_WORK.md` is older and differs from the code. The code is as stated here.)
- Several sources do not add up. The largest single value wins. The result is clamped to 0..15 and is a fractional number.
- The dynamic value is merged into the vanilla packed light ("lightmap coordinates", block light in bits 4..19, sky light from bit 20): if the dynamic value is greater than the vanilla block light level, the block part is replaced by `floor(dynamic * 16)`. Sky light is untouched. So the vanilla path keeps 1/16 level precision. Sodium does not (see 3.4 b).
- Dynamic light is never applied at a position whose block is "solid render" (`BlockStateBase.isSolidRender(BlockGetter, BlockPos)`). Faces of solid blocks take their light from the air position in front of them, as in vanilla.
- The source point of an entity is its X and Z position and its **eye height** Y (`Entity.getEyeY()`). The position is the tick position, not an interpolated one.

### 1.2 What emits light

Luminance is recomputed in the entity's client tick (see 4.2). The rules are layered. For every entity the final value is the maximum of all layers that apply.

**All entities (base rule, `EntityMixin`)**

1. If the entity type is switched off (see 1.6), or the global "entities" option is off (for entities other than the local player), or the entity is the local player and "self" is off: luminance 0.
2. If `Entity.isInvisible()`: luminance 0. This also suppresses fire and held items.
3. Otherwise: 15 if `Entity.isOnFire()`, else 0. Note that `isOnFire()` is always false for fire-immune entities.
4. Maximum with the data-driven luminance for this entity (entity light source files, 1.2 table below).

**Living entities (`LivingEntityMixin`, replaces steps 2–4)**

1. Invisible: 0.
2. If on fire, or the entity has the glowing outline (`Entity.isCurrentlyGlowing()`) and the option `glowing_effect` is on: 15.
3. Otherwise: the maximum item luminance over all equipment (`LivingEntity.getAllSlots()`: both hands, four armour slots, body armour slot), and then over the slots of one accessory mod (see below). The "submerged" flag for the item lookup is computed once per entity per tick (1.3).
4. Maximum with the data-driven entity luminance.

**Players (`PlayerEntityMixin`)**: spectators emit 0. Otherwise the living-entity rule. On the first tick after the player's level object changed, luminance is forced to 0 once.

**First person / third person.** There is no first-person-specific logic. The local player is an ordinary light source at eye height. The option `light_sources.self` (and a key binding that toggles it) switches the local player's light off in every camera mode. Other players are not affected by it.

**Accessory slots (`compat/`)**. Exactly one layer is chosen at class-load time, in this order: Accessories if mod id `accessories` is present, else Trinkets if `trinkets` is present, else Curios. The chosen layer returns the maximum item luminance over all equipped accessory stacks, with the same submerged flag. It is only asked if the vanilla slots gave less than 15.
- Accessories: `AccessoriesCapability.get(LivingEntity)` (may be null), `getAllEquipped()`, each entry's `stack()`. Checked against Accessories 1.1.0-beta.53: these methods exist with these signatures.
- Trinkets: `TrinketsApi.getTrinketComponent(LivingEntity)` (an `Optional`), `getAllEquipped()`, each pair's second element is the stack (`net.minecraft.util.Tuple.getB()`). Checked against Trinkets 3.10.0.

**Entities handled by code, not data**

| Entity | Rule | Where |
|---|---|---|
| Primed TNT (`PrimedTnt`) | Off if option `tnt` is `off` (the default). `simple`: 10. `fancy`: `10 - trunc(10 * s(fuse / startFuse))`, where `s(x) = x^3 * (x * (6x - 15) + 10)` (`Mth.smoothstep`, not clamped) and `startFuse` is the fuse value read at the end of the entity constructor. Then the maximum with the base rule (fire). | `mixin/lightsource/PrimedTntEntityMixin` |
| Minecarts (`AbstractMinecart`) | Base rule, then maximum with the light emission of the displayed block (`getDisplayBlockState().getLightEmission()`). | `AbstractMinecartEntityMixin` |
| Falling blocks (`FallingBlockEntity`) | Base rule, then maximum with the light emission of the carried block state. | `FallingBlockEntityMixin` |

**Entities handled by built-in data files** (`assets/lambdynlights/dynamiclights/entity/`, 17 files)

| Entity type(s) | Luminance |
|---|---|
| `allay` | 8 |
| `blaze` | 10 when dry, 4 when wet (in water, rain or bubble column) |
| `creeper` | 0 while not swelling (`Creeper.getSwelling(0)` not above 0.001). While swelling: option `creeper` = `off` → 0, `simple` (default) → 10, `fancy` → `trunc(swelling * 10)` |
| `dragon_fireball` | 14 |
| `fireball` | 14, but 0 while under water |
| `small_fireball` | 12, but 0 while under water |
| `wither_skull` | 12 |
| `glow_squid` | `trunc(clamp(12 * (1 - darkTicksRemaining / 10), 0, 12))`. So 12 normally, 0 right after being hurt, back to 12 over the last 10 dark ticks |
| `magma_cube` | 11 if its `squish` value is above 0.6, else 8 |
| `enderman` | Light emission of the carried block, 0 if none |
| `item` (dropped item) | Item luminance of the stack. Submerged flag is `Entity.isUnderWater()` |
| `item_frame`, `glow_item_frame` | Item luminance of the framed stack. Submerged flag is "the fluid state at the frame's block position is not empty" |
| `glow_item_frame` | Additionally 12. This type is switched off in the default config file |
| `spectral_arrow` | Item luminance of the arrow's pickup item (`AbstractArrow.getPickupItemStackOrigin()`), which gives 8 through the built-in item file. Submerged flag is `isUnderWater()` |
| `egg`, `ender_pearl`, `experience_bottle`, `snowball`, `potion` | Item luminance of the thrown item (`ThrowableItemProjectile.getItem()`), submerged flag `isUnderWater()` |
| `block_display` | Only if the display has no brightness override: light emission of the displayed block state |
| `item_display` | Only if the display has no brightness override: item luminance of the displayed stack, submerged flag `isUnderWater()` |

Ordinary arrows, tridents, fireworks, experience orbs and so on have no entry. They glow only through the base rule (15 while burning).

**Item luminance (`ItemLightSources.getLuminance(stack, submerged)`)**

1. Go through all loaded item light sources. For every source whose predicate matches the stack: remember that something matched. If the stack is submerged, the option `water_sensitive_check` is on and the source is flagged water-sensitive, skip it. Otherwise take the maximum of its luminance.
2. If **no** source matched at all: if the item is a `BlockItem`, take the block's default state, apply the stack's `minecraft:block_state` component if present, and use that state's light emission. Otherwise use `Block.byItem(item)` (air for most items, so 0).

Consequences worth knowing:
- A matching source disables the block fallback, even if it was skipped because of water. That is how a submerged torch goes dark.
- A resource pack can switch off the glow of a block item by adding a source that matches it with luminance 0.
- The fallback uses the **default** block state. Blocks that are dark in their default state do not glow as items (candles, glow lichen, redstone lamp, furnace). Blocks lit in their default state do (lantern 15, glowstone 15, sea lantern 15, shroomlight 15, jack o'lantern 15, end rod 14, soul lantern 10, magma block 3, and so on).

Built-in item files (`assets/lambdynlights/dynamiclights/item/`, 11 files):

| Items | Luminance | Water-sensitive |
|---|---|---|
| `torch`, `redstone_torch`, `soul_torch` | the item's own block (14, 7, 10) | yes |
| `campfire`, `soul_campfire` | the item's own block (15, 10) | yes |
| `lava_bucket` | block `minecraft:lava` (15) | yes |
| `fire_charge` | 10 | yes |
| `blaze_powder`, `blaze_rod` | 10 | no |
| `glowstone_dust` | 10 | no |
| `prismarine_crystals` | 10 | no |
| `glow_berries` | 14 | no |
| `glow_ink_sac` | 12 | no |
| `nether_star` | 8 | no |
| `spectral_arrow` | 8 | no |

(The block light values in brackets are vanilla values quoted from memory, not re-read from the jar.)

### 1.3 Water rules

- **Held and worn items.** Submerged means: option `water_sensitive_check` is on **and** the fluid state at the block that contains the entity's eye position is not empty (`Level.getFluidState(pos).isEmpty()` is false). It is any fluid, lava included, at any fill level. It is block-granular.
- **Dropped items, arrows, thrown items, item displays.** Submerged means `Entity.isUnderWater()`: the eyes were in water and the entity is in water. Water only.
- **Item frames.** Submerged means any fluid at the frame's block position.
- **The item lookup checks the option again**, so with the option off no item is ever treated as submerged.
- **Entities with their own water rule** (data types `water_sensitive` and `wet_sensitive`, see 2.3) are **not** affected by the option. `water_sensitive` uses `isUnderWater()`. `wet_sensitive` uses `isInWaterRainOrBubble()`.

### 1.4 Fire, glowing, invisibility, spectators

- Burning: any non-fire-immune entity that is on fire emits 15.
- Glowing: living entities with the glowing flag emit 15 if `light_sources.glowing_effect` is on. Non-living entities with the glowing flag do not emit.
- Invisible: 0, always. An invisible player with a torch emits nothing, in first person too (reported as upstream issue #394).
- Spectator players: 0.
- Entities render brighter too: an entity's own render light is at least its own luminance (3.4 c).

### 1.5 Out of scope for our 1.0.0 (short notes)

- **Beacon beams.** A client-side hook in `BeaconBlockEntity.tick` registers a column-shaped light (from the beacon up to the build height, luminance = the beacon block's light emission, fall-off by horizontal distance) while the beam exists, and removes it when the beam ends or the block entity is removed. Option `light_sources.beam`.
- **End gateway beams.** A hook at the end of `TheEndGatewayBlockEntity.beamAnimationTick` registers a full-height column light while the gateway is spawning or cooling down. Same option.
- **Guardian lasers.** While a guardian has an attack target, a line-shaped light runs from the guardian to the target. Luminance 7, rising with the attack animation by up to 5, and 13 in the last 5 ticks. Option `light_sources.guardian_laser`. The line's bounding box is computed wrongly when the start coordinate is larger than the end coordinate (`api/.../behavior/LineLightBehavior.java:148-156`: floor is applied to the start and ceil to the end before they are ordered), so the box can be up to one block too small on each side.
- **Particle lights.** Every `Particle` gets light-source fields through a mixin and is ticked through a hook in `ParticleEngine.tickParticle`. In this branch only the warden's sonic boom particle emits (its own particle light times a fade factor times 0.75). Option `light_sources.sonic_boom`. The default config file also contains `light_sources.firefly`, which no code in this branch reads.
- **Debug renderers.** Four renderers are drawn at the end of `DebugRenderer.render`: section rebuild boxes, light level numbers, bounding boxes of custom sources, and spatial-hash cells. Extra lines are added to the F3 screen through `DebugScreenOverlay.getGameInformation`. The setter for `debug.light_level_radius` stores the wrong variable (`src/main/java/.../DynamicLightsConfig.java:534`).
- **Pride backgrounds.** The settings screen draws a random pride flag behind the tab list (bundled library `pridelib`).
- **NeoForge.** A second source set and "shim" classes let one jar run on both loaders. On NeoForge the resource-condition check always passes, so `neoforge:conditions` in data files is ignored.
- **Public API for other mods.** Entrypoints `lambdynlights:initializer` and the legacy `dynamiclights`, managers for item sources, entity sources and custom "behaviours" (shapes with their own `lightAtPos`), data generators, and a deprecated handler API kept alive with two mixins into the API classes (`mixin/retrofit/`).

### 1.6 Config options

File: `lambdynlights.toml` in the config directory, TOML, read once at start with the NightConfig library. A missing file is replaced by the default file from the jar. A file that fails to **parse** is copied to `lambdynlights.toml.old` and replaced by the default. Saving happens when the settings screen closes and after the key binding is used: the config is serialised, compared by hash with the last saved text, written to a `.tmp` file and moved over the real file on a single background thread.

| Key | Values | Default | Effect |
|---|---|---|---|
| `mode` | `off`, `fastest`, `fast`, `fancy` (case-insensitive; unknown text falls back to the default) | `fancy` | Master switch and update rate, see 1.7 |
| `chunk_rebuild_scheduler` | `immediate`, `culling` | `culling` | Rebuild scheduler, see 4.6 |
| `adaptive_ticking.slow` | integer, chunks. GUI slider 1..33, 33 shown as "Off" | 5 | Sources farther from the camera than this update every 5 ticks |
| `adaptive_ticking.slower` | integer, chunks, same slider | 8 | Sources farther than this update every 10 ticks |
| `adaptive_ticking.background_sleep` | boolean | true | Sources more than 8.75 blocks behind the camera plane update every 20 ticks |
| `light_sources.entities` | boolean | true | Off: no entity except the local player emits |
| `light_sources.self` | boolean | true | Off: the local player emits nothing |
| `light_sources.water_sensitive_check` | boolean | true | See 1.3 |
| `light_sources.creeper` | `off`, `simple`, `fancy` | `simple` | See 1.2 |
| `light_sources.tnt` | `off`, `simple`, `fancy` | `off` | See 1.2 |
| `light_sources.glowing_effect` | boolean | true | See 1.4 |
| `light_sources.beam` | boolean | true | Beacon and end gateway beams |
| `light_sources.guardian_laser` | boolean | true | Guardian laser |
| `light_sources.sonic_boom` | boolean | true | Sonic boom particle |
| `light_sources.settings.entities.<namespace>.<path>` | boolean, one per entity type, created on demand | true (default file: `minecraft.glow_item_frame = false`) | Off: that entity type never emits. `minecraft.player` covers all players |
| `debug.active_dynamic_lighting_cells`, `debug.display_dynamic_lighting_chunk_rebuild`, `debug.display_behavior_bounding_box` | boolean | false | Debug renderers |
| `debug.cell_display_radius`, `debug.light_level_radius` | integer 0..10 | 0 | Debug renderers |

Key binding: `lambdynlights.key.toggle_fps_dynamic_lighting`, unbound by default, category "Miscellaneous". It flips `light_sources.self`, saves, and shows an action-bar message. It is added by a mixin at the start of `Options.load()` that rewrites the `keyMappings` array (after first removing an earlier copy, a fix for corrupted `options.txt`, issues #311 and #386).

None of the values are range-checked or type-checked (see defects 13 and 14).

### 1.7 Performance modes

**`mode`** sets a floor for how often a source is updated:

| Mode | Update period of every source | Light lookup hook |
|---|---|---|
| `off` | never (entity light ticks return at once, the spatial lookup is not rebuilt) | returns the vanilla value |
| `fastest` | every 10 ticks | active |
| `fast` | every 5 ticks | active |
| `fancy` | every tick | active |

"Update" means two things: the luminance of the entity is recomputed, and the sections around it are considered for a rebuild. The effective period of a source is the **longer** of the mode's period and the adaptive period below. A source with period `n` is updated on ticks where `tick mod n == id mod n`, which spreads the work.

Important: only the updates are throttled. The light lookup always uses the source's **current** position and luminance fields. So in `fast` and `fastest`, and for throttled far sources, a section that is rebuilt for any other reason shows newer light than its neighbours (defect 6).

**Adaptive ticking** (per source, per tick, measured from the main camera):
- If `background_sleep` is on and the source is more than 8.75 blocks behind the plane through the camera that is perpendicular to the view direction: period 20.
- Else if the squared distance to the camera is above `(slower * 16)^2`: period 10.
- Else if above `(slow * 16)^2`: period 5.
- Else: period 1.
For shaped sources (beams) the eight corners of the bounding box are tested and the shortest period wins.

**`chunk_rebuild_scheduler`**: `immediate` marks every affected section dirty at once. `culling` (default) only marks sections that intersect the camera frustum and remembers the rest (4.6).

### 1.8 Settings screen, Mod Menu, Sodium

- Own screen built with the bundled SpruceUI library. Tabs: General (mode; entities; self; water check), Performance (scheduler; two distance sliders; background sleep), Entities (searchable list with one toggle per registered entity type), Special (creeper; TNT; beams; glowing; guardian laser; sonic boom), Debug. A reset button and a donation button. The glowing option is added twice on the Special tab (`src/main/java/.../gui/SettingsScreen.java:271` and `:273`).
- Mod Menu: entrypoint `modmenu`, a `ModMenuApi` whose config screen factory opens that screen.
- Vanilla video settings: a mixin appends one button that opens the screen (not visible when Sodium replaces the video settings screen).
- Sodium 0.8.x: entrypoint `sodium:config_api_user` with a class implementing Sodium's `ConfigEntryPoint`. In `registerConfigLate` it registers the mod's own entry (name, colour theme, icon) with a single **external page** that opens the SpruceUI screen. It does not register native Sodium options. Checked against Sodium 0.8.13: `ConfigEntryPoint.registerConfigLate(ConfigBuilder)`, `ConfigBuilder.registerOwnModOptions()`, `createExternalPage()`, and also native builders (`createOptionPage`, `createOptionGroup`, `createBooleanOption`, `createIntegerOption`, `createEnumOption`) exist.
- Sodium 0.6 and older: three mixins into Sodium's and Reese's Sodium Options' GUI classes add a page that redirects to the screen. A mixin plugin switches them off for newer Sodium, so they are inactive in our target.
- Metadata: breaks `sodiumdynamiclights` (any version) and `sodium` below 0.6.0; recommends `modmenu`.

---

## 2. Data file format

### 2.1 Where files are found, merging, timing, errors

- **Location.** Client resources only (`assets/`), any namespace: `assets/<namespace>/dynamiclights/item/**/*.json` and `assets/<namespace>/dynamiclights/entity/**/*.json`. Sub-folders are included. Files under `data/` are never read.
- **One file is one light source.** There is no list form and no `replace` key.
- **Override.** The loader asks the resource manager for all matching resources, which gives one resource per identifier (namespace + path): the one from the highest-priority pack. So a pack overrides another pack's file only by using the same namespace and path, and the whole file is replaced. To remove a built-in source, a pack must ship a file at the same path, for example one with luminance 0.
- **Merge.** All files that survive are active together. For an item the result is the maximum over all matching sources (with the rules in 1.2). For an entity it is the maximum over all matching sources, stopping early at 15.
- **Load.** Two reload listeners (ids `lambdynlights:item` and `lambdynlights:entity`, the second depends on the first) run at every client resource reload: game start, F3+T, resource pack change. In the prepare stage (a worker thread) each file is parsed to a JSON tree and kept. Only JSON objects are accepted.
- **Apply.** Decoding into predicates needs registries with tags, so it is done later on the main thread: (1) at the end of a resource reload if a level exists, with the level's registry access; (2) every time the client receives tags (Fabric's `CommonLifecycleEvents.TAGS_LOADED` with the client flag, which Fabric fires at the end of `TagCollector.updateTags(RegistryAccess, boolean)`, so on joining a world and after a server-side `/reload`). Each apply rebuilds the complete list from the kept JSON trees. Sources registered by other mods through the API event are appended at each apply. Before the first apply of a session the lists are empty.
- **Errors.** A file that is not a JSON object, or cannot be read: one warning, file skipped. A file that fails to decode: one warning with the file id and the decoder's message, file skipped. The warning is suppressed if the file has `"silence_error": true`, unless the JVM property `lambdynamiclights.resource.force_log_errors` is set or it is a development build. Syntactically invalid JSON is **not** caught (defect 10).
- **Unknown keys** are ignored everywhere, because the decoders are DataFixerUpper record codecs.

### 2.2 Item light source file

Top-level keys:

| Key | Required | Type | Meaning |
|---|---|---|---|
| `match` | yes | vanilla item predicate | Which stacks this source applies to |
| `luminance` | yes | integer 0..15, or an object | How bright |
| `water_sensitive` | no, default `false` | boolean | See 1.3 |
| `silence_error` | no, default `false` | boolean | Removed before decoding. See 2.1 |
| `fabric:load_conditions` | no | Fabric resource conditions | See 2.4 |

`match` is decoded with Minecraft's own `ItemPredicate.CODEC` (1.21.1). All four keys are optional:

| Key | Form | Notes |
|---|---|---|
| `items` | one item id as a string, a list of item ids, or a tag as a string starting with `#` | Decoded as a holder set against the item registry. An unknown id or tag makes the whole file fail |
| `count` | integer, or `{"min": .., "max": ..}` | Stack size |
| `components` | object: component id → exact value | Exact match of data components |
| `predicates` | object: item sub-predicate id → predicate | For example enchantments, damage, potion contents, custom data. Uses dynamic registries (enchantments), which is the reason decoding waits for the level |

A `match` with none of these keys, for example because a key is misspelled, decodes without error and matches **every** stack (defect 11).

`luminance` forms:

| Form | Parameters | Value |
|---|---|---|
| plain integer | none | that value (must be 0..15, else the file fails) |
| `{"type": "value", "value": n}` | `value`: integer 0..15 | `n` |
| `{"type": "block", "block": "<block id>"}` | `block`: id in the block registry | light emission of that block's default state |
| `{"type": "block_self"}` | none | light emission of the default state of the block that belongs to the stack's item (`Block.byItem`) |

Type names for items are **plain strings without a namespace**. `"lambdynlights:block_self"` is not accepted.

### 2.3 Entity light source file

Top-level keys: `match` (required), `luminance` (required), `silence_error`, `fabric:load_conditions`. There is no `water_sensitive` key for entities.

`match` is upstream's own entity predicate. All keys are optional; an empty object matches every entity (defect 11).

| Key | Form | Checked against |
|---|---|---|
| `type` | one entity type id, a list of ids, or a `#tag` (vanilla `EntityTypePredicate`) | the entity's type |
| `location` | object, see below | the entity's level and feet position |
| `effects` | vanilla `MobEffectsPredicate` (object: effect id → details) | the entity's active effects. On a client these are normally only known for the local player (believed, not verified) |
| `flags` | vanilla `EntityFlagsPredicate`: booleans `is_on_ground`, `is_on_fire`, `is_sneaking`, `is_sprinting`, `is_swimming`, `is_flying`, `is_baby` (key spellings from memory; the seven fields exist in the jar) | entity state |
| `equipment` | vanilla `EntityEquipmentPredicate`: `head`, `chest`, `legs`, `feet`, `body`, `mainhand`, `offhand`, each an item predicate | equipment of living entities |
| `vehicle` | a nested entity predicate | the entity it rides (no match if it rides nothing) |
| `passenger` | a nested entity predicate | at least one direct passenger must match |
| `slots` | vanilla `SlotsPredicate`: slot range name → item predicate | inventory slots |

There is **no** `components` or NBT key for entities in this branch (the changelog adds entity component predicates only for Minecraft 1.21.5 and later).

`location` keys (all optional): `position` (`x`, `y`, `z`, each a number or `{min,max}` range), `biomes` (id, list or `#tag`), `dimension` (id), `smokey` (boolean: above a lit campfire), `light`, `can_see_sky` (boolean). `light` is meant to accept either `{"block": range, "sky": range}` or `{"any": range}` (maximum of block and sky light). The second form does not work (defect 18).

`luminance` is **one value or a list of values**. The result is the maximum of the list. Each value is a plain integer 0..15 or an object with `type`. Entity type names are **full identifiers**; the namespace is required.

| `type` | Parameters | Value |
|---|---|---|
| `lambdynlights:value` | `value`: integer 0..15 | constant |
| `lambdynlights:water_sensitive` | `out_of_water`, `in_water`: each one value or a list, default empty (0) | `in_water` if `Entity.isUnderWater()`, else `out_of_water` |
| `lambdynlights:wet_sensitive` | `dry`, `wet`: each one value or a list, default empty (0) | `wet` if `Entity.isInWaterRainOrBubble()`, else `dry` |
| `lambdynlights:item` | `item`: an item stack in vanilla stack JSON (`{"id": .., "count": .., "components": ..}`); `include_rain`: boolean, default false; `always`: `"dry"` or `"wet"`, optional | item luminance of that stack. Submerged flag: `always` if given, else rain-inclusive wetness if `include_rain`, else `isUnderWater()` |
| `lambdynlights:item_entity` | none | dropped item: luminance of its stack |
| `lambdynlights:item_frame` | none | item frame: luminance of its stack |
| `lambdynlights:arrow/derived_from_self_item` | none | arrow: luminance of its pickup item |
| `lambdynlights:projectile/throwable_item` | none | thrown item projectile: luminance of its item |
| `lambdynlights:falling_block` | none | falling block: light emission of its block state |
| `lambdynlights:minecart/display_block` | none | minecart: light emission of its display block |
| `lambdynlights:enderman` | none | carried block's light emission |
| `lambdynlights:glow_squid` | none | see 1.2 |
| `lambdynlights:magma_cube` | none | see 1.2 |
| `lambdynlights:creeper` | none | see 1.2 |
| `lambdynlights:display` | `luminance`: one value or a list | the nested values, but only if the display entity has no brightness override |
| `lambdynlights:display/block` | none | block display: light emission of its block state |
| `lambdynlights:display/item` | none | item display: luminance of its stack |

The types that name a specific entity class return 0 for any other entity. Other mods can register more types through the API.

### 2.4 Conditions

On Fabric, if the file has the key `fabric:load_conditions`, it is decoded with Fabric API's `ResourceCondition.CONDITION_CODEC` (a single condition object or a list, a list means "all") and tested with the current registries. If the test is false the file is skipped silently. If the conditions cannot be **decoded** (for example an unknown condition type), an error is logged and the file is **loaded anyway** (defect 19). `neoforge:conditions` is just an unknown key on Fabric.

Fabric API 0.116.17 itself knows these condition types: `fabric:true`, `fabric:not`, `fabric:and`, `fabric:or`, `fabric:all_mods_loaded`, `fabric:any_mods_loaded`, `fabric:tags_populated`, `fabric:features_enabled`, `fabric:registry_contains`. Other mods can register more.

### 2.5 Fresh examples

An item source with a list of ids:

```json
{
  "match": { "items": ["examplemod:brass_lamp", "examplemod:brass_lamp_lit"] },
  "luminance": 13,
  "water_sensitive": true
}
```

An item source with a tag and a block-derived value:

```json
{
  "match": { "items": "#examplemod:glowing_minerals" },
  "luminance": { "type": "block", "block": "minecraft:sea_lantern" }
}
```

An item source that only applies to enchanted stacks (the sub-predicate syntax is vanilla 1.21.1, written from memory):

```json
{
  "match": {
    "items": "#minecraft:swords",
    "predicates": {
      "minecraft:enchantments": [
        { "enchantments": "minecraft:fire_aspect", "levels": { "min": 1 } }
      ]
    }
  },
  "luminance": 9
}
```

An entity source with a list of luminance values and a condition:

```json
{
  "fabric:load_conditions": [
    { "condition": "fabric:all_mods_loaded", "values": ["examplemod"] }
  ],
  "match": { "type": ["examplemod:ember_sprite", "examplemod:ember_golem"] },
  "luminance": [
    5,
    { "type": "lambdynlights:wet_sensitive", "dry": 12, "wet": 2 }
  ]
}
```

An entity source that follows what the entity rides in:

```json
{
  "match": {
    "type": "minecraft:pig",
    "passenger": { "type": "minecraft:player" }
  },
  "luminance": { "type": "lambdynlights:value", "value": 6 }
}
```

### 2.6 What the three pack jars really contain

All files were extracted and parsed. All 105 files are valid JSON.

| Jar | Folder inside the jar | Files | Top-level keys | `match` form | `luminance` form | Other | What upstream does with them |
|---|---|---|---|---|---|---|---|
| `amendments-1.21-2.1.10-fabric.jar` | `assets/supplementaries/dynamiclights/item/` | 21 (`candle_holder.json`, `candle_holder_<colour>.json`) | `match`, `luminance`, `water_sensitive`, `fabric:load_conditions`, `neoforge:conditions` | `items` as **one id string** | integer, always 8 | `water_sensitive` always `true` | 17 load. 4 are skipped by their conditions (they need `buzzier_bees`, `endergetic`, `caverns_and_chasms` or `cave_enhancements`, none in the pack) |
| same | same folder | 21 (`*_legacy.json`) | **`item`** (one id string), `luminance`, `water_sensitive`, both condition keys | none (old pre-3.0 format) | integer 8 | `water_sensitive` `true` | Rejected: no `match` key. 17 warnings at every apply (the other 4 are skipped by conditions first) |
| same | `assets/supplementaries/ryoamiclights/dynamiclights/` | 21 | same shape as the legacy files | none | integer 8 | another mod's folder layout | Never read (not under `dynamiclights/item` or `dynamiclights/entity`) |
| `simplyswords-fabric-1.70.2-1.21.1.jar` | `assets/simplyswords/dynamiclights/item/` | 41 | `match`, `luminance`, `water_sensitive` | `items` as one id string | integer: 6 (4 files), 8 (1), 9 (7), 10 (10), 11 (2), 12 (8), 13 (5), 14 (4) | `water_sensitive` always `false`, written out | All load |
| `friendsandfoes-fabric-4.0.27+mc1.21.1.jar` | **`data/lambdynlights/dynamiclights/entity/`** | 1 (`friendsandfoes_wildfire.json`) | `match`, `luminance` | `type` as one id string (`friendsandfoes:wildfire`) | object: `lambdynlights:wet_sensitive` with integer `dry` 10 and `wet` 4 | none | Never read: it is under `data/`, upstream only reads `assets/` |

Corrections to the task description: the Amendments jar has 42 files in the upstream folder (21 current, 21 legacy) plus 21 in a RyoamicLights folder, and its namespace is `supplementaries`. The Friends&Foes file is not under `assets/`.

What our reader must support for these jars:

1. Item files: `match.items` as a single id string; integer `luminance`; boolean `water_sensitive`. Nothing else is used: no lists, no tags, no components, no sub-predicates, no object luminance.
2. Entity file: `match.type` as a single id string; `luminance` object of type `lambdynlights:wet_sensitive` with integer `dry` and `wet`.
3. `fabric:load_conditions` as a list of objects `{"condition": "fabric:mod_loaded", "modid": "<id>"}`, 1 to 3 entries, all must hold. **`fabric:mod_loaded` is not a Fabric API condition type.** In this pack it is registered by Moonlight Lib (`moonlight-1.21.1-3.7.0-fabric.jar`, class `ResourceConditionsBridge$ModLoadedCondition`, key `modid`), together with `fabric:false`. If we evaluate conditions through Fabric API's registry it resolves as long as Moonlight is installed. A safer reader also understands `fabric:mod_loaded` itself.
4. `neoforge:conditions` must be ignored without a warning.
5. The legacy shape (`item` instead of `match`) should be either accepted as an alias or skipped quietly. Each legacy file has a current twin with the same item and value, so skipping loses nothing. Do not log 17 warnings per apply.
6. To make the wildfire glow we must decide on purpose to also read `data/<namespace>/dynamiclights/` from mod jars. A client resource manager does not see `data/`.
7. Items named in a file may not exist. Such files must be skipped quietly.

Also relevant: `accessorify-2.4.0-beta.5` in the pack adds a lantern accessory and lists `lambdynlights` under `recommends`. It has no light code of its own; it relies on the Accessories slot scan.

---

## 3. Hook points into Minecraft 1.21.1

### 3.1 Mixins

Thread column: **R** = render (main client) thread only. **W** = also chunk-meshing worker threads. All names are Mojang names, verified in the 1.21.1 jar.

**Light value hooks**

| Mixin | Target | Kind | Purpose | Thread |
|---|---|---|---|---|
| `CommonLevelRendererMixin` (priority 900) | `LevelRenderer.getLightColor(BlockAndTintGetter, BlockState, BlockPos)` (static) | `@ModifyReturnValue` at every return (MixinExtras) | Fetches the block state at the position again, and if it is not solid-render and the mode is not `off`, merges dynamic light into the returned packed light (1.1) | **W** and R |
| `EntityRendererMixin` | `EntityRenderer.getBlockLightLevel(T, BlockPos)` | `@ModifyReturnValue` at return | If mode is on: returns the entity's own luminance if it is 15, else the maximum of the vanilla value, the entity's own luminance and the truncated dynamic light at the position | R |
| `fapi.AoCalculatorMixin` (`@Pseudo`, `require = 0`) | Fabric Indigo `net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator.getLightmapCoordinates(BlockAndTintGetter, BlockState, BlockPos)` (static) | `@Inject` at the **first** return, cancellable | Same merge as the first row, for Indigo's own light path (3.4 d) | **W** and R |

**Tick and lifecycle hooks**

| Mixin | Target | Kind | Purpose | Thread |
|---|---|---|---|---|
| `ClientLevelMixin` | `ClientLevel.tickEntities()` | `@Inject` HEAD | Start of tick: read the mode, advance the tick counter | R |
| | `ClientLevel.tickEntities()` | `@Inject` TAIL | End of tick: rebuild the spatial lookup, schedule section rebuilds (4.2) | R |
| | `ClientLevel.tickNonPassenger(Entity)` | `@Inject` after the call to `Entity.tick()` | Per-entity light tick | R |
| | `ClientLevel.tickPassenger(Entity, Entity)` | `@Inject` after the call to `Entity.rideTick()` | Per-entity light tick for passengers | R |
| | `ClientLevel.removeEntity(int, Entity.RemovalReason)` | `@Inject` RETURN with the local `Entity` captured | Untrack the entity. Needed because this method calls `Entity.setRemoved` directly, not `Entity.remove` | R |
| `EntityMixin` | `Entity.remove(Entity.RemovalReason)` | `@Inject` TAIL | Untrack the entity, client side only | R (and server thread for server entities, where it does nothing) |
| `MinecraftClientMixin` | `Minecraft.updateLevelInEngines(ClientLevel)` | `@Inject` HEAD | Level change or disconnect: drop all sources, reset the lookup size, replace or drop the scheduler | R |
| | `Minecraft.tick()` | `@Inject` RETURN | Poll the key binding | R |
| `ParticleEngineMixin` | `ParticleEngine.tickParticle(Particle)` | `@Inject` after `Particle.tick()` | Per-particle light tick (out of scope) | R |

**State added to game classes**

| Mixin | Target | What it adds | Thread |
|---|---|---|---|
| `lightsource.EntityMixin` | `Entity` | Implements the light-source interface on every entity. Fields: luminance, last scheduled luminance, last scheduled position (three doubles), set of tracked section keys. Light id = entity id; position = X, eye Y, Z | fields written on R, read on **W** |
| `lightsource.LivingEntityMixin`, `PlayerEntityMixin`, `PrimedTntEntityMixin`, `AbstractMinecartEntityMixin`, `FallingBlockEntityMixin` | `LivingEntity`, `Player`, `PrimedTnt`, `AbstractMinecart`, `FallingBlockEntity` | Override the light tick (1.2). The TNT mixin also injects at the TAIL of the constructor `PrimedTnt(EntityType, Level)` to remember the fuse, and shadows `PrimedTnt.getFuse()`. The falling block mixin shadows the private field `blockState` | R |
| `lightsource.ParticleMixin`, `SonicBoomParticleMixin` | `Particle`, `SonicBoomParticle` | The same interface and fields on every particle (out of scope) | R |
| `lightsource.GuardianEntityMixin`, `BeaconBlockEntityMixin`, `TheEndGatewayBlockEntityMixin` | `Guardian`, `BeaconBlockEntity` (inject in static `tick` at a field access, and at RETURN of `setRemoved`), `TheEndGatewayBlockEntity` (inject at RETURN of static `beamAnimationTick`) | Beam and laser sources (out of scope) | R |
| `EntityTypeMixin` | `EntityType` | A lazily created per-type on/off setting, plus name and id helpers for the settings list | R |
| `LevelRendererMixin` | `LevelRenderer` | Getter for the culling frustum: private field `capturedFrustum` if set, else private field `cullingFrustum` | R |
| `LevelRendererAccessor` | `LevelRenderer.setSectionDirty(int, int, int, boolean)` (private) | `@Invoker`, used to mark one section dirty. Always called with the boolean `false` | R |

**GUI and misc**

| Mixin | Target | Kind | Purpose |
|---|---|---|---|
| `OptionsMixin` | `Options.load()`, field `keyMappings` made mutable | `@Inject` HEAD | Register the key binding |
| `VideoSettingsScreenMixin` | `VideoSettingsScreen` constructor; `VideoSettingsScreen.addOptions()` at the call to `OptionsList.addSmall(OptionInstance[])` | `@Inject` TAIL; `@ModifyArg` | Add the settings button |
| `DebugScreenOverlayMixin` | `DebugScreenOverlay.getGameInformation()` | `@Inject` RETURN | F3 lines |
| `DebugRendererMixin` | `DebugRenderer.render(PoseStack, MultiBufferSource.BufferSource, double, double, double)` | `@Inject` TAIL | Debug renderers |
| `DevModeMixin` | `GameRenderer.render(DeltaTracker, boolean)` at the call to `GuiGraphics.flush()` | `@WrapOperation`, only in development builds | Overlay text |
| `sodium.SodiumOptionsGuiMixin`, `sodium.SodiumOptionsAPITabFrameMixin`, `sodium.RSOTabAccessor` | Sodium 0.6 GUI, Sodium Options API, Reese's Sodium Options | `@Pseudo`, only for Sodium 0.6 and older | Settings page redirect |
| `retrofit.DynamicLightHandlerMixin`, `retrofit.DynamicLightHandlersMixin` | upstream's own deprecated API classes | `@Overwrite`, `@Inject` | Legacy API implementation |

There is no mixin into chunk-meshing classes and no mixin into Sodium's renderer.

### 3.2 Access widener (`lambdynlights.accesswidener`)

| Entry (Mojang names) | Why |
|---|---|
| accessible class `net.minecraft.client.OptionInstance$ValueSet` | To implement a custom value set for the video settings button |
| accessible method `net.minecraft.world.entity.Display.getPackedBrightnessOverride()I` | Display entity rule: only glow without a brightness override |
| accessible method `net.minecraft.world.entity.Display$BlockDisplay.getBlockState()` | Block display luminance |
| accessible method `net.minecraft.world.entity.Display$ItemDisplay.getItemStack()` | Item display luminance |
| accessible method `net.minecraft.client.renderer.culling.Frustum.cubeInFrustum(DDDDDD)Z` | Frustum test for a section box in the culling scheduler |

All five are private in vanilla 1.21.1 (checked).

### 3.3 Other entry points

- Client initialiser: loads the config, adds a crash report section, registers the two reload listeners through Fabric's `ResourceManagerHelper` for client resources, subscribes to `CommonLifecycleEvents.TAGS_LOADED`, calls other mods' initialisers.
- Mod Menu and Sodium entrypoints (1.8).

### 3.4 How the raised light reaches the screen

**(a) Vanilla chunk meshes.** The block and fluid renderers ask `LevelRenderer.getLightColor` for the packed light of each sampled position: `ModelBlockRenderer` (two call sites, the three-argument form), `LiquidBlockRenderer` (the two-argument form, which fetches the state and calls the three-argument form). The level object is a `RenderChunkRegion`. The work runs on the section-compile worker threads of `SectionRenderDispatcher`, and on the render thread for synchronous near rebuilds. The hook raises the block part of the returned value, and the raised value is baked into the mesh vertices. Because the value is baked, the mod must mark sections dirty whenever the light changes (4.5). With smooth lighting the neighbouring samples are averaged by vanilla, which gives the soft gradient. Fractional light (1/16 steps) survives.

**(b) Sodium 0.8.x chunk meshes.** Sodium has its own mesher but still calls the same vanilla method. Verified in the pack's `sodium-fabric-0.8.13+mc1.21.1.jar`, class `net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess`, method `compute(int, int, int)`:

1. It reads the block state and several flags. If the block is solid-render and emits no light, it stores block light 0 and sky light 0 and does **not** call `getLightColor`.
2. If the block has emissive rendering, it reads raw block and sky light from the level and does not call `getLightColor`.
3. Otherwise it calls `LevelRenderer.getLightColor(level, state, pos)`, where the level is Sodium's own level slice (not a `ClientLevel`), and splits the result with `LightTexture.block(int)` and `LightTexture.sky(int)`.
4. It packs block light into **4 bits** of a cache word. `LightTexture.block` already drops the low 4 bits of the packed value. So the fractional part of the dynamic light is **lost**: under Sodium every position has a whole light level, the floor of the dynamic value.

Results are cached per position for one section build (`ArrayLightDataCache`), so the hook is called at most once per position per build. It runs on Sodium's chunk-build threads. Sections are marked dirty through the same `LevelRenderer.setSectionDirty(int, int, int, boolean)`, which Sodium overwrites to call `SodiumWorldRenderer.scheduleRebuildForChunk` and then `RenderSectionManager.scheduleRebuild(x, y, z, important)`. That method (bytecode read): asserts the render thread; invalidates Sodium's cloned-section cache for that position; looks the section up by key and ignores unknown or not-yet-built sections; uses update type "rebuild", or "rebuild + important" only if `important` is true **and** the section is near the camera; and if the pending update type actually changes, stores it and marks Sodium's visibility graph dirty. Upstream always passes `false`, so its rebuilds never get the "important" priority.

**(c) Entities, block entities, particles.**
- Entities: `EntityRenderer.getPackedLightCoords(T, float)` builds the packed light from `getBlockLightLevel` and `getSkyLightLevel` at the entity's light probe position. The hook on `getBlockLightLevel` raises the block part per frame. Whole levels only (the value is truncated). Renderers that override `getBlockLightLevel` without calling the base method are not affected. Held items in first person are lit through the same path (`EntityRenderDispatcher.getPackedLightCoords`).
- Block entities: `BlockEntityRenderDispatcher` calls the two-argument `LevelRenderer.getLightColor(level, pos)` with the `ClientLevel`. Hooked through (a)'s hook, per frame, on the render thread.
- Particles: `Particle.getLightColor(float)` calls the same two-argument method. Hooked the same way.
- Not hooked: entity shadows. `EntityRenderDispatcher` uses `LevelReader.getMaxLocalRawBrightness` for the shadow strength, so shadows ignore dynamic light (open upstream issue #278).

**(d) Fabric's Indigo renderer.** Without Sodium, Fabric API's Indigo meshes terrain (its `always-tesselate-blocks` setting defaults to true). Its light for a position comes from `AoCalculator.getLightmapCoordinates(level, state, pos)`, cached per section build in `ChunkRenderInfo`. Verified in Fabric API 0.116.17: that static method has two branches. If Indigo's flag `FIX_EMISSIVE_LIGHTING` is set (it is set to the same value as `fix-mean-light-calculation`, default true), it computes the packed light **itself** from raw sky and block light and returns — this is the first return, and it bypasses `LevelRenderer.getLightColor`. Otherwise it calls `LevelRenderer.getLightColor` — the second return, already covered by hook (a). Upstream's `fapi` mixin injects at the first return and applies the same merge. It is optional (`require = 0`, `@Pseudo`). With Sodium installed Indigo switches itself off (Sodium declares that it contains a renderer), so in our target environment this mixin does nothing.

### 3.5 With Iris shaders on

- Upstream has no Iris code at all. It does not detect shaders.
- Iris 1.8.14 does not touch the light query path: its jar has no reference to Sodium's `LightDataAccess` and none to `LevelRenderer.getLightColor`. So dynamic light still arrives in the chunk meshes as ordinary block light in the lightmap vertex attribute, and in the light coordinates of entities. Shader packs see it as normal block light and colour it like any block light. The whole-level truncation of 3.4 (b) applies.
- Shader packs that have their own handheld light add it on top, so held lights are counted twice. Upstream's answer is the `self` option and its key binding (changelog 4.4.0, issue #248). Iris offers `IrisApi.getInstance().isShaderPackInUse()` (present in the pack's Iris jar), which upstream does not use.
- Packs with voxel-based coloured lighting do not treat dynamic lights as coloured light (issue #306; the author says it cannot be fixed from the mod side).
- Entities that carry a light look over-bright with some packs because of the entity hook (issue #383, open).
- Not verified: whether Iris changes the `LevelRenderer` frustum fields that the culling scheduler reads (Iris's `MixinLevelRenderer` references `cullingFrustum`), and how sections that are only visible in the shadow pass interact with that scheduler (see defect 4).

---

## 4. Engine

### 4.1 Tracking of light sources

- The mod object holds one `HashSet` of active sources. A source is in the set exactly while its luminance is above 0 (and the mode is on). Members are entity objects themselves (every `Entity` implements the source interface through the mixin), particle objects, and wrapper objects for shaped sources.
- Entity equality and hash code in Minecraft are the network id, so the set treats two entity objects with the same id as equal.
- Two helper collections: `toAdd` (sources added since the last end-of-tick, used to force their first rebuild) and `toClear` (sources removed since the last end-of-tick, whose sections still need a clean-up rebuild).
- Per source (stored on the entity): current luminance; the luminance and position at the time of the last rebuild request; the set of section keys requested last time.

### 4.2 Tick flow (all on the render thread)

1. **Start of `ClientLevel.tickEntities()`.** Read the mode: set "ticking disabled" if it is `off`, remember the mode's minimum period, increase the tick counter. Nothing happens while the game is paused, because Minecraft does not call `tickEntities` then.
2. **After each entity's tick** (non-passengers and passengers):
   - Ask "should this source update now?" (mode period and adaptive period, 1.7). If not, stop.
   - If the entity is removed, untrack it. Otherwise, if the entity may emit (options and per-type setting), run its light tick (1.2), else set luminance 0.
   - Tracking update: if luminance is above 0 and the entity is not in the set, add it (also to `toAdd`). If luminance is 0 and it is in the set, remove it by scanning the set and add it to `toClear`.
   - Guardians additionally update their laser.
3. **Entity removal** (`Entity.remove` and `ClientLevel.removeEntity`): untrack as above.
4. **End of `ClientLevel.tickEntities()`:**
   1. Create the scheduler if there is none. Tell it a tick starts.
   2. Take the **write lock**. If the mode is on, rebuild the spatial lookup from the whole set (4.3). For each source in `toClear`, ask the scheduler to clean up its sections. Clear `toClear`. Release the lock.
   3. If ticking is enabled or a forced refresh is pending: for every source in the set, drop shaped sources that report themselves removed; skip sources that should not update this tick; otherwise compute the section map of the source (4.5), forced if a refresh is pending or the source is in `toAdd`, and pass a non-empty map to the scheduler. Then clear `toAdd`.
   4. Tell the scheduler the tick ends (the culling scheduler does its work here). Clear the forced-refresh flag.
5. **Level change or disconnect** (`Minecraft.updateLevelInEngines`): move every source to `toClear`, reset the lookup tables to their default size (without clearing them if they already have that size, and without the lock), then either create a fresh scheduler (new level) or drop the scheduler (no level).

### 4.3 Spatial lookup

- Space is divided into cubic **cells of 8 blocks** (cell coordinate = block coordinate shifted right by 3, so negative coordinates work). 8 is the light radius 7.75 rounded up.
- A cell coordinate triple is hashed to an integer key in `0 .. tableSize - 1`: a linear combination of the three cell coordinates with small odd factors, absolute value, another odd factor, then a bit mask. The table size is a power of two, 1024 at first.
- Two parallel arrays of that size: `entries` (objects) and `startIndices` (ints).
- Rebuild, every tick, from scratch:
  1. Every source is turned into one or more entry objects. An entity gives **one** entry: the key of the cell that contains its light position, and a reference to the entity. A shaped source gives one entry for every cell its bounding box touches, with a reference to the shape.
  2. If there are more entries than 95 % of the table size, both arrays are replaced by arrays of double size (logged) and step 1 is repeated. The table never shrinks, except to 1024 on level change.
  3. Both arrays are wiped. The entries are sorted by key and written to `entries` from index 0. For each key, `startIndices[key]` is set to the index of its first entry.
- What is stored is therefore **references to live game objects**, not copies of positions. The key is computed at rebuild time; position and luminance are read from the entity at lookup time.
- Two different cells can have the same key. Then a lookup also walks the other cell's entries. This costs time only; the distance test rejects them.

### 4.4 Light value for a block position

1. If the mode is `off`: 0.
2. Compute the cell of the position. For each of the **27 cells** in the 3×3×3 block around it: hash the cell, read `startIndices`, walk `entries` from there while the entry's key equals the cell's key, ask each entry for its light at the position (1.1 for entities; the shape's own function for shaped sources), keep the maximum.
3. Clamp to 0..15.

Scanning the neighbour cells is what makes one entry per entity enough: a block within 7.75 of a source is always in the source's cell or a neighbouring one. There is no early exit when the table is empty.

The caller in the chunk hook takes the **read lock** around step 1–3 when the level argument is not a `ClientLevel` (so for mesh workers), and takes no lock otherwise. It then merges the value into the packed light (1.1).

### 4.5 Which sections are rebuilt

For an entity source, when it is updated:

1. Compare its current light position and luminance with the values stored at the last request. If nothing is forced, the position moved by at most 0.1 on every axis and the luminance is the same: no sections. (The stored position is only replaced when a request is made, so slow movement adds up and is not lost.)
2. Otherwise, if luminance is above 0, collect the **2×2×2 block of 16-block sections** around the source: the section containing it, plus in each axis the neighbour on the side the source is closer to (local coordinate 8..15 → the next section, 0..7 → the previous one). These 8 sections always contain the whole 7.75 sphere.
3. Build a map: every section of the previous request gets "remove requested", then every section of the new set gets "requested" (overwriting). Store the new set, position and luminance on the entity.

So a moving light asks for 8 sections every time it is updated, plus the sections it just left when it crosses the middle of a section (4 for a crossing on one axis). The size of the set does not depend on the luminance: a luminance-4 source with a 2-block reach still asks for 8 sections. Section coordinates are not checked against the world height or the loaded area. Vanilla's `ViewArea.setDirty` wraps out-of-range coordinates with a modulo into the render grid (verified in bytecode), so a request above the build height dirties the bottom section of the column instead; Sodium ignores unknown sections.

For shaped sources the set is every section touched by the bounding box grown by half a section, old box "remove requested", new box "requested", recomputed only when the shape reports a change.

Marking a section dirty goes through `LevelRenderer.setSectionDirty(x, y, z, false)`. In vanilla, `false` means the section is recompiled on a worker thread, never synchronously. Vanilla only compiles dirty sections that are in its visible list (verified in `LevelRenderer.compileSections`); a dirty section outside the view waits.

### 4.6 The two rebuild schedulers

**Immediate.** Every section in the map is marked dirty at once, whatever its status. Sources being cleaned up: all their sections are marked dirty at once. No state.

**Culling (default).** Keeps a map from section key to a small map "source object → status". Statuses: *requested* (should be rebuilt because of this source, not done yet), *affected* (was rebuilt and now contains this source's light), *requested again* (affected, and the source changed), *remove requested* (the source's light must be removed from this section).

- Incoming "requested": new or still-requested entry stays *requested*; an *affected* entry becomes *requested again*.
- Incoming "remove requested": if the section is not tracked, ignore. If the source's entry was never rebuilt (*requested*), delete the entry: nothing to clean. Otherwise set *remove requested*.
- Source removed: the same rule for all of its sections.
- At the end of every tick, for every tracked section that has at least one status other than *affected*: test the section's box against the camera frustum (the frustum of the last rendered frame; if there is none yet, treat as visible). If it is visible: delete *remove requested* entries, turn the other pending ones into *affected*, mark the section dirty, drop the section from the map if it has no entries left. If it is not visible: leave everything as it is for a later tick.

The scheduler object is created when the first tick finds none and at every level change. Changing the option does not replace the current one (defect 15).

### 4.7 Adaptive ticking

The rules are in 1.7. In the engine they are one question, "should this source update on this tick?", asked in two places with the same answer: before an entity's light tick (4.2 step 2) and before its section request at the end of the tick (4.2 step 4.3). The question is answered from the main camera's position and view direction, the config distances and the tick counter. It is evaluated for every ticking entity on every tick, whether the entity emits or not, and it allocates one small vector each time (`LambDynLights.java:242-274`). It never changes what a lookup returns; it only delays luminance updates and section requests. When the mode is `off` the answer is always "no", which is also what breaks the forced refresh (defect 1).

### 4.8 Shared state and threads

| State | Written by | Read by | Protection |
|---|---|---|---|
| Lookup arrays `entries`, `startIndices` (the references and the contents) | Render thread: rebuild each tick; replaced on growth; replaced on level change | Mesh workers through the chunk hook; render thread (entity, block entity, particle light, F3, debug) | A `ReentrantReadWriteLock`. The tick rebuild holds the write lock. Mesh workers hold the read lock per lookup. Render-thread lookups and the level-change reset take **no** lock. Lock and unlock are not in try/finally |
| Per-entity luminance, last luminance, last position, tracked sections | Render thread (entity tick, end of tick) | Luminance: mesh workers (through lookup entries). The rest: render thread | None. Plain fields |
| Entity position (`Entity.getX`, `getEyeY`, `getZ`) | Render thread (entity tick, packets) | Mesh workers (through lookup entries) | None |
| Shaped source geometry (for example the two end points of a laser, mutated in place) | Render thread | Mesh workers | None |
| The set of sources, `toAdd`, `toClear` | Render thread | Render thread | Not needed, single thread (with the AsyncParticles mod, particle ticks are posted back to the main thread) |
| Scheduler maps | Render thread | Render thread | Not needed |
| Config values (mode, options) | Render thread (GUI, key binding) | Mesh workers (mode check in the hooks), render thread | None. Plain, non-volatile fields |
| Loaded JSON trees (`loadedLightSources`, an `ArrayList`) | Resource-reload worker thread (prepare stage) | Render thread (apply at reload end and at tags-loaded) | None |
| Decoded item and entity source lists | Render thread (apply) | Render thread (entity ticks) | Not needed |
| Config file | One background save thread | — | Hash compare, temp file and atomic move |

---

## 5. Defects

Paths are relative to `src/main/java/dev/lambdaurora/lambdynlights/` unless they start with `api/`.

### 5.1 Lights that stay, lights that do not appear, stale state

**1. Switching the mode to `off` leaves the dynamic light baked into the world.** CONFIRMED.
- Where: `LambDynLights.java:307-331` together with `:206-207` and `DynamicLightsConfig.java:357-364`.
- What is wrong: changing the mode between on and off sets a "force refresh" flag that is meant to rebuild every lit section once. The refresh loop runs, but for each source it first asks "should this source update now?", and that question answers "no" for every source while the mode is `off`. So every source is skipped and no section is marked dirty. Nothing else triggers a rebuild (there is no call to `LevelRenderer.allChanged()` anywhere).
- Trigger: stand next to a dropped glowstone, or hold a torch. Open the settings, set the mode to `off`, close.
- Result: the lit area stays lit. Each section returns to normal only when something else re-meshes it, so the world shows a patchwork. A light the player carried stays as a patch at the place where it was switched off. Fits open issue #398.
- The same gate weakens the opposite direction: when switching back on, only sources whose update tick happens to be the current tick are refreshed. In `fast` and `fastest`, and for far or behind-camera sources, the refresh is skipped and their surroundings stay dark until they move or change.

**2. With the default culling scheduler, removed or moved lights stay visible in sections that were out of view.** CONFIRMED.
- Where: `engine/scheduler/CullingChunkRebuildScheduler.java:181-207`.
- What is wrong: the clean-up rebuild of a section is postponed until the section's box is inside the camera frustum at the end of a tick. Until then the old mesh with the old light is kept.
- Trigger: walk forward with a torch, then turn around quickly. Or: a burning mob dies behind you, then you turn.
- Result: for at least one tick (50 ms) plus the meshing time, the sections behind show the light of a source that is gone or elsewhere: a trail of lit patches that then pop away one section at a time. Upstream's option text admits "extremely rare visual glitches". The frustum used is the one of the last rendered frame, so every fast camera turn hits this.

**3. The culling scheduler keeps references to removed entities, without limit, for sections that are never looked at again.** CONFIRMED.
- Where: `engine/scheduler/CullingChunkRebuildScheduler.java:37-38`, `:122-143`, `:190-207`.
- What is wrong: the per-section status map uses the source object (the entity) as key. An entry in state *remove requested* is only deleted when its section is visible at the end of a tick.
- Trigger: travel in one direction with a light, or let lit entities die or despawn in sections you then leave and never face again.
- Result: every such section keeps its entries, and each entry keeps an entity object alive, until the dimension changes. The map grows through a long session, and the end-of-tick pass walks all of it and runs a frustum test for every pending section, every tick.

**4. A section that was meshed for another reason while it had only a "requested" entry keeps that light forever.** PLAUSIBLE.
- Where: `engine/scheduler/CullingChunkRebuildScheduler.java:101-103` and `:129-131`.
- What is wrong: when a source leaves a section whose entry is still *requested*, the entry is deleted without a rebuild, on the assumption that the section never contained the light. But any other re-mesh of that section during that time (block change, first build) bakes the light in, because the lookup is global.
- Trigger: needs a section that is outside the camera frustum at every end of tick while the source is near, yet gets meshed in that time. With plain vanilla or Sodium, sections outside the frustum are not meshed, so this should not happen. With Iris shadows, sections seen only by the shadow pass may be meshed.
- Unverified: whether Iris's shadow pass meshes dirty sections outside the camera frustum, and which frustum the `LevelRenderer` fields hold during it.

**5. Everything from the old world stays in memory after a disconnect, until the next world ticks.** CONFIRMED.
- Where: `LambDynLights.java:460-482`, `:303-304`; `engine/DynamicLightingEngine.java:165-174`.
- What is wrong: on level change every source is moved into the `toClear` list, which is only emptied at the end of the next level tick. On disconnect there is no next tick until another world is joined. The lookup table is not cleared either: the reset only allocates new arrays if the size differs from 1024, otherwise the old entries, which reference old entities, stay. `toAdd` is not cleared. With the mode `off` the table is never rebuilt at all, so those references stay for the rest of the session.
- Trigger: hold a torch (the local player is then a source), leave the world, stay in the menu.
- Result: the entities hold their `ClientLevel`, so the whole old client world (chunks, entities, block entities) cannot be collected. When the next world is joined, old and new world are in memory together for a moment. On the first tick in the new world the `immediate` scheduler also marks sections dirty at the old world's coordinates.

**6. In `fast` and `fastest`, and for throttled far sources, neighbouring sections show the light at different source positions.** CONFIRMED (follows directly from the design).
- Where: `engine/lookup/SpatialLookupEntityEntry.java:26-38` (position and luminance read live at lookup time) against `LambDynLights.java:323-327` (rebuilds throttled).
- Trigger: mode `fastest`, walk with a torch and break blocks. Each broken block re-meshes its section at once with the current torch position, while the neighbours were last rebuilt up to 10 ticks ago.
- Result: visible steps in brightness along section borders. The same happens in `fancy` at smaller scale, because the sections of one request are meshed on different threads in different frames and each reads the position it finds at that moment (compare closed issue #236).

**7. Entities that exist but are not ticked never get their light evaluated.** CONFIRMED.
- Where: `mixin/ClientLevelMixin.java:34-48` (the only places where luminance is computed).
- Trigger: `/tick freeze` on the server and then drop a torch or set a mob on fire; or an entity in a chunk the client does not tick.
- Result: no light until the entity ticks again. An entity that was lit stays lit at its last values. The local player is not affected by tick freeze.

**8. Under Sodium the dynamic light loses its fractional part.** CONFIRMED.
- Where: `mixin/CommonLevelRendererMixin.java:23-32` is the only terrain hook, and `LambDynLights.java:389-391` puts the fraction into the low 4 bits of the packed value. Sodium discards exactly those bits (`LightDataAccess.compute` and `packBL`, 3.4 b). There is no Sodium-specific hook.
- Trigger: any dynamic light with Sodium installed, which is our target.
- Result: light moves in whole levels per block instead of sixteenths. A slowly moving light makes blocks at the edge of its reach jump between two levels: visible flicker and banding (issue #351, closed by the author as "an issue with Sodium itself", not fixable in upstream's design). `HOW_DOES_IT_WORK.md` itself explains that the fractional value is what makes the light smooth.

**9. Invisible entities emit nothing, also the local player.** CONFIRMED (deliberate in the code, reported as a bug: issue #394).
- Where: `mixin/lightsource/EntityMixin.java:129-131`, `mixin/lightsource/LivingEntityMixin.java:21-24`.
- Trigger: drink an invisibility potion while holding a torch.
- Result: the torch stops lighting the world, in first person too. For our mod this should be a decision, not an accident.

### 5.2 Data files and config

**10. One syntactically broken data file makes the whole resource reload fail.** CONFIRMED.
- Where: `resource/LightSourceLoader.java:142-165`.
- What is wrong: parsing uses Gson's `JsonParser.parseReader`, which throws `JsonSyntaxException` on bad syntax. The catch block only handles `IOException` and `IllegalStateException`. The exception leaves the reload listener's prepare future. The same happens when `silence_error` is a JSON object (`getAsBoolean` throws `UnsupportedOperationException`).
- Trigger: any pack with a `dynamiclights/item/*.json` that has a missing comma or bracket.
- Result: Minecraft treats the reload as failed (bytecode of `Minecraft.rollbackResourcePacks`, `clearResourcePacksOnError` and `abortResourcePackRecovery` read). If more than one pack is selected it clears the user's whole resource pack selection, **saves that to the options file**, and reloads. If the bad file is in a user pack, that second reload works and the user has lost the pack selection. If the bad file is in a mod jar (always loaded), the second reload fails too and the game drops to the title screen with a failure toast and incompletely loaded resources. If only one pack is selected the exception is rethrown and the game crashes.

**11. A `match` object without any recognised key matches everything.** CONFIRMED.
- Where: `api/src/main/java/.../api/item/ItemLightSource.java:31` (vanilla item predicate with only optional keys); `api/src/main/java/.../api/entity/EntityLightSource.java:87-131` (all keys optional).
- Trigger: `"match": {}` or a typo such as `"match": {"item": "minecraft:torch"}` (`item` instead of `items`).
- Result: every held item makes its holder glow with that luminance, or every entity glows. This is the "faint glow for every item" of issues #323 and #305, caused there by another mod's badly written files.

**12. Two threads use the list of loaded JSON trees without protection.** PLAUSIBLE.
- Where: `resource/LightSourceLoader.java:54`, `:109-114` (clear and fill on the prepare worker thread), `:123-134` (iteration on the render thread).
- Trigger: a resource reload is in its prepare stage (F3+T) at the moment the client receives a tags packet (server `/reload`).
- Result: a `ConcurrentModificationException` on the render thread, or an apply from a half-filled list, which removes data-driven lights until the next apply. Unverified: never observed, the window is small.

**13. Config values of the wrong type crash the game.** CONFIRMED for the library behaviour (NightConfig 3.8 bytecode read), not run.
- Where: `DynamicLightsConfig.java:212-228`; `config/BooleanSettingEntry.java:43-46`.
- What is wrong: only TOML syntax errors are handled. The typed reads are unchecked casts.
- Trigger: `mode = 3`, or `slow = "five"` under `[adaptive_ticking]`, or `self = "true"` under `[light_sources]`, written by hand or by a broken tool.
- Result: `ClassCastException` during mod initialisation (string keys and integer keys), or at first use in the world (booleans). The game does not start, or crashes on joining.

**14. Numeric config values are not range-checked.** CONFIRMED.
- Where: `DynamicLightsConfig.java:220-221`, `:391-405`.
- Trigger: `slow = 0` → every source not exactly at the camera updates only every 5 ticks, including the player's own light in third-person view. `slow = 3000` → `(3000 * 16)^2` overflows a 32-bit integer, the threshold becomes meaningless. Negative values behave like their absolute value. `slow` larger than `slower` is accepted from the file.
- Result: silently wrong update rates.

**15. Changing the rebuild strategy has no effect until the next level change.** CONFIRMED.
- Where: `DynamicLightsConfig.java:378-381`; the scheduler is only created at `LambDynLights.java:291-293` and `:476`.
- Trigger: switch "Chunk Rebuild Strategy" in the settings while in a world.
- Result: the old scheduler keeps running. The option looks broken to the user.

**16. Only one accessory mod is queried.** CONFIRMED.
- Where: `compat/CompatLayer.java:53-59`.
- Trigger: Accessories and Trinkets both installed, an emitting item in a Trinkets slot, no bridge mod.
- Result: the Trinkets slot is ignored. In our pack this is probably hidden because `accessories_compat_layer` routes Trinkets through Accessories (not verified at runtime). The calls into Accessories and Trinkets are not wrapped in any error handling (only Curios is), so an API change in either mod would crash the entity tick. The current pack versions are compatible (signatures checked).

**17. The TNT "fancy" ramp assumes a fuse of 80.** CONFIRMED.
- Where: `mixin/lightsource/PrimedTntEntityMixin.java:34-37`, `:55-57`.
- What is wrong: the start fuse is read at the end of the constructor. On the client the real fuse arrives later as synced data, so the stored value is always the default 80. The smoothstep function used is not clamped.
- Trigger: TNT with a long fuse (`/summon tnt ~ ~ ~ {fuse:400}`): the ratio is above 1, the formula goes far below 0, and the TNT is dark until 80 ticks remain. TNT lit by another explosion (fuse 10 to 30) starts at high brightness at once.
- Result: wrong ramp. Low impact, and TNT light is off by default.

**18. The `{"any": ...}` form of the `light` location predicate can never be reached.** CONFIRMED by reading (relies on the rule that the first alternative of an either-codec that decodes wins).
- Where: `api/src/main/java/.../api/predicate/LightSourceLightPredicate.java:27-30`.
- What is wrong: the first alternative has only optional keys, so it accepts every object, including one that only has `any`. It then matches all light levels.
- Trigger: an entity file with `"location": {"light": {"any": {"max": 7}}}`.
- Result: the condition is silently always true.

**19. A file whose conditions cannot be decoded is loaded anyway.** CONFIRMED.
- Where: `platform/fabric/FabricPlatform.java:100-115`.
- Trigger: a condition type that no installed mod registers, for example Amendments' `fabric:mod_loaded` without Moonlight Lib.
- Result: an error line in the log and the file is applied as if it had no condition.

### 5.3 Thread safety

**20. The lookup tables are replaced without the lock on level change, and the read lock is not released on an exception.** PLAUSIBLE.
- Where: `LambDynLights.java:471` (reset outside the write lock), `:368-370` (lock and unlock without try/finally); `engine/DynamicLightingEngine.java:100-103`, `:151-153`, `:169-174` (the key is masked with the length of one array and then used as an index into the other array, and the two array fields are replaced one after the other).
- Trigger: the table has grown beyond 1024 (more than about 970 entries: many beacon or gateway beams, see issue #292), and the player changes dimension while a mesh worker of the old world is inside a lookup.
- Result: the worker computes a key for a 2048-size table and indexes a 1024-size array: `ArrayIndexOutOfBoundsException` inside a chunk build task, which is expected to end in a crash. If it does not, that worker never releases its read lock, and the next tick's write lock blocks the render thread forever.
- Unverified: the timing, and how Sodium and vanilla handle the failed build task. It was not reproduced.

**21. The "is this a `ClientLevel`?" test is used as "is this the render thread?".** PLAUSIBLE.
- Where: `LambDynLights.java:368-370`.
- What is wrong: lookups with a `ClientLevel` argument take no lock. Any mod that queries light with the real level from another thread (for example a mod that computes particle light off-thread) races with the per-tick rebuild: it can see wiped or half-filled tables (missing light for that query) or, during a growth, the index error of defect 20.
- Unverified: no such mod was found in the pack.

**22. Mesh workers read entity fields that the render thread is writing.** CONFIRMED as a data race, low impact.
- Where: `engine/lookup/SpatialLookupEntityEntry.java:26-38`; `mixin/lightsource/EntityMixin.java:56-67`.
- Result: no crash (plain reads of primitive fields and of an immutable position object), but no consistency either: see defect 6. For lasers the two end points are mutated in place, so a worker can see a half-updated line (out of scope).

**23. The config save thread is not a daemon thread.** PLAUSIBLE.
- Where: `DynamicLightsConfig.java:62`.
- Result: reported upstream as a thread "stuck on shutdown" (issue #396). Unverified: whether it can really keep the JVM alive depends on how Minecraft exits.

### 5.4 Chunk borders, rounding, world limits

No wrong result was found for negative coordinates in the in-scope code: cell and section coordinates use arithmetic shifts and floor, and the "which half of the section" test uses floor and a bit mask. The 2×2×2 section set always covers the light sphere. The findings in this area are:

**24. Section requests are not limited to the world height or the render grid.** CONFIRMED.
- Where: `engine/source/DynamicLightSource.java:62-88`; `engine/scheduler/ChunkRebuildScheduler.java:96-100`.
- Trigger: without Sodium, fly above the build limit with a torch (or stand at the edge of a small render distance).
- Result: vanilla wraps the out-of-range coordinate, so an unrelated section (the lowest one of the column, or one at the opposite edge of the grid) is marked dirty every tick. Wasted rebuilds, no wrong picture. With Sodium the request is ignored.

**25. Rebuilds are requested for sections the light cannot reach, and for faces just across the border not at all.** CONFIRMED, cosmetic.
- Where: `engine/source/EntityDynamicLightSourceBehavior.java:112-116`.
- The set is always 8 sections regardless of the reach (performance, see 6). In the other direction: a block one step outside the set can have a face whose light sample position is inside the sphere, and that face is not rebuilt. The largest possible error is below half a light level (the sample is at least 7.5 blocks from the source), so it is not visible in practice.

**26. Entity light is whole-level and sampled at one block.** CONFIRMED, cosmetic.
- Where: `mixin/EntityRendererMixin.java:32`.
- Result: entities step in brightness while terrain (without Sodium) changes smoothly.

### 5.5 Rebuild storms

**27. Every moving light re-meshes up to 8 sections 20 times per second, with no budget.** CONFIRMED.
- Where: `engine/source/EntityDynamicLightSourceBehavior.java:98-129` (threshold at `:108`, always the full set at `:112-116`); `LambDynLights.java:307-328` (no limit per tick).
- Trigger: several lights moving in different places at once: a group of burning mobs, a mob farm with fire, several players with torches.
- Result: more section rebuilds are requested per tick than the mesh threads can finish (10 separate moving lights are up to 1600 section rebuilds per second in `fancy`). All other chunk updates queue behind them, and the frame rate drops while moving (compare issue #401). Numbers and causes are in section 6, item 2.

### 5.6 User-visible problems from the issue tracker and changelog that the 1.21 branch still has

The 300 most recent issues and pull requests (numbers #159 to #403, open and closed, read through the GitHub API on 2026-10-09; bodies of the relevant ones and their comments were read) and the changelog were checked. Older issues were not read. Items that apply to 1.21.x and are not fixed in 4.8.11:

| Issue | State | Problem | Relation to the code |
|---|---|---|---|
| #398 | open | Mode setting "not respected": light lags, is tied to block coordinates, and `off` does not switch it off | Defects 1 and 8, and the 20 Hz tick-position update |
| #351 | closed, won't fix | Flicker at the edge of the light with Sodium | Defect 8 |
| #399 | open | Some entities (Create's blueprint) are lit only by dynamic lights at certain Y levels, 1.21.1 | Cause not found in the code read |
| #394 | closed | Invisible entities emit no light | Defect 9, still in this branch |
| #383 | open | Players holding lights look over-bright with shader packs | Entity hook returns the entity's own luminance (3.4 c) |
| #306 | closed, won't fix | No coloured light with coloured-lighting shader packs | Design limit (3.5) |
| #278 | open | Entity shadows ignore dynamic light | Shadow path not hooked (3.4 c) |
| #277 | open | Shadow glitches with smooth lighting depending on distance | Not analysed further |
| #231 | open | Slower chunk loading with the mod installed | Per-lookup cost, section 6 item 1 |
| #401 | open (newer Minecraft) | Frame rate halves while moving with a light | Rebuild volume, section 6 item 2 |
| #396 | closed | Save thread stuck on shutdown | Defect 23, the code is unchanged in this branch |
| #382, #346 | open / duplicate | Wish: light should fade in and out instead of popping | Not implemented |
| #259 | closed, won't fix | Wish: light should follow at frame rate, not tick rate | Design limit: tick positions, baked meshes |
| #338 | closed | Held light reaches less far than a placed torch | The 7.75 radius (1.1) |
| #323, #305, #309 | closed | All items glow when another mod ships wrong files | Defect 11 (still possible) |
| #292 | closed, mitigated in 4.4.0 | Lights stop working with very many sources | Table now grows; it never shrinks and the growth path has defect 20 |

Fixed in this branch and therefore only lessons: a null scheduler crash on joining a world (#358, #385; fixed by creating it lazily), a duplicated key binding that corrupted `options.txt` (#311, #386), a crash with AsyncParticles (#312).

---

## 6. Performance weak spots

No measurements were made. The ratings follow from how often the code runs and what it does each time.

**1. The cost of one light lookup — HIGH.**
The chunk hook runs for every light sample of every section that is meshed anywhere in the world, with or without lights nearby. Each call does, in this order (`mixin/CommonLevelRendererMixin.java:28`, `LambDynLights.java:367-372`, `engine/DynamicLightingEngine.java:83-115`):
- one extra block state fetch from the mesh region and an `isSolidRender` call, although the caller already has the state and Sodium has already excluded solid blocks;
- acquire and release of the shared read lock. All mesh threads write to the same lock word, so this is a contended cache line plus thread-local bookkeeping, twice per sample;
- two object allocations (a mutable and an immutable block position);
- 27 cell hashes and 27 reads of the start-index array, with the array fields re-read from the object each time because they are not final;
- for every candidate entry an interface call with three implementations, then virtual calls into the entity for X, eye Y and Z, and a square root when in range.
There is **no early exit**: with zero light sources all of this still happens, only the entry walks are empty. A section build makes several thousand samples (Sodium: at most once per position in the 20×20×20 block of its per-build light cache, minus solid blocks; vanilla and Indigo: more calls, softened by their own caches). A rough estimate is a few tenths of a microsecond per sample, so in the order of a millisecond per section build, on top of a build that takes about that long by itself. This matches the open report of slower chunk loading (#231).

**2. How much is rebuilt per moving light — HIGH.**
(`engine/source/EntityDynamicLightSourceBehavior.java:98-129`.) Any movement of more than 0.1 block on one axis, or any luminance change, asks for all 8 sections of the 2×2×2 set again: up to 160 section rebuilds per second for one walking player in `fancy`, 32 in `fast`, 16 in `fastest`. Each is a full re-mesh and upload of a 16×16×16 section although only light changed. The set is not reduced for weak lights (a luminance-8 light reaches 4.1 blocks and usually touches 1 or 2 sections), not reduced for sections that are empty, not limited per tick, and not ordered by distance. The culling scheduler removes sections outside the frustum, not occluded ones. With Sodium every request that changes a section's pending state also invalidates a cache entry and marks the visibility graph dirty (3.4 b). Requests are never "important", so near sections wait in the normal queue and the light visibly trails the player when the queue is busy.

**3. Per-entity work for entities that emit nothing — MEDIUM to HIGH with many mobs.**
(`engine/source/EntityDynamicLightSourceBehavior.java:77-96`, `LambDynLights.java:206-274`, `:605-634`.) Every ticking client entity pays, every tick: the camera test with one vector allocation; the per-type setting lookup; for living entities a fluid lookup at the eye block (one position allocation and a chunk access); an iteration over all equipment slots through a concatenated iterable; for every non-empty stack a **linear scan over all item light sources**, each a full vanilla item predicate test (about 70 sources in this pack: 11 built-in, 17 from Amendments, 41 from Simply Swords); then the accessory query (capability lookup and a fresh list of equipped stacks) for every living entity, mobs included; then a linear scan over all entity light sources; then a hash-set lookup for the tracking state. A zombie in full armour with a sword costs about 350 predicate tests per tick. A hundred armed mobs cost several hundred thousand per second.

**4. The spatial lookup is rebuilt from nothing every tick — MEDIUM.**
(`engine/DynamicLightingEngine.java:183-245`.) Even when no light moved: a stream over all sources, one stream object and one entry object per source, a list, a sort with a comparator, two full-array fills. All of it under the write lock, so every mesh thread that needs a light sample waits for it, and the render thread waits for all running samples before it can start.

**5. Particles — MEDIUM, and pure waste for our scope.**
(`mixin/ParticleEngineMixin.java:25-31`, `mixin/lightsource/ParticleMixin.java:38-50`, `engine/source/ParticleLightSourceBehavior.java:35-48`.) Every particle carries about 44 extra bytes and takes a number from a global atomic counter when created. Every particle, every tick: a liveness check, a type test, and a hash-set lookup by identity hash to see whether it is tracked. With thousands of particles this is tens to hundreds of thousands of set lookups per second for a feature that lights one particle type.

**6. Entity removal scans the whole source set, twice — LOW to MEDIUM.**
(`LambDynLights.java:444-455`, called from `mixin/lightsource/EntityMixin.java:69-73` and `mixin/ClientLevelMixin.java:50-56`.) Removing any entity, emitting or not, walks the set of sources with `equals` to find it, instead of a direct set removal, and both removal hooks do it. Cost grows with (entities removed) × (sources).

**7. The culling scheduler's bookkeeping — LOW to MEDIUM.**
(`engine/scheduler/CullingChunkRebuildScheduler.java:85-119`, `:157-229`.) A hash map of hash maps keyed by objects; a new inner map for every newly tracked section; per update of a moving source a new hash set, a new array map and several lambdas; a full pass over all tracked sections every tick with a frustum test for each pending one; a closure allocated every tick for the debug renderer even when it is off. Grows without limit together with defect 3.

**8. Work done while the mod is switched off — LOW to MEDIUM.**
The chunk hook tests "is the block solid" **before** "is the mod on", so the extra block state fetch of item 1 is paid for every light sample even when `mode = off` (`mixin/CommonLevelRendererMixin.java:28`; the Indigo hook has the same order). The entity and particle tick hooks, the end-of-tick lock, the two set scans per entity removal and the mixin fields on every entity and particle also remain.

**9. Memory — LOW.**
Every `Entity` (client and integrated server) gets two ints, three doubles and one reference from the mixin. The culling scheduler and the `toClear` list hold strong references to game objects (defects 3 and 5).

---

## 7. Ideas for a better design

Short recommendations for a from-scratch design. Ideas only.

**Light data**
- Publish the lights as one **immutable snapshot**, built on the render thread and handed over through a single volatile reference. Store plain numbers (position, luminance, squared reach), never entity references. Readers need no lock and can never see a half-built state, a leaked entity or a torn position. Sections meshed from the same snapshot agree with each other.
- Build a new snapshot only when something changed.
- Make the common case nearly free: first a single "any lights at all?" check; then a check whether the 16-block section of the queried position is touched by any light (a small hash set of section keys kept with the snapshot); only then look at the lights, and only at those listed for that section. Most of the world never gets past the second check.
- No allocation and no block-position objects on the lookup path. Use the block state the caller passes in. Check "mod on" first.
- Take the per-section candidate list once per section build where the renderer allows it (Sodium's per-build light cache is a natural place), with a thread-local "last section" cache as the general fallback.

**Rebuild scheduling**
- Per light, request only the sections its real sphere touches (reach from luminance), usually one or two.
- Collect all requests of a tick in one set of section keys, so each section is marked dirty at most once per tick, and apply a per-tick budget, nearest to the camera first, rest carried over.
- Keep one set "sections whose mesh may contain dynamic light". When a section leaves it, mark it dirty once. Use the same set to clean up when the mod is switched off, when the level changes and when data reloads. This replaces per-source status maps and cannot leak objects.
- Do not cull by frustum ourselves. Vanilla and Sodium only mesh dirty sections that are visible, and marking an already-pending section is cheap in Sodium. Just mark dirty at once. This removes the "trail when turning around" problem.
- Mark sections right next to the camera as important so Sodium treats them with priority. Make it an option.
- Clamp section coordinates to the level's height range before marking.
- Tie the movement threshold to the output precision and to the performance mode instead of a fixed 0.1 block.

**Sources**
- Track sources by entity id in a primitive map. Add and remove through Fabric's client entity load and unload events and clear everything on disconnect and level change. Hold no entity objects anywhere outside the tick.
- Decide per entity type in advance whether it can ever emit (data sources by type, living entities, burning or glowing state) and skip everything else in the tick.
- Pre-compute item luminance per item at data load into a table (dry value and wet value). Only items that have component-based rules need a per-stack test. Query accessory slots only for entities that can have them, ideally only players.
- Do not hook particles in 1.0.0.
- Make "invisible entities emit nothing" an explicit rule with a decision for the local player.

**Quality**
- Under Sodium the generic hook can only deliver whole light levels. If smooth light is wanted there, it needs a Sodium-specific path that adds the dynamic value where Sodium computes vertex light. That depends on Sodium internals; keep it optional, version-guarded, with the generic hook as fallback.
- Optional short fade when a light appears or disappears.
- For entities, block entities and particles (evaluated per frame) the snapshot can be read with interpolated positions.
- Offer "no own light while a shader pack is active" using Iris's API.

**Data files**
- Never let one file break a reload: catch everything per file, log once, go on.
- Reject a `match` without any usable constraint.
- Accept the legacy `item` key or skip such files quietly. Ignore `neoforge:conditions`. Evaluate `fabric:load_conditions` through Fabric API and additionally understand `fabric:mod_loaded`. If conditions cannot be read, skip the file.
- Decide whether to read `data/<namespace>/dynamiclights/` from mod jars (needed for the Friends&Foes file).

**Config and UI**
- Typed, validated config: clamp numbers, fall back per key on wrong types, never crash on a bad file. Save on a daemon thread.
- Apply every option at once, including the master switch (clean-up through the lit-section set).
- Register real options with Sodium's config API instead of only a link to a separate screen.

---

## Appendix: what could not be verified

- Nothing was run. Every defect rating comes from reading code and bytecode.
- Timing-dependent defects (12, 20, 21) and the Iris case of defect 4 were not reproduced.
- Iris: whether its shadow pass meshes sections outside the camera frustum, and what the `LevelRenderer` frustum fields contain during it.
- Whether `accessories_compat_layer` makes items in Trinkets slots visible through the Accessories API.
- Whether mob effects of other entities are available on the client (assumed not), which decides if the entity `effects` predicate is useful.
- The exact JSON key spellings of vanilla's entity flags predicate, and the vanilla block light values quoted in 1.2, are from memory.
- The cost numbers in section 6 are estimates.
- The cause of upstream issue #399 was not found.
- Whether a non-daemon save thread can really block the JVM exit (defect 23).
