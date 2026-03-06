package me.bechberger.jstall.provider.requirement;

/**
 * Defines how data should be collected over time.
 *
 * @param count Number of samples to collect
 * @param intervalMs Interval between samples in milliseconds
 * @param captureTimestamps Whether to capture exact timestamps for each sample
 */
public record CollectionSchedule(int count, long intervalMs, boolean captureTimestamps) {
    
    /**
     * Creates a schedule for a single collection (no intervals).
     */
    public static CollectionSchedule once() {
        return new CollectionSchedule(1, 0, true);
    }
    
    /**
     * Creates a schedule for multiple collections at intervals.
     */
    public static CollectionSchedule intervals(int count, long intervalMs) {
        return new CollectionSchedule(count, intervalMs, true);
    }
    
    /**
     * Returns the total duration needed for this collection schedule.
     */
    public long totalDurationMs() {
        return count > 1 ? (count - 1) * intervalMs : 0;
    }
    
    /**
     * Returns true if this schedule collects multiple samples.
     */
    public boolean isMultiple() {
        return count > 1;
    }
}
