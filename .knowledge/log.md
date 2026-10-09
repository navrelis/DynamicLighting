# Log (Dynamic Lighting)

- 2026-10-09 Survey by lead: workspace, pack versions, upstream clone and licence, pack jars with light data. Questions sent, owner accepted the defaults and asked for the upstream study.
- 2026-10-09 T0 delegated (Opus): upstream study. Running.
- 2026-10-09 T1 (Sonnet): project scaffold. Accepted after review: all files read, `gradlew build` exit 0, 1 test passed, jar holds only own files, all four optional dependencies resolve at the pack versions.
- 2026-10-09 T0 (Opus): upstream study, 873 lines. Accepted after review: read in full; defects 1, 5, 10, 20 and weak spot 1 checked line by line against the upstream source, all correct; Sodium call of `getLightColor` confirmed in the pack jar. Two corrections to the survey: Amendments files are in namespace `supplementaries` (21 current, 21 legacy), the Friends and Foes file is under `data/`.
- 2026-10-09 T2 by lead: architecture and file ownership decided, see decisions.md and plan.md.
- 2026-10-09 T2a delegated (Sonnet): contract skeleton. Running.
