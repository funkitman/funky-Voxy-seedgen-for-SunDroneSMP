package com.example.voxygen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                LOGGER.info("Local worldgen datapack selected: {}", path.getFileName());
            }
            // Order is vanilla -> listed packs. Later packs override earlier packs.
            resources = new MultiPackResourceManager(PackType.SERVER_DATA, stack);
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            RegistryAccess.Frozen accessForLoading = layers.getAccessForLoading(RegistryLayer.WORLD);
            RegistryAccess.Frozen world = RegistryDataLoader.load(
                            resources, accessForLoading.listRegistries().toList(),
                            RegistryDataLoader.WORLD_REGISTRIES, Runnable::run)
                    .join();
            LOGGER.info("Loaded {} local worldgen datapacks", profile.datapacks.size());
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
