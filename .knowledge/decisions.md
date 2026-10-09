# Decisions (Dynamic Lighting)

## Project
- Own code under MIT, no fork: upstream branch `1.21` is under the Lambda License, which keeps derivatives under that licence and ties binaries to the author's approval.
- Upstream source is studied, not copied: the owner asked for it; the study is prose in our own words, implementers work from the study and from Minecraft, Fabric and Sodium sources.
- The upstream clone stays in `F:\Coding\_upstream\LambDynamicLights`, outside our repository: its licence does not allow it inside an MIT repository.
- Build setup mirrors `F:\Coding\HungerDial` (Loom `fabric-loom-remap` 1.17-SNAPSHOT, Gradle 9.5.0, Mojang mappings, group `navrelis.*`): the owner's convention, proven to build on this machine.
- All optional compile-only dependencies were added in T1: later parallel tasks never edit `build.gradle`.
- Mod id `dynamiclighting`, no `provides: lambdynlights`: providing it would make Amendments and Simply Swords call an upstream API we do not ship.
- The pack repository is not committed to from this session: it belongs to session S2; the jar swap is done under the game lock and reported.
- T3, T4 and T5 run in separate git worktrees after a small contract commit (T2a): three agents building in one Gradle project would see each other's half-written files and fight over build locks.

## Engine (answers to the study's defects and weak spots)
- One hook on `LevelRenderer.getLightColor(BlockAndTintGetter, BlockState, BlockPos)` serves vanilla meshes, Sodium 0.8.13 (`LightDataAccess` calls it, checked in the pack jar), block entities and particles; one on `EntityRenderer.getBlockLightLevel`; one optional on Fabric Indigo's `AoCalculator` for installs without Sodium. No mixin into Sodium, entities or particles: fewer compat risks, no per-entity fields.
- Lights are published as an immutable snapshot of plain numbers through one volatile reference: no lock, no entity references, no torn reads, sections meshed from one snapshot agree (study defects 5, 6, 20, 21, 22).
- Lookup order: "any light at all", then "is this 16-block section touched" (one probe in a primitive hash table), then only that section's lights; the solid-block test comes last and uses the state the caller passes in (study weak spots 1 and 8).
- Sources are found by one loop over the level's entities at the end of the client tick, state kept by entity id in a primitive map, everything dropped when the level object changes: no hooks into entity ticking or removal, frozen entities still work, nothing survives a disconnect (study defects 5, 7, weak spot 6).
- A source is re-published only when it moved more than a threshold tied to the fall-off and the mode, or its luminance changed; a standing light costs nothing; no new snapshot when nothing changed (study weak spots 2 and 4).
- Sections to rebuild come from the light's real reach, clamped to the level height, collected in one set per tick, nearest first, with a per-tick budget and carry-over; no frustum culling of our own (study defects 2, 3, 4, 24, 27).
- Switching the mod off, changing options or reloading data cleans up through the set of lit sections (study defects 1 and 15).
- Fall-off is linear, 2 levels per block (reach = luminance / 2, at most 7.5 blocks, so at most 8 sections) by default; option "long range" uses 1 level per block like vanilla light: answers "held light reaches less far than a placed torch" and reduces level steps under Sodium.
- Under Sodium the dynamic value is rounded to the nearest whole level instead of cut off; without Sodium sixteenths are kept: Sodium stores block light in 4 bits (study defect 8); a Sodium-internal vertex-light hook is left out of 1.0.0 as too fragile.
- Rebuilds are never flagged "important": in vanilla that means meshing on the render thread, which would cause frame hitches.
- Invisible entities emit nothing, except the local player: the owner's torch keeps working under invisibility, other invisible players are not given away (study defect 9).

## Luminance and data
- All "what emits" rules, options included, live behind `Luminance.ofEntity(Entity)`; the engine only decides where light goes.
- Item luminance is precomputed per item (dry and wet value) when data is applied; only items with component or count rules are tested per stack (study weak spot 3).
- Data files: same folder and shapes as upstream so pack mods work; per-file error handling; a `match` without any constraint is rejected; legacy `item` shape and `neoforge:conditions` skipped quietly; unknown ids skipped quietly; unreadable conditions skip the file; `fabric:mod_loaded` understood without Moonlight (study defects 10, 11, 12, 19).
- Entity `match` supports `type`, `flags` and `equipment`; files using other predicate keys are skipped with one warning: no pack jar uses them and a half-applied predicate would light the wrong entities.
- Friends and Foes ships its wildfire file under `data/`, where no client resource manager looks: a built-in file for `friendsandfoes:wildfire` covers it instead of scanning every mod jar's `data` folder.
- Accessories and Trinkets are both queried, players only, each call guarded, result cached for a few ticks (study defect 16, weak spot 3).
- No display-entity access widener: `Display.renderState()`, `BlockDisplay.blockRenderState()` and `ItemDisplay.getSlot(0)` are public.
- Own additions over upstream: firework rockets, lightning bolts and end crystals emit light; TNT light on by default.

## Config and UI
- Config is one validated JSON file read with Gson (already in Minecraft): no bundled config library; wrong types or ranges fall back per key (study defects 13, 14).
- Options are an immutable record behind one volatile reference: every change applies at once and is safe to read from any thread.
- Key binding through Fabric's key binding API, not a mixin into `Options` (upstream's cause of corrupted `options.txt`).
- Per-entity-type switches exist in the config file only (`disabled_entity_types`); no searchable list screen in 1.0.0.
- The engine keeps ticking while the game is paused: the options screen pauses single player, and option changes must show at once (corrects the first instruction for T3).
- No performance claim against LambDynamicLights is made in README, description or report unless a measurement supports it: the first quick comparison showed no advantage and a deficit with 256 moving lights.
- Rebuild volume is the cost that matters (Flight Recorder: over 80 % of samples in a 256-light scene are re-meshing, our own code under 2 %): sections holding only air are never marked, and the per-tick budget shrinks below 90 fps. The scale uses the raw frame rate, so a frame cap lowers the budget too; accepted, because one moving light needs about 6 sections per tick.
