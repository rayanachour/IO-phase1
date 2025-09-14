package com.example.loadgen;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class App {

    public static void main(String[] args) throws Exception {
        Cli cli = Cli.parse(args);
        Path configPath = cli.configPath != null ? Path.of(cli.configPath) : Path.of("config", "config.yml");
        String profileName = cli.profileName != null ? cli.profileName : "baseline";

        System.out.println("== Java LoadGen ==");
        System.out.println("Config: " + configPath.toAbsolutePath());
        System.out.println("Profile: " + profileName);

        LoaderOptions opts = new LoaderOptions();
        Constructor ctor = new Constructor(Config.class, opts);
        Yaml yaml = new Yaml(ctor);

        try (InputStream in = new FileInputStream(configPath.toFile())) {
            Config cfg = yaml.load(in);
            if (cfg == null) throw new IllegalStateException("Failed to load YAML (null).");

            LoadProfile profile = findProfile(cfg.getLoad_profiles(), profileName)
                    .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileName));

            System.out.printf(Locale.ROOT,
                    "Targets: %,d tags/s, %,d alarms/s, duration %ds%n",
                    profile.getTags_per_second(), profile.getAlarms_per_second(), profile.getDuration_seconds());

            simulate(profile);

            System.out.println("\n== Summary ==");
            System.out.printf(Locale.ROOT, "Ran profile '%s' for %ds%n", profile.getName(), profile.getDuration_seconds());
            System.out.printf(Locale.ROOT, "Total tags ~ %,d%n", (long) profile.getTags_per_second() * profile.getDuration_seconds());
            System.out.printf(Locale.ROOT, "Total alarms ~ %,d%n", (long) profile.getAlarms_per_second() * profile.getDuration_seconds());
            System.out.println("NOTE: dry-run simulator (no real OPC UA).");
        }
    }

    static Optional<LoadProfile> findProfile(List<LoadProfile> profiles, String name) {
        if (profiles == null) return Optional.empty();
        return profiles.stream().filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(name)).findFirst();
    }

    static void simulate(LoadProfile p) throws InterruptedException {
        System.out.println("\n== Running ==");
        Instant start = Instant.now();
        for (int sec = 1; sec <= p.getDuration_seconds(); sec++) {
            System.out.printf(Locale.ROOT, "t=%3ds | tags=%,d/s | alarms=%,d/s%n",
                    sec, p.getTags_per_second(), p.getAlarms_per_second());
            Thread.sleep(1000L);
        }
        Instant end = Instant.now();
        System.out.println("Elapsed: " + Duration.between(start, end).toSeconds() + "s");
    }
}
