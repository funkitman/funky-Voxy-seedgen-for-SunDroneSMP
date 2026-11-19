package com.example.voxygen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads explicitly allowed server-data packs into a separate, CLIENT-LOCAL worldgen registry.
 * Nothing is uploaded to / downloaded from the multiplayer server.
 */
public final class VoxygenWorldgenPacks {
    private static final Logger LOGGER = LoggerFactory.getLogger("funky-voxygen-packs");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("funky-voxygen-packs");

    private VoxygenWorldgenPacks() {}

    public static Loaded load(Minecraft client, VoxygenConfig.WorldgenProfile profile) {
        if (profile == null || profile.datapacks == null || profile.datapacks.isEmpty()) {
            throw new IllegalStateException("Funky Voxygen multiplayer profile must select at least one worldgen datapack");
        }
        List<PackResources> stack = new ArrayList<>();
        stack.add(client.getVanillaPackResources().fullResources());
        MultiPackResourceManager resources = null;
        try {
            for (String name : profile.datapacks) {
                Path path = resolvePack(name);
                Pack.ResourcesSupplier supplier = Files.isDirectory(path)
                        ? new PathPackResources.PathResourcesSupplier(path)
                        : new FilePackResources.FileResourcesSupplier(path);

                PackLocationInfo info = new PackLocationInfo(
                        "funky-voxygen:" + name,
                        Component.literal("Funky Voxygen: " + name),
                        PackSource.BUILT_IN,
                        Optional.empty());
                Pack pack = Pack.readMetaAndCreate(
                        info, supplier, PackType.SERVER_DATA,
                        new PackSelectionConfig(false, Pack.Position.TOP, false));
                if (pack == null) {
                    throw new IllegalStateException("Invalid or unsupported pack.mcmeta for local datapack: " + name);
                }
                try (Stream<PackResources> opened = pack.open()) {
                    List<PackResources> parts = opened.toList();
                    if (parts.isEmpty()) throw new IllegalStateException("Empty local datapack: " + name);
                    stack.addAll(parts);
                }
                LOGGER.info("[Funky.3] pack priority {} / {}: {} (SHA-256 {})",
                        stack.size() - 1, profile.datapacks.size(), path.getFileName(), fingerprint(path));
            }
            // Order is vanilla -> listed packs. Later packs override earlier packs.
            resources = new MultiPackResourceManager(PackType.SERVER_DATA, stack);
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            RegistryAccess.Frozen accessForLoading = layers.getAccessForLoading(RegistryLayer.WORLD);
            RegistryAccess.Frozen world = RegistryDataLoader.load(
                            resources, accessForLoading.listRegistries().toList(),
                            RegistryDataLoader.WORLD_REGISTRIES, Runnable::run)
                    .join();
            LOGGER.info("[Funky.3] Loaded {} local worldgen packs; final pack has highest priority. "
                    + "Actual server order is NOT verified by the client.", profile.datapacks.size());
            return new Loaded(layers.replaceFrom(RegistryLayer.WORLD, world).compositeAccess(), resources);
        } catch (RuntimeException | Error failure) {
            if (resources != null) {
                resources.close();
            } else {
                // Vanilla pack resources belong to Minecraft, not to this loader.
                for (int i = 1; i < stack.size(); i++) stack.get(i).close();
            }
            throw failure; // Fail closed: never silently fall back to incorrect vanilla terrain.
        }
    }

    /**
     * Decode the REAL minecraft:overworld generator from the active pack stack.
     * This retains Terralith's explicit climate-to-biome mapping. Registry references,
     * including the settings overwritten by Terratonic, resolve against the same
     * combined local worldgen registry rather than the vanilla biome preset.
     */
    public static NoiseBasedChunkGenerator loadOverworldGenerator(Loaded loaded) {
        logSource(loaded, "minecraft:dimension/overworld.json");
        logSource(loaded, "minecraft:worldgen/noise_settings/overworld.json");
        logSource(loaded, "minecraft:worldgen/density_function/overworld/final_density.json");
        Identifier id = Identifier.parse("minecraft:dimension/overworld.json");
        Resource resource = loaded.resources().getResource(id).orElseThrow(() ->
                new IllegalStateException(
                        "Missing minecraft:dimension/overworld.json; this profile needs the same overworld generator as SunDroneSMP"));
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) {
                throw new IllegalStateException("Invalid minecraft:dimension/overworld.json (expected JSON object)");
            }
            JsonObject dimension = json.getAsJsonObject();
            if (!dimension.has("generator") || !dimension.get("generator").isJsonObject()) {
                throw new IllegalStateException("Missing generator in minecraft:dimension/overworld.json");
            }
            ChunkGenerator parsed = ChunkGenerator.CODEC.parse(
                    RegistryOps.create(JsonOps.INSTANCE, loaded.registries()),
                    dimension.get("generator")
            ).getOrThrow();
            if (!(parsed instanceof NoiseBasedChunkGenerator noise)) {
                throw new IllegalStateException(
                        "SunDroneSMP profile requires a noise-based overworld generator, got " +
                        parsed.getClass().getName());
            }
            LOGGER.info("Decoded datapack overworld generator: {}, biome source: {}, settings: {}",
                    noise.getClass().getSimpleName(),
                    noise.getBiomeSource().getClass().getSimpleName(),
                    noise.generatorSettings().unwrapKey().map(Object::toString).orElse("<inline>"));
            return noise;
        } catch (IOException problem) {
            throw new IllegalStateException("Cannot read local minecraft:dimension/overworld.json", problem);
        }
    }

    private static void logSource(Loaded loaded, String identifier) {
        Identifier id = Identifier.parse(identifier);
        Optional<Resource> resource = loaded.resources().getResource(id);
        LOGGER.info("[Funky.3] effective resource {} <- {}", identifier,
                resource.map(Resource::sourcePackId).orElse("<MISSING>"));
    }

    private static String fingerprint(Path path) {
        if (Files.isDirectory(path)) return "<directory; archive hash unavailable>";
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = stream.read(buffer)) != -1) md.update(buffer, 0, count);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint datapack: " + path, e);
        }
    }

    private static Path resolvePack(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..") || name.contains(":")) {
            throw new IllegalArgumentException("Invalid datapack file name: " + name);
        }
        Path path = ROOT.resolve(name).normalize();
        if (!path.getParent().equals(ROOT) || Files.isSymbolicLink(path) || !Files.exists(path)) {
            throw new IllegalArgumentException("Missing or unsafe local datapack: " + name + " in " + ROOT);
        }
        if (!Files.isDirectory(path) && !(Files.isRegularFile(path) && name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))) {
            throw new IllegalArgumentException("Datapack must be a directory or .zip file: " + path);
        }
        return path;
    }

    public record Loaded(RegistryAccess registries, MultiPackResourceManager resources)
            implements AutoCloseable {
        @Override public void close() {
            resources.close();
        }
    }
}
