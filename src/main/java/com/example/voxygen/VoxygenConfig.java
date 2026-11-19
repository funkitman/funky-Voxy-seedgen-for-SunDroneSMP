package com.example.voxygen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoxygenConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("voxygen");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("voxygen.json");

	public boolean enable = true;
	public boolean showProgress = false;
	public boolean generateForFreecam = false;
	public int radius = 64;
	public int threads = 32;
	public Map<String, Long> seeds = new HashMap<>();
	public boolean requireWorldgenProfileInMultiplayer = true;
	public Map<String, WorldgenProfile> worldgenProfiles = new HashMap<>();

	public static final class WorldgenProfile {
		public boolean enabled = false;
		// Diagnostic-only mode builds chunks but NEVER writes synthetic LOD to Voxy storage.
		public boolean diagnosticOnly = true;
		// LEGACY_HYBRID keeps the v1-style land relief while using Terralith's
		// real biome source. FULL_DATAPACK reproduces v2/v3 generator selection.
		// Neither is guaranteed to match the live server until compared in-game.
		public String terrainMode = "legacy_hybrid";
		public List<String> datapacks = new ArrayList<>();
		public String overworldNoiseSettings = "minecraft:overworld";
		public String overworldBiomePreset = "minecraft:overworld";
		public int minGenerationDistanceFromSpawnBlocks = 0;
		public int spawnX = 0;
		public int spawnZ = 0;
	}

	public WorldgenProfile profileFor(String serverAddress) {
		if (serverAddress == null || worldgenProfiles == null) return null;
		WorldgenProfile profile = worldgenProfiles.get(serverAddress.trim().toLowerCase(Locale.ROOT));
		return profile != null && profile.enabled ? profile : null;
	}

	public static VoxygenConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			VoxygenConfig config = new VoxygenConfig();
			config.save();
			return config;
		}

		try {
			VoxygenConfig config =
					GSON.fromJson(Files.readString(CONFIG_PATH), VoxygenConfig.class);
			if (config == null) {
				config = new VoxygenConfig();
			}
			if (config.seeds == null) {
				config.seeds = new HashMap<>();
			}
			if (config.worldgenProfiles == null) {
				config.worldgenProfiles = new HashMap<>();
			}
			for (WorldgenProfile profile : config.worldgenProfiles.values()) {
				if (profile != null) {
					if (profile.datapacks == null) profile.datapacks = new ArrayList<>();
					if (profile.terrainMode == null || profile.terrainMode.isBlank()) {
						profile.terrainMode = "legacy_hybrid";
					}
				}
			}
			if (config.radius < 1) {
				config.radius = 64;
			}
			if (config.threads < 1) {
				config.threads = 32;
			} else if (config.threads > 256) {
				config.threads = 256;
			}
			config.save();
			return config;
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Failed to load Voxygen config", e);
			return new VoxygenConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(this));
		} catch (IOException e) {
			LOGGER.error("Failed to save Voxygen config", e);
		}
	}
}
