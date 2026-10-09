# Plan (Dynamic Lighting 1.0.0)

Status values: open / in progress / in review / done.

| ID | Task | Definition of done | Depends on | Model | Status |
|----|------|--------------------|------------|-------|--------|
| T0 | Upstream study: behaviour, data format, hook points, defects, performance weak spots, written in our own words | `docs/upstream-study.md` exists; every defect has an upstream file and line and a trigger; lead spot-checked at least five claims against the source | none | Opus | in progress |
| T1 | Project scaffold, mirrors `F:\Coding\HungerDial` | `gradlew build` passes and produces `dynamiclighting-1.0.0.jar`; `genSources` done; optional compile-only dependencies resolve or are reported | none | Sonnet | done |
| T2 | Architecture: packages, interfaces, threading rules, file ownership per task | Decisions in `decisions.md`; T3 to T7 instructions can be written without overlap | T0, T1 | lead | open |
| T3 | Engine: light source registry, spatial index, light lookup hook, section rebuild scheduling, performance modes | Unit tests for lookup maths and spatial index pass; hooks apply in a dev client without mixin errors | T2 | Opus | open |
| T4 | Luminance: held, worn and dropped items, entities, fuse and fire, water rule; reader for `dynamiclights/{item,entity}` data files; built-in defaults | Unit tests for the data reader pass, including the Amendments, Simply Swords and Friends and Foes files | T2 | Opus or Sonnet (decided in T2) | open |
| T5 | Config file, config screen on vanilla widgets, Mod Menu and Sodium options entries | Options persist across restarts; screen opens from Mod Menu and from Sodium's options | T2 | Sonnet | open |
| T6 | Accessories and Trinkets integrations | Compiles without either mod present at runtime; worn light items glow when the mod is present | T4 | Sonnet | open |
| T7 | English and German texts, README, CurseForge description | All option keys translated in both languages; README states licence and data-file compatibility | T5 | Sonnet | open |
| T8 | Pack test: jar into the instance under the game lock, client smoke test in a throwaway world | No crash, no mixin error in the log; manual checklist items checked where the lead can see them | T3 to T7 | lead | open |
| T9 | Final acceptance: full build and tests, leftovers, Graphify, report, push | Report in `.knowledge/report.md`; `dev` pushed | T8 | lead | open |

Parallel: T0 with T1. After T2: T3, T4 and T5 in parallel (separate packages), then T6 and T7.
