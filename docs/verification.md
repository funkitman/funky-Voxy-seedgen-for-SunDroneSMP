# Release verification checklist — Funky Voxygen

This is a **source-only experimental port until CI passes and a real client is tested**.

- [ ] GitHub Actions Java 25 / Loom compile and package succeeds.
- [ ] In a cloned Prism instance set `/voxygen dryrun on` before testing (no Voxy writes). Use `/voxygen diagnose`.
- [ ] Compare generated dryrun sample `x/z, y, biome` with actual SunDroneSMP real chunks at the exact coordinates outside 10,000 blocks.
- [ ] Compare SHA-256 of all datapack ZIPs and resource provenance against server copies. Confirm actual server order and any Fabric load conditions.
- [ ] Only turn dryrun off after matching several real chunks; use a separate Voxy cache for write tests.
- [ ] Fabric 26.3 client starts with only the fork installed (NOT original Voxygen too).
- [ ] `/voxygen status` reports the expected seed `493136979358296920`.
- [ ] Configured packs load, with exact hashes and priority order copied from SunDroneSMP. Inspect logs for `Local worldgen datapack selected`.
- [ ] Singleplayer Voxygen test still matches before/after fork (control test).
- [ ] Multiplayer without profile does not generate local LOD.
- [ ] Multiplayer with missing pack fails closed; check log first cause.
- [ ] Confirm Funky.2 logs `Decoded datapack overworld generator` with Terralith biome source.
- [ ] Compare terrain height, biome and trees at the same coordinates beyond 10,000 blocks, no shaders, separate/new Voxy cache.
- [ ] Confirm Terratonic noise router final_density is retained, not replaced with vanilla.
- [ ] Inside legacy pregenerated area, Voxygen leaves new output absent; Voxy Server still populates LOD (verify).
- [ ] Test at legacy area boundary including +/-X/Z diagonals and structures/trees/biomes.
- [ ] Verify Voxy Server late chunk deliveries do not flicker or get replaced by stale synthetic LOD.
- [ ] Measure chunks/min, CPU/RAM, and shutdown; test full render distance later.
- [ ] Re-enable shaders, physics, Vivecraft only after baseline is stable.
- [ ] License MIT + original author + AI-assisted modification clearly documented.

If this fork misrenders terrain, use the original 1.2.10 jar again and restore the backed-up Voxy cache. Never touch the actual SunDroneSMP world data as part of client testing.
