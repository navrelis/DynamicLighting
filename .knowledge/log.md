# Log (Dynamic Lighting)

- 2026-10-09 Survey by lead: workspace, pack versions, upstream clone and licence, pack jars with light data. Questions sent, owner accepted the defaults and asked for the upstream study.
- 2026-10-09 T0 delegated (Opus): upstream study. Running.
- 2026-10-09 T1 (Sonnet): project scaffold. Accepted after review: all files read, `gradlew build` exit 0, 1 test passed, jar holds only own files, all four optional dependencies resolve at the pack versions.
- 2026-10-09 T0 (Opus): upstream study, 873 lines. Accepted after review: read in full; defects 1, 5, 10, 20 and weak spot 1 checked line by line against the upstream source, all correct; Sodium call of `getLightColor` confirmed in the pack jar. Two corrections to the survey: Amendments files are in namespace `supplementaries` (21 current, 21 legacy), the Friends and Foes file is under `data/`.
- 2026-10-09 T2 by lead: architecture and file ownership decided, see decisions.md and plan.md.
- 2026-10-09 T2a (Sonnet): contract skeleton. Accepted after review: diff and all seven new files read, signatures match the specification, build exit 0.
- 2026-10-09 T3 (Opus, engine), T4 (Opus, luminance and data), T5 (Sonnet, config and UI) delegated in parallel, each in its own worktree. Running.
- 2026-10-09 T5 (Sonnet): config and UI. Accepted after review: full diff and all new files read, build exit 0, 57 tests pass. Sodium entry point key and builder calls were verified by the agent against the real Sodium 0.8.13 jar; none of the UI has run in a client yet (checklist moved to T8). Merged into dev (ed05f7d).
- 2026-10-09 T3 (Opus): engine. Review: all ten engine classes and three mixins read in full; build exit 0, 76 tests. One correction sent (1 of 3): the tick must not stop while the game is paused, otherwise options changed in the screen do not apply at once (lead's own instruction was wrong). In review.
- 2026-10-09 T3 correction verified: pause check removed, build exit 0. Accepted and merged into dev (2c409bf). dev with T5 and T3 builds: 132 tests pass.
- 2026-10-09 T4 (Opus): luminance and data. Accepted after review: Luminance, LightRules, ItemTable, EntityLights, WornItems, loader, reader and compiler read in full; build exit 0. Agent fed all 84 real pack files through the reader in a throwaway test: no warnings. Merged into dev (b891d29). dev with all three parts: 218 tests pass, jar 126 KB.
- 2026-10-09 T6 (Sonnet, README, description, icon) and T7 (Opus, independent read-only review) delegated. Running.
