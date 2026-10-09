# Graph Report - DynamicLighting  (2026-10-09)

## Corpus Check
- 101 files · ~58,066 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 7 file(s) not represented in the graph (top: (none) 3, .properties 2, .jar 1)

## Summary
- 1067 nodes · 3122 edges · 40 communities (31 shown, 9 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 427 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `82c2cf61`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- org.junit.jupiter.api.Test
- net.minecraft.world.entity.Entity
- .flush
- ItemTable
- .parse
- LightTrackerTest
- LightEngine.java
- .creeper
- LuminanceTest
- SodiumIntegration.java
- net.minecraft.resources.ResourceLocation
- LightHooksTest.java
- LightDataLoader.java
- 4. Engine
- assertequals
- DataFormatTest.java
- net.minecraft.world.level.block.state.BlockState
- TestGame.java
- LightHooksTest
- ConfigScreen.java
- Options
- TuningTest
- OptionsCodec.java
- .merge
- EntityLights.java
- DataRobustnessTest.java
- LuminanceTest.java
- .get
- .value
- DataCompiler.java
- Dynamic Lighting
- ModMenuIntegration.java
- Requirements (Dynamic Lighting, session 2026-10-09)
- TestRenderer
- FuseMode
- 3. Hook points into Minecraft 1.21.1
- gradlew
- log.md

## God Nodes (most connected - your core abstractions)
1. `Options` - 49 edges
2. `EntityLight` - 31 edges
3. `LightRules` - 28 edges
4. `DataFormatTest` - 28 edges
5. `RawFile` - 27 edges
6. `LightTrackerTest` - 27 edges
7. `DataCompiler` - 26 edges
8. `LightTracker` - 25 edges
9. `ItemTable` - 25 edges
10. `SkipReason` - 25 edges

## Surprising Connections (you probably didn't know these)
- `Config and UI` --references--> `Options`  [INFERRED]
  .knowledge/decisions.md → src/main/java/navrelis/dynamiclighting/config/Options.java
- `File ownership (T3 to T5)` --references--> `Options`  [INFERRED]
  .knowledge/plan.md → src/main/java/navrelis/dynamiclighting/config/Options.java
- `Plan (Dynamic Lighting 1.0.0)` --references--> `Options`  [INFERRED]
  .knowledge/plan.md → src/main/java/navrelis/dynamiclighting/config/Options.java
- `3.1 Mixins` --references--> `EntityRendererMixin`  [INFERRED]
  docs/upstream-study.md → src/main/java/navrelis/dynamiclighting/mixin/EntityRendererMixin.java
- `3.1 Mixins` --references--> `LevelRendererMixin`  [INFERRED]
  docs/upstream-study.md → src/main/java/navrelis/dynamiclighting/mixin/LevelRendererMixin.java

## Import Cycles
- None detected.

## Communities (40 total, 9 thin omitted)

### Community 0 - "org.junit.jupiter.api.Test"
Cohesion: 0.05
Nodes (28): org.junit.jupiter.api.BeforeAll, org.junit.jupiter.api.Test, CompiledData, Skip, DataKind, ENTITY, ITEM, DataReader (+20 more)

### Community 1 - "net.minecraft.world.entity.Entity"
Cohesion: 0.05
Nodes (46): 1.0.0 - 2026-10-09, Added, Changelog, Known limits, com.google.gson.JsonElement, com.google.gson.JsonObject, 2.6 What the three pack jars really contain, 5.2 Data files and config (+38 more)

### Community 2 - ".flush"
Cohesion: 0.06
Nodes (18): arrays, 2.3 Entity light source file, it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap, it.unimi.dsi.fastutil.longs.LongOpenHashSet, long2intmap, longiterator, objectiterator, FunctionalInterface (+10 more)

### Community 3 - "ItemTable"
Cohesion: 0.06
Nodes (24): accessoriescapability, it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap, net.minecraft.advancements.critereon.ItemPredicate, net.minecraft.world.entity.player.Player, net.minecraft.world.item.ItemStack, net.minecraft.world.level.block.Block, slotentryreference, slotreference (+16 more)

### Community 4 - ".parse"
Cohesion: 0.07
Nodes (8): org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.Arguments, org.junit.jupiter.params.provider.ValueSource, OptionsCodec, Result, LightSnapshot, OptionsCodecTest, LightSnapshotTest

### Community 5 - "LightTrackerTest"
Cohesion: 0.12
Nodes (8): it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap, MutableBlockPos, net.minecraft.client.multiplayer.ClientLevel, net.minecraft.world.phys.Vec3, org.junit.jupiter.api.BeforeEach, LightEngine, LightTracker, LightTrackerTest

### Community 6 - "LightEngine.java"
Cohesion: 0.08
Nodes (19): blockpos, chatscreen, clientcommandmanager, clientcommandregistrationcallback, clienttickevents, clientworldevents, command, inputconstants (+11 more)

### Community 7 - ".creeper"
Cohesion: 0.09
Nodes (16): 1.2 What emits light, mth, Building from source, Config file, Dynamic Lighting, Features, How it works, Known limits (+8 more)

### Community 9 - "SodiumIntegration.java"
Cohesion: 0.12
Nodes (20): bifunction, component, 1.1 The light model, 1.3 Water rules, 1.4 Fire, glowing, invisibility, spectators, 1.5 Out of scope for our 1.0.0 (short notes), 1.6 Config options, 1.7 Performance modes (+12 more)

### Community 10 - "net.minecraft.resources.ResourceLocation"
Cohesion: 0.14
Nodes (11): net.fabricmc.fabric.api.resource.SimpleResourceReloadListener, net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.PackResources, net.minecraft.server.packs.resources.Resource, net.minecraft.util.profiling.ProfilerFiller, nullable, Override, Provider (+3 more)

### Community 11 - "LightHooksTest.java"
Cohesion: 0.14
Nodes (15): aocalculator, blocks, entityrendererprovider, fluids, method, net.minecraft.core.BlockPos, net.minecraft.core.Direction, net.minecraft.world.entity.decoration.ArmorStand (+7 more)

### Community 12 - "LightDataLoader.java"
Cohesion: 0.11
Nodes (19): commonlifecycleevents, completablefuture, executor, fabricloader, fluidtags, holderlookup, it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap, itemstack (+11 more)

### Community 13 - "4. Engine"
Cohesion: 0.08
Nodes (24): 2.1 Where files are found, merging, timing, errors, 2.2 Item light source file, 2.4 Conditions, 2.5 Fresh examples, 2. Data file format, 4.1 Tracking of light sources, 4.2 Tick flow (all on the render thread), 4.3 Spatial lookup (+16 more)

### Community 14 - "assertequals"
Cohesion: 0.18
Nodes (16): assertequals, assertfalse, assertnotequals, assertnotnull, assertnotsame, assertsame, asserttimeoutpreemptively, asserttrue (+8 more)

### Community 15 - "DataFormatTest.java"
Cohesion: 0.14
Nodes (19): assertinstanceof, assertnull, blockitem, blockitemstateproperties, builtinregistries, candleblock, compile, datacomponents (+11 more)

### Community 16 - "net.minecraft.world.level.block.state.BlockState"
Cohesion: 0.22
Nodes (12): at, com.llamalad7.mixinextras.injector.ModifyReturnValue, 3.1 Mixins, net.minecraft.client.renderer.LevelRenderer, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.BlockAndTintGetter, org.spongepowered.asm.mixin.Mixin, org.spongepowered.asm.mixin.Pseudo (+4 more)

### Community 17 - "TestGame.java"
Cohesion: 0.12
Nodes (18): atomicmovenotsupportedexception, bootstrap, bytebuffer, charactercodingexception, filechannel, files, path, registries (+10 more)

### Community 19 - "ConfigScreen.java"
Cohesion: 0.13
Nodes (13): button, commoncomponents, linearlayout, net.minecraft.client.gui.screens.Screen, optioninstance, LightRange, LONG, SHORT (+5 more)

### Community 20 - "Options"
Cohesion: 0.18
Nodes (4): File ownership (T3 to T5), Plan (Dynamic Lighting 1.0.0), org.junit.jupiter.params.provider.MethodSource, Options

### Community 22 - "OptionsCodec.java"
Cohesion: 0.16
Nodes (12): arraylist, collectors, comparator, gsonbuilder, jsonarray, jsonobject, jsonparseexception, jsonparser (+4 more)

### Community 24 - "EntityLights.java"
Cohesion: 0.14
Nodes (13): abstractarrow, abstractminecart, creeper, display, enderman, fallingblockentity, glowsquid, itemframe (+5 more)

### Community 25 - "DataRobustnessTest.java"
Cohesion: 0.16
Nodes (12): assertthrows, bytearrayinputstream, entity, ioexception, item, jsonioexception, linkedhashmap, net.minecraft.server.packs.resources.ResourceManager (+4 more)

### Community 26 - "LuminanceTest.java"
Cohesion: 0.14
Nodes (13): dragonfireball, endcrystal, eyeofender, fail, fireworkrocketentity, largefireball, lightningbolt, minecart (+5 more)

### Community 27 - ".get"
Cohesion: 0.31
Nodes (5): net.minecraft.client.gui.components.CycleButton, net.minecraft.client.gui.screens.options.OptionsSubScreen, ConfigScreen, Override, Row

### Community 29 - "DataCompiler.java"
Cohesion: 0.20
Nodes (9): com.mojang.serialization.Codec, dataresult, entitytype, holderset, itempredicate, jsonops, net.minecraft.resources.RegistryOps, net.minecraft.tags.TagKey (+1 more)

### Community 30 - "Dynamic Lighting"
Cohesion: 0.29
Nodes (6): Compatibility, Dynamic Lighting, Licence and source, Options, Requirements, What emits light

### Community 31 - "ModMenuIntegration.java"
Cohesion: 0.47
Nodes (4): com.terraformersmc.modmenu.api.ConfigScreenFactory, com.terraformersmc.modmenu.api.ModMenuApi, Override, ModMenuIntegration

### Community 32 - "Requirements (Dynamic Lighting, session 2026-10-09)"
Cohesion: 0.33
Nodes (5): Facts found (verified 2026-10-09), Owner answers (2026-10-09), Owner, during the work (2026-10-09), Owner task (2026-10-09), Requirements (Dynamic Lighting, session 2026-10-09)

### Community 34 - "FuseMode"
Cohesion: 0.40
Nodes (4): FuseMode, FANCY, OFF, SIMPLE

### Community 35 - "3. Hook points into Minecraft 1.21.1"
Cohesion: 0.40
Nodes (5): 3.2 Access widener (`lambdynlights.accesswidener`), 3.3 Other entry points, 3.4 How the raised light reaches the screen, 3.5 With Iris shaders on, 3. Hook points into Minecraft 1.21.1

### Community 36 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **73 isolated node(s):** `OFF`, `SIMPLE`, `FANCY`, `SHORT`, `LONG` (+68 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 222 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Options` connect `Options` to `net.minecraft.world.entity.Entity`, `FuseMode`, `.parse`, `LightEngine.java`, `LuminanceTest`, `SodiumIntegration.java`, `net.minecraft.resources.ResourceLocation`, `LightDataLoader.java`, `DataFormatTest.java`, `ConfigScreen.java`, `LuminanceTest.java`, `.get`?**
  _High betweenness centrality (0.103) - this node is a cross-community bridge._
- **Why does `Mode` connect `ConfigScreen.java` to `LightTrackerTest`, `LightEngine.java`, `SodiumIntegration.java`, `Options`, `TuningTest`, `OptionsCodec.java`, `LuminanceTest.java`, `.value`?**
  _High betweenness centrality (0.033) - this node is a cross-community bridge._
- **Why does `Upstream study: LambDynamicLights 4.8.11 (branch `1.21`, Minecraft 1.21.1)` connect `4. Engine` to `SodiumIntegration.java`, `3. Hook points into Minecraft 1.21.1`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `Options` (e.g. with `Config and UI` and `File ownership (T3 to T5)`) actually correct?**
  _`Options` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `OFF`, `SIMPLE`, `FANCY` to the rest of the system?**
  _73 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `org.junit.jupiter.api.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.05186880244088482 - nodes in this community are weakly interconnected._
- **Should `net.minecraft.world.entity.Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.054455445544554455 - nodes in this community are weakly interconnected._