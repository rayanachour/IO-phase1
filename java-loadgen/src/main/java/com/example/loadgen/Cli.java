package com.example.loadgen;

import java.util.HashMap;
import java.util.Map;

class Cli {
    final String configPath;
    final String profileName;

    Cli(String configPath, String profileName) {
        this.configPath = configPath;
        this.profileName = profileName;
    }

    static Cli parse(String[] args) {
        Map<String, String> kv = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                int i = a.indexOf('=');
                if (i > 2) {
                    kv.put(a.substring(2, i), a.substring(i + 1));
                } else {
                    kv.put(a.substring(2), "true");
                }
            }
        }
        return new Cli(kv.get("config"), kv.get("profile"));
    }
}
