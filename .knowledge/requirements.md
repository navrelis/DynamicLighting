# Requirements (Dynamic Lighting, session 2026-10-09)

Cross-session board: `F:\Coding\.knowledge`. Pack board: `F:\Coding\NytheriaDevelopment\.knowledge`.

## Owner task (2026-10-09)
Download LambDynamicLights (branch `1.21`) and make it work in the NytheriaDevelopment instance. Find bugs, optimise, possibly write a whole new and more efficient implementation for Fabric 1.21.1. Licence must be MIT. Mod name: Dynamic Lighting.

## Facts found (verified 2026-10-09)
- Upstream clone (reference only, outside any repo of ours): `F:\Coding\_upstream\LambDynamicLights`, branch `1.21`, head c2e10b8, version 4.8.11, about 10 600 lines of Java.
- Upstream licence: `LICENSE` is the Lambda License (derivatives stay under it, binaries need written approval of the author). `LICENSE.OLD`: only commits before b964a12 were MIT. That commit is not in the shallow clone; its version is not yet checked.
- Upstream build: Loom 1.16, own Gradle plugin `lambdamcdev`, own mappings `yalmm`, bundles SpruceUI, pridelib, Yumi Commons / Foundation, Night Config. Optional: Mod Menu, Trinkets, Accessories, Sodium config API.
- Upstream technique (public `HOW_DOES_IT_WORK.md`): raise block light in the lightmap coordinates at render time, light = luminance * (1 - dist / 7.75), then schedule rebuilds of the affected chunk sections.
- Pack: Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Java 21 (Microsoft 21.0.10), Sodium 0.8.13, Iris 1.8.14-beta.1 (shaders off, pack Complementary Unbound + Euphoria), Mod Menu 11.0.5, YACL 3.8.2, Cloth Config 15.0.140, Accessories 1.1.0-beta.53, Trinkets 3.10.0.
- No dynamic lights mod is installed. Accessorify 2.4.0-beta.5 "recommends" mod id `lambdynlights`.
- Pack jars that ship LambDynamicLights data files (`assets/<ns>/dynamiclights/{item,entity}/*.json`): Amendments 2.1.10 (42), Simply Swords 1.70.2 (41), Friends and Foes 4.0.27 (1). Amendments and Simply Swords also contain compat classes for the upstream API.
- Pack repo `navrelis/Nytheria` is on branch `s2-ranks-shake-gui-2026-10-08`, owned by session S2. Other sessions may only swap their own jar in `mods` under the game lock `F:\Coding\.knowledge\game.lock` and report it.
- Tools: git 2.52, gh 2.100 logged in as `navrelis`, Graphify CLI present, no Gradle on PATH (wrapper).

## Owner answers (2026-10-09)
- Approach: study the upstream source, then write our own mod that is better, has none of its bugs and is far better optimised. Licence MIT. No fork.
- Upstream is studied on purpose (overrides the lead's default 2): behaviour, bugs and performance weak spots are written down in our own words; no upstream code is copied.
- No LambDynamicLights in the pack, not even as a stopgap.
- All other defaults accepted:
  - Scope v1.0.0: held items (both hands), Accessories and Trinkets slots, dropped items, burning entities, glowing mobs, projectiles, TNT and creeper fuse, torches going out under water, performance modes, config screen on vanilla widgets, Mod Menu and Sodium options entries, English and German. Left out: beam lights, particle lights, debug renderers, pride backgrounds, other languages.
  - Mod id `dynamiclighting`; reads existing `assets/<ns>/dynamiclights/{item,entity}/*.json` data files; does not provide `lambdynlights`, no upstream API.
  - Hard dependencies: Fabric Loader and Fabric API only. Sodium, Mod Menu, Accessories, Trinkets optional; nothing bundled.
  - Stack: Fabric Loom, official Mojang mappings, Java 21, Minecraft 1.21.1 only, client-side only. Folder `F:\Coding\DynamicLighting`.
  - GitHub: new public repo `navrelis/DynamicLighting`, default branch `main`, work on `dev`.
  - Pack: lead copies the jar into the instance `mods` under the game lock and launches the client for a smoke test in a throwaway world; the owner's four saves stay untouched. No CurseForge upload; jar plus description text.
  - Graphify: build once at the end, commit without cache.
