package me.bechberger.jstall.provider.requirement;

import java.util.Map;

/**
 * Container for data collected at a specific point in time.
 *
 * @param timestamp Milliseconds since epoch when data was collected
 * @param rawData Raw data as string (e.g., jcmd output, thread dump)
 * @param metadata Optional metadata about the collection (e.g., errors, warnings)
 */
public record CollectedData(long timestamp, String rawData, Map<String, String> metadata) {
    
    /**
     * Creates collected data with current timestamp.
     */
    public static CollectedData now(String rawData) {
        return new CollectedData(System.currentTimeMillis(), rawData, Map.of());
    }
    
    /**
     * Creates collected data with metadata.
     */
    public static CollectedData withMetadata(long timestamp, String rawData, Map<String, String> metadata) {
        return new CollectedData(timestamp, rawData, metadata);
    }
}
