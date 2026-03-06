package me.bechberger.jstall.analyzer;

import me.bechberger.jstall.model.SystemEnvironment;
import me.bechberger.jstall.model.ThreadDumpSnapshot;
import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.util.JcmdOutputParsers;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Container for resolved data requirements passed to analyzers.
 * This provides direct access to collected data without needing to extract it from ThreadDumpSnapshot.
 */
public record ResolvedData(
    List<ThreadDumpSnapshot> dumps,
    Map<String, String> systemProperties,
    SystemEnvironment environment,
    Map<String, List<CollectedData>> collectedDataByType
) {

    public ResolvedData {
        dumps = dumps == null ? List.of() : List.copyOf(dumps);
        systemProperties = systemProperties == null ? Map.of() : Map.copyOf(systemProperties);
        collectedDataByType = normalize(collectedDataByType);
    }

    private static Map<String, List<CollectedData>> normalize(Map<String, List<CollectedData>> dataByType) {
        if (dataByType == null || dataByType.isEmpty()) {
            return Map.of();
        }
        Map<String, List<CollectedData>> normalized = new TreeMap<>();
        for (Map.Entry<String, List<CollectedData>> entry : dataByType.entrySet()) {
            String type = entry.getKey();
            if (type == null || type.isBlank()) {
                continue;
            }
            List<CollectedData> samples = entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
            normalized.put(type, samples);
        }
        return Map.copyOf(normalized);
    }
    
    /**
     * Creates ResolvedData from thread dump snapshots.
     * Automatically extracts system properties and environment from the first dump.
     */
    public static ResolvedData fromDumps(List<ThreadDumpSnapshot> dumps) {
        return fromDumpsAndCollectedData(dumps, Map.of());
    }

    /**
     * Creates ResolvedData from thread dump snapshots and additional collected requirement data.
     */
    public static ResolvedData fromDumpsAndCollectedData(List<ThreadDumpSnapshot> dumps,
                                                         Map<String, List<CollectedData>> collectedDataByType) {
        List<ThreadDumpSnapshot> safeDumps = dumps == null ? List.of() : dumps;

        Map<String, String> systemProps = Map.of();
        SystemEnvironment env = null;

        if (!safeDumps.isEmpty()) {
            ThreadDumpSnapshot firstDump = safeDumps.get(0);
            systemProps = firstDump.systemProperties() != null ? firstDump.systemProperties() : Map.of();
            env = firstDump.environment();
        }

        if (systemProps.isEmpty()) {
            List<CollectedData> collectedProps = collectedDataByType == null
                ? List.of()
                : collectedDataByType.getOrDefault("system-properties", List.of());
            for (int i = collectedProps.size() - 1; i >= 0; i--) {
                String raw = collectedProps.get(i).rawData();
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Map<String, String> parsed = JcmdOutputParsers.parseVmSystemProperties(raw);
                if (!parsed.isEmpty()) {
                    systemProps = parsed;
                    break;
                }
            }
        }

        return new ResolvedData(safeDumps, systemProps, env, collectedDataByType);
    }
    
    /**
     * Returns true if system properties are available.
     */
    public boolean hasSystemProperties() {
        return systemProperties != null && !systemProperties.isEmpty();
    }
    
    /**
     * Returns true if environment data is available.
     */
    public boolean hasEnvironment() {
        return environment != null;
    }
    
    /**
     * Returns true if thread dumps are available.
     */
    public boolean hasDumps() {
        return dumps != null && !dumps.isEmpty();
    }

    /**
     * Returns collected samples for a requirement type (e.g. "thread-dumps", "system-properties").
     */
    public List<CollectedData> collectedData(String requirementType) {
        if (requirementType == null || requirementType.isBlank()) {
            return List.of();
        }
        return collectedDataByType.getOrDefault(requirementType, List.of());
    }
}
