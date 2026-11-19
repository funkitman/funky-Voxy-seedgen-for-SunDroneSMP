# Funky Voxygen for SunDroneSMP

Client-side Voxy LOD generator for **Minecraft 26.3 / Fabric**, based on Wenhan's [Voxygen 1.2.10](https://github.com/lyuwenhan/minecraft-java-edition-mods/tree/66c536b0666985e68376ed1ceb6a3dcc5c981cd3/voxygen) (MIT license).

**Status: experimental source release.** This fork adds opt-in per-server local worldgen datapacks. Minecraft 26.3 + Java 25 are required. Compilation and in-game correctness are not yet verified.

### Funky.2 terrain-correctness fix

- Multiplayer now decodes the actual `minecraft:dimension/overworld.json` generator from the selected local datapacks, rather than rebuilding a vanilla preset biome source. Terralith's explicit biome mapping is retained.
- Terratonic's datapack-overridden `minecraft:overworld` noise settings and original `final_density` noise router are preserved. Funky.1 accidentally replaced that density function with simplified vanilla terrain.
- This correctness-first mode may be slower than upstream's simplified generator; performance comes after matching real server terrain. Local ZIP priority order and worldgen pack versions must match the server.
- No arbitrary custom world generator is supported: fail closed unless the parsed Overworld generator is noise-based.
- Verify the resulting terrain against real server chunks outside the old 10,000-block vanilla spawn radius. This is a build-verified experimental release, not yet an in-game certified terrain match.

### Funky.3 diagnostic and safety controls

The exact source of the server's old versus new terrain is still unverified; **this build is NOT a proven terrain-match fix**.

- `/voxygen diagnose`: shows connected server, seed, profile, exact pack order, and whether dryrun is on. Detailed per-file SHA-256 and effective Overworld/density resource origin are printed in `latest.log`.
- `/voxygen dryrun on`: generate datapack virtual chunks but **do not write synthetic LOD to Voxy storage**; logs chunk coordinates, surface height, and biome for comparison with real server chunks. Use this first outside the 10,000-block old vanilla area. `/voxygen dryrun off`: opt into writes *after* validation.
- Dryrun generates even where Voxy already has cached chunks, which makes it useful for spotting incorrect generator output. It cannot validate server pack order automatically.
- The Voxy cache persists between mod versions; disabling generation does not delete old wrong cached LOD. Test using a **separate Prism profile and a clean Voxy cache** (backup old storage first).
- The multiplayer virtual stage pipeline is not identical to the native singleplayer server-level generator pipeline. Even with matching pack hashes/priority, surfaces may differ. No guarantee of exact server parity.

### Funky.4 - hybrid terrain restoration (experimental)

User testing shows Funky.1 matches the server terrain shape better than Funky.2/3,
which use the full Terratonic noise router and yielded severe terrain mismatch.

Funky.4 **changes the actual multiplayer terrain algorithm**:

- Default `terrainMode: "legacy_hybrid"` reconstructs the v1-style simplified
  density terrain but keeps Terralith's **explicit real biome source** from
  `minecraft:dimension/overworld.json`. That addresses the primary
  v1-versus-v2 tradeoff without reverting biome selection to vanilla.
- `/voxygen terrain hybrid` selects this mode and restarts generation.
- `/voxygen terrain full` restores the Funky.2/3 full Terratonic terrain
  for coordinate-by-coordinate diagnostic comparison.
- `/voxygen terrain` displays the current selection. `/voxygen diagnose`
  includes the mode.
- **Dry-run remains enabled by default**; no new LOD is written to Voxy
  until the user explicitly runs `/voxygen dryrun off`. This is to protect
  existing server-provided and previously generated LOD data.

**Limitations:** This hybrid is NOT a proven exact SunDroneSMP terrain match.
Without server level.dat generator metadata, enabled-pack order and actual
chunk comparisons, we cannot prove the live server runs the same generator.
Retaining v1 density simplification means Terratonic's advanced mountains,
aquifers and other noise effects may still differ. The short-term goal is
closer relief and correct biome placement; exact parity requires in-game tests.

