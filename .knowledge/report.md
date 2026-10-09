# Report: Dynamic Lighting 1.0.0 (2026-10-09)

Repository: https://github.com/navrelis/DynamicLighting, branch `dev`. Jar: `build/libs/dynamiclighting-1.0.0.jar` (132 305 bytes, sha256 `21150938790CA9FFD65C89BDC47FD37AA221A388D9C0FF932B0031DD6B237CB7`), installed in the pack's `mods` folder.

## Result in one paragraph
The mod is written from scratch under MIT, runs in the Nytheria pack (Fabric 1.21.1, Sodium 0.8.13, Iris, about 410 mods) and was checked in the real client. It does not have the defects found in LambDynamicLights 4.8.11. It is **not** "far better optimised" by measurement: in normal play there is no measurable difference, under heavy load (256 moving lights) it was about 10 % ahead in frame rate and CPU use in the two clean runs. Its own code is about eight times cheaper in the profile, but that is a small part of the total cost.

## What was implemented, per requirement
| Requirement | State |
|---|---|
| Download LambDynamicLights `1.21` and study it | Done. Clone in `F:\Coding\_upstream\LambDynamicLights` (outside the repository). Study in `docs/upstream-study.md`: behaviour, data format, hooks, 27 defects with file and line, 9 performance weak spots. Five claims checked by the lead line by line. |
| Licence MIT, name Dynamic Lighting | Done as own code. The upstream branch is under the Lambda License, so a fork could not be MIT. No upstream code is in the repository. |
| Works in the NytheriaDevelopment instance | Done and proven in the client (see checks). |
| No upstream bugs | The defects of the study are answered by design (see `decisions.md`); an independent review found no critical issue, its findings F1 to F5 and F7 are fixed. |
| Better optimised | Partly. See "Comparison". |
| Scope 1.0.0 | Held and worn items (incl. Accessories and Trinkets), dropped items, burning and glowing entities, mobs, projectiles, TNT and creeper fuse, water rule, modes, options screen, Mod Menu, Sodium page, key binding, `/dynamiclighting`, English and German. Added beyond the agreed scope: light range option (Short / Long), firework rockets, lightning, end crystals, own light while invisible. |
| Reads other mods' light data files | Done: in the pack 73 item rules and 1 entity rule load (Amendments, Simply Swords, built-in files, wildfire rule), 26 files skipped quietly, 0 with problems. |

## Changed files and why
- `src/main/java/navrelis/dynamiclighting/engine/` (10 classes), `mixin/` (3): snapshot, lookup, tracking, rebuild queue, light hooks.
- `.../luminance/` (19 classes), `assets/dynamiclighting/dynamiclights/` (17 files): what emits, item table, data reader, worn items.
- `.../config/`, `.../gui/`, `.../integration/`, `lang/en_us.json`, `lang/de_de.json`: config file, screen, key binding, command, Mod Menu, Sodium.
- `fabric.mod.json`, `dynamiclighting.mixins.json`, `icon.png`, build files, `LICENSE`.
- `README.md`, `CHANGELOG.md`, `docs/curseforge/` (summary, description, logo), `docs/upstream-study.md`.
- `src/test/`: 249 tests in 19 suites.
- `graphify-out/`: knowledge graph (1067 nodes, 3122 edges), built at the end, cache not committed.

## Key decisions (full list with reasons: `decisions.md`)
- Immutable snapshot of plain numbers behind one volatile reference; meshing threads read it without locks; no entity is ever stored.
- Three light hooks (LevelRenderer, EntityRenderer, optional Fabric Indigo); no mixin into Sodium, entities or particles.
- Sources found by one loop per client tick; a light is re-published only when it moved enough or changed.
- Rebuild set from the real reach; all-air sections never marked; per-tick budget that shrinks below 90 fps; retry for sections Sodium has not built yet.
- Data files handled one by one; a `match` without constraint is rejected.
- Mod id `dynamiclighting`; the upstream API is not provided.

## Checks and tests run
- `gradlew build`: exit 0, 249 tests, 0 failures (unit tests plus Fabric-Loader tests that call the hooked game methods).
- Independent code review (Opus, read-only) against the real jars: mixin remapping in the built jar, entry points, Sodium page, Accessories and Trinkets signatures, thread safety, leaks.
- In the real pack client, through the pack's test harness, throwaway worlds only, sound muted, settings restored and verified after every run:
  - start with the whole pack, jar in `mods`: no crash, no mixin error, no warning from the mod;
  - screenshots: held torch, lantern, dropped glowstone and clean-up after removal, primed TNT, blaze, burning zombie, glowing pig, torch dark under water and lit again when dry, lantern lit under water;
  - `/dynamiclighting` opens the screen; mode Off darkens the world behind the paused screen at once; Reset restores it;
  - Sodium video settings show "Dynamic Lighting 1.0.0" with eight native options in two groups; closing without Apply leaves the config file unchanged;
  - key binding registered, unbound.

