# Plan (Dynamic Lighting 1.0.0)

Status values: open / in progress / in review / done.

| ID | Task | Definition of done | Depends on | Model | Status |
|----|------|--------------------|------------|-------|--------|
| T0 | Upstream study: behaviour, data format, hook points, defects, performance weak spots, in our own words | `docs/upstream-study.md` exists; every defect has an upstream file and line and a trigger; lead spot-checked at least five claims against the source | none | Opus | done |
| T1 | Project scaffold, mirrors `F:\Coding\HungerDial` | `gradlew build` passes and produces `dynamiclighting-1.0.0.jar`; `genSources` done; optional compile-only dependencies resolve | none | Sonnet | done |
| T2 | Architecture: packages, contracts, threading rules, file ownership per task | Decisions in `decisions.md`; T3 to T5 can run in parallel without touching the same files | T0, T1 | lead | done |
| T2a | Contract skeleton: `config.Options` and its enums, stubs `ConfigManager.init`, `Luminance.init/ofEntity`, `LightEngine.init`, final entry point wiring | Build passes; signatures match the lead's specification exactly | T2 | Sonnet | in progress |
| T3 | Engine (`engine/`, `mixin/`, `dynamiclighting.mixins.json`): source tracking, immutable snapshot, lock-free lookup, light hooks (chunks, entities, Indigo), section rebuild queue with budget, modes | Unit tests for key packing, lookup maths, touched sections and the queue pass; a Fabric-Loader JUnit test proves `LevelRenderer.getLightColor` returns raised light and that all mixins apply | T2a | Opus | open |
| T4 | Luminance (`luminance/`, built-in data under `assets/dynamiclighting/dynamiclights/`): item table, entity rules, water rule, data file reader, worn items through Accessories and Trinkets | Unit tests pass for the reader (all shapes the pack jars use, broken files, empty `match`, unknown ids, conditions) and for item and entity luminance | T2a | Opus | open |
| T5 | Config and UI (`config/`, `gui/`, `integration/`, `fabric.mod.json`, lang files): validated JSON config, screen on vanilla widgets, key binding, Mod Menu and Sodium entries, English and German | Unit tests for config parsing and validation pass; every option has both translations; entry points verified against the Mod Menu and Sodium jars | T2a | Sonnet | open |
| T6 | README, CurseForge description, icon | README states features, options, data-file compatibility, licence; description text in `docs/`; icon referenced in `fabric.mod.json` | T3 to T5 merged | Sonnet | open |
| T7 | Independent code review of the merged code (threading, leaks, edge cases, leftovers) and fixes | Every confirmed finding fixed or recorded as a known limitation | T3 to T5 merged | Opus | open |
| T8 | Pack test: jar into the instance under the game lock, client smoke test in a throwaway world | No crash, no mixin error in the log; data files of Amendments and Simply Swords load; checklist items the lead can observe are checked | T6, T7 | lead | open |
| T9 | Final acceptance: full build and tests, leftovers, Graphify, report, push | Report in `.knowledge/report.md`; `dev` pushed | T8 | lead | open |

Parallel: T3, T4 and T5 run at the same time, each in its own git worktree, on disjoint files. Then T6 with T7.

## File ownership (T3 to T5)
- T3: `src/*/java/navrelis/dynamiclighting/engine/**`, `.../mixin/**`, `src/main/resources/dynamiclighting.mixins.json`.
- T4: `src/*/java/navrelis/dynamiclighting/luminance/**`, `src/main/resources/assets/dynamiclighting/dynamiclights/**`, `src/test/resources/**` for its fixtures.
- T5: `src/*/java/navrelis/dynamiclighting/config/**` (may add to `Options`, never rename or remove), `.../gui/**`, `.../integration/**`, `src/main/resources/fabric.mod.json`, `src/main/resources/assets/dynamiclighting/lang/**`.
- Nobody: `build.gradle`, `gradle.properties`, `DynamicLighting.java` (final after T2a).
