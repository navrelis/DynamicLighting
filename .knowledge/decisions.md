# Decisions (Dynamic Lighting)

- Own code under MIT, no fork: upstream branch `1.21` is under the Lambda License, which keeps derivatives under that licence and ties binaries to the author's approval.
- Upstream source is studied, not copied: the owner asked for it; the study is prose in our own words, implementers work from the study and from Minecraft, Fabric and Sodium sources.
- The upstream clone stays in `F:\Coding\_upstream\LambDynamicLights`, outside our repository: its licence does not allow it inside an MIT repository.
- Build setup mirrors `F:\Coding\HungerDial` (Loom `fabric-loom-remap` 1.17-SNAPSHOT, Gradle 9.5.0, Mojang mappings, group `navrelis.*`): it is the owner's convention and is proven to build on this machine.
- All optional compile-only dependencies are added in T1: later parallel tasks then never edit `build.gradle`.
- Mod id `dynamiclighting`, no `provides: lambdynlights`: providing it would make Amendments and Simply Swords call an upstream API we do not ship.
- The pack repository is not committed to from this session: it belongs to session S2; the jar swap is done under the game lock and reported.
