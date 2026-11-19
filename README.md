# Funky Voxygen for SunDroneSMP

Client-side Voxy LOD generator for **Minecraft 26.3 / Fabric**, based on Wenhan's [Voxygen 1.2.10](https://github.com/lyuwenhan/minecraft-java-edition-mods/tree/66c536b0666985e68376ed1ceb6a3dcc5c981cd3/voxygen) (MIT license).

**Status: experimental source release.** This fork adds opt-in per-server local worldgen datapacks. Minecraft 26.3 + Java 25 are required. Compilation and in-game correctness are not yet verified.

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

Java 25 and Gradle compatible with Fabric Loom 1.17.21 are required. In a network-enabled environment run `gradle build`. Compiled jar (after successful build): `build/libs/funky-voxygen-1.2.10-funky.1.jar`. A GitHub Actions workflow is included for build verification.

### Attribution

Original code: **Wenhan / lyuwenhan**, Voxygen 1.2.10, copyright 2026, MIT. See [LICENSE](LICENSE). The fork's changes, instructions and documentation are **AI-assisted**. Upstream remains the source of the underlying LOD engine.