### Funky.5 — datapack parity mode (experimental)

Funky.5 changes the multiplayer generation algorithm rather than merely adding logging:

- New `/voxygen terrain parity` uses the original Terralith Overworld biome source and the original Terratonic noise settings, with no `simplifyGenerator`.
- For parity mode the existing client-local chunk scheduler follows the vanilla dependency progression (structures, structure references, biomes, terrain, features) and invokes the exposed vanilla `ChunkGenerator` methods. Suppression of cave carvers, underground features, excluded structures and rejected blocks is bypassed in parity mode.
- Restores vanilla `ChunkGeneratorStructureState` concentric-ring positions rather than using Voxygen's approximate ring algorithm.
- Repairs a critical 26.3 virtual world biome lookup: when the requested neighboring chunk is missing, sample the actual generator's biome source and seeded climate sampler, never the stored biome of a random unrelated chunk.
- Existing configs migrate once to parity and **dryrun=true** for both server addresses (with no generated LOD writes) and retain the original 64-bit seed.

**Limitation:** The full `ChunkStatusTasks` runtime requires a real `ServerLevel` from a Minecraft server. A connected remote client cannot construct this from the seed and ZIPs alone. This build calls Minecraft's genuine generator routines with virtual chunks and vanilla dependency ordering; it is **not** a full native dedicated-server chunk scheduler, and 1:1 equality with SunDroneSMP is NOT yet verified. Test heights/biomes/structures in dryrun before enabling writes.

### Goal

Use the original fast Voxygen LOD engine in Funky Ultimate connected to SunDroneSMP, but use local copies of **exactly the same Terralith + Tectonic + Terratonic integration files** as the server. No server datapacks are transferred automatically. The world seed and pack order must match.

### Setup

1. Copy the exact server worldgen datapack ZIPs or unpacked pack folders to **`minecraft/config/funky-voxygen-packs/`** in the Prism instance. Do not put these in `resourcepacks/`. Never execute files from untrusted archives.
2. Launch once to create **`minecraft/config/voxygen.json`**, quit, then edit it. Keep all existing keys, and add `requireWorldgenProfileInMultiplayer` and `worldgenProfiles` following [config/example-voxygen.json](config/example-voxygen.json).
3. Add the pack names **in server priority order** (first to last, with later entries overriding earlier entries). If you used Terratonic on the server, include the integration pack in the same relative order.
4. Add the seed with `/voxygen seed 493136979358296920` while connected to the corresponding server address.
5. Replace the original Voxygen jar (both use mod id `voxygen`). Do **not** install both.
6. Start without shaders, test at a known coordinate outside the old 10,000-block vanilla area, and compare against real server chunks before using a large LOD radius.

### Safety

- No multiplayer generation without an explicitly enabled server profile (unless you turn off the safety option).
- Missing/invalid configured datapacks stop generation, rather than silently falling back to vanilla.
- The example profile skips client-side generation near spawn inside the old vanilla radius; existing server-supplied Voxy LOD can still render there.
- Voxygen already skips chunks that exist in its Voxy storage; this is **not** a guarantee about ordering/priority of asynchronously arriving Voxy Server LOD.
- There is **no automatic server-to-client datapack synchronization** or proof that server and client pack files are identical.
- If datapacks define a custom world preset rather than overriding `minecraft:overworld`, select the relevant biome preset and noise settings in the profile; matching these still may not fully reproduce an arbitrary custom generator.
- When updating packs, restart the game. Test on a **copy of the Prism instance and Voxy cache** first.

### Build

Java 25 and Gradle compatible with Fabric Loom 1.17.21 are required. In a network-enabled environment run `gradle build`. Compiled jar (after successful build): `build/libs/funky-voxygen-1.2.10-funky.5.jar`. A GitHub Actions workflow is included for build verification.

### Attribution

Original code: **Wenhan / lyuwenhan**, Voxygen 1.2.10, copyright 2026, MIT. See [LICENSE](LICENSE). The fork's changes, instructions and documentation are **AI-assisted**. Upstream remains the source of the underlying LOD engine.