## Comparison with LambDynamicLights 4.8.11 (official jar, measurement only, never in `mods`)
Scene: flat world at midnight, 64x4x64 field of leaves. Easy = sprinting with a torch (15 s). Extreme = 256 torch-carrying armour stands walking circles (20 s). Profile "player" (shaders off), one run per column.

| Round | Test | No mod | Dynamic Lighting | LambDynamicLights |
|---|---|---|---|---|
| q1 (old jar, other agents busy) | easy avg fps | 1440 | 1326 | 1409 |
| q1 | extreme avg fps | 81 | 43 | 53 |
| p1 (quiet, Flight Recorder on) | extreme avg fps / CPU cores | - | 58 / 8.0 | 52 / 9.0 |
| q2 (quiet, final jar) | easy avg fps / CPU cores | 1471 / 3.2 | 1497 / 3.2 | 1420 / 3.6 |
| q2 | extreme avg fps / CPU cores | 80 / 1.7 | 61 / 7.4 | 55 / 8.2 |
| q2 | extreme 1 % low fps / p99 ms | 35 / 20.7 | 23 / 29.1 | 23 / 32.2 |

- Easy: differences are inside the spread between runs (the idle window without any light differs by up to 8 %).
- Extreme: both mods cost a lot. Ours was ahead in the two clean rounds and behind in the first.
- Flight Recorder (extreme window): over 80 % of samples are chunk mesh threads in both arms. Our code: 1.6 % of mesh-thread samples, under 1 % of the render thread. LambDynamicLights' code: about 14 % and about 4 %. Better Biome Blend alone is 25 to 32 % of mesh samples; Accessories is over half of the render thread (256 armour stands).
- Not measured: more than one run per arm in the same state, shaders on, a real world instead of a flat one, low-end hardware.

## Manual test checklist (not verifiable by the lead)
- [ ] Look and feel while walking with a torch under Sodium: light moves in whole levels; compare "Short" and "Long" range.
- [ ] Right after joining a world or changing dimension, switch away from a torch: no lit patch stays behind (review finding F1, fixed, not reproducible by script).
- [ ] Shaders on (Complementary + Euphoria): dynamic light looks right, hand not too bright; the pack's own handheld light adds up with ours (switch one off).
- [ ] An item from Simply Swords and a candle holder from Supplementaries glow in the hand; the Friends and Foes wildfire glows.
- [ ] A light item in an Accessories or Trinkets slot lights the player within half a second.
- [ ] Mod Menu "Configure" opens the screen; Sodium "Apply" writes `config/dynamiclighting.json`.
- [ ] Bind "Toggle Own Light" and press it: message above the hotbar, light switches.
- [ ] German texts read well.
- [ ] On a multiplayer server: other players' torches, burning mobs.

## Known limits and recommendations
- Under Sodium light moves in whole levels (Sodium keeps four bits). A Sodium-internal vertex-light hook could fix it, at the price of depending on Sodium internals.
- A stricter rebuild cap under load was offered and declined by the owner: the update rate stays as it is.
- The frame-rate budget uses the raw frame rate, so a 60 fps cap gives 60 % of the budget. It only matters in very heavy scenes.
- Rules keep registry references of the previous server until the next world join (no world or entity is held).
- No coloured light, no beacon / guardian beam lights, no particle lights, no per-entity-type screen (`disabled_entity_types` is file-only).
- `dev` is not merged into `main`. No CurseForge upload was done; summary, description and logo are in `docs/curseforge/`.

## Left on the machine
- The throwaway worlds `PerfTest-dynlight` and `PerfTest-dynbench` were moved to the Recycle Bin on the owner's word.
- `config/dynamiclighting.json` in the instance (defaults).
- The pack repository (`navrelis/Nytheria`) shows the new jar as untracked; it belongs to session S2 and was not committed from here.
- Harness results under `F:\Coding\NytheriaDevelopment\.dev\perf\runtime\client\results\20261009-*` (labels `dynlight-*`, `dynbench-*`).
- The official LambDynamicLights jar exists only in the session's temp folder.
