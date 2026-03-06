package me.bechberger.jstall.provider;

import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.provider.requirement.DataRequirement;
import me.bechberger.jstall.provider.requirement.DataRequirements;
import me.bechberger.jstall.util.JMXDiagnosticHelper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrates collection of data requirements from a JVM.
 * Handles timing, parallelism, and scheduling of data collection operations.
 */
public class DataCollector {
    
    private final JMXDiagnosticHelper helper;
    private final DataRequirements requirements;
    private final ScheduledExecutorService scheduler;
    private final boolean ownScheduler;
    
    public DataCollector(JMXDiagnosticHelper helper, DataRequirements requirements) {
        this(helper, requirements, null);
    }
    
    public DataCollector(JMXDiagnosticHelper helper, DataRequirements requirements, 
                        ScheduledExecutorService scheduler) {
        this.helper = helper;
        this.requirements = requirements;
        if (scheduler == null) {
            this.scheduler = Executors.newScheduledThreadPool(2);
            this.ownScheduler = true;
        } else {
            this.scheduler = scheduler;
            this.ownScheduler = false;
        }
    }
    
    /**
     * Collects all data according to the requirements.
     * Groups requirements by interval for synchronized collection.
     * 
     * @return Map of requirement -&gt; collected data samples
     * @throws IOException if collection fails
     */
    public Map<DataRequirement, List<CollectedData>> collectAll() throws IOException {
        Map<DataRequirement, List<CollectedData>> results = new ConcurrentHashMap<>();
        
        try {
            // Group requirements by schedule
            Map<Long, List<DataRequirement>> byInterval = requirements.getRequirements().stream()
                .collect(Collectors.groupingBy(r -> r.getSchedule().intervalMs()));
            
            // Separate one-time from interval-based
            List<DataRequirement> oneTime = byInterval.getOrDefault(0L, List.of());
            Map<Long, List<DataRequirement>> intervals = byInterval.entrySet().stream()
                .filter(e -> e.getKey() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            // Collect one-time data first (system properties, etc.)
            for (DataRequirement req : oneTime) {
                List<CollectedData> samples = new ArrayList<>();
                samples.add(req.collect(helper, 0));
                results.put(req, samples);
            }
            
            // Collect interval-based data
            if (!intervals.isEmpty()) {
                collectWithIntervals(intervals, results);
            }
            
            return results;
            
        } finally {
            if (ownScheduler) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    /**
     * Collects data for requirements with intervals.
     * Synchronizes collection so requirements with the same interval are collected together.
     */
    private void collectWithIntervals(Map<Long, List<DataRequirement>> byInterval,
                                     Map<DataRequirement, List<CollectedData>> results) 
            throws IOException {
        
        // Find the max count needed for each interval
        Map<Long, Integer> maxCountByInterval = byInterval.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                    .mapToInt(r -> r.getSchedule().count())
                    .max()
                    .orElse(1)
            ));
        
        // Execute collections synchronized by time
        CountDownLatch latch = new CountDownLatch(byInterval.size());
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        
        for (Map.Entry<Long, List<DataRequirement>> entry : byInterval.entrySet()) {
            long intervalMs = entry.getKey();
            List<DataRequirement> reqs = entry.getValue();
            
            // Start interval collection on scheduler
            scheduler.execute(() -> {
                try {
                    for (DataRequirement req : reqs) {
                        List<CollectedData> samples = new ArrayList<>();
                        int count = req.getSchedule().count();
                        
                        for (int i = 0; i < count; i++) {
                            try {
                                samples.add(req.collect(helper, i));
                            } catch (IOException e) {
                                exceptions.add(e);
                                samples.add(new CollectedData(
                                    System.currentTimeMillis(),
                                    "",
                                    Map.of("error", e.getMessage())
                                ));
                            }
                            
                            // Wait for interval (except after last sample)
                            if (i < count - 1) {
                                try {
                                    Thread.sleep(intervalMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                        
                        results.put(req, samples);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all interval collections to complete
        try {
            // Calculate max wait time based on longest collection schedule
            long maxDuration = byInterval.entrySet().stream()
                .mapToLong(e -> {
                    long intervalMs = e.getKey();
                    int maxCount = maxCountByInterval.get(intervalMs);
                    return (maxCount - 1) * intervalMs;
                })
                .max()
                .orElse(0);
            
            // Add buffer for overhead
            long waitTime = maxDuration + 30_000; // Max duration + 30 seconds buffer
            
            if (!latch.await(waitTime, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timeout waiting for data collection to complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while collecting data", e);
        }
        
        // If there were collection errors, throw the first one
        if (!exceptions.isEmpty()) {
            Exception first = exceptions.get(0);
            if (first instanceof IOException io) {
                throw io;
            }
            throw new IOException("Data collection failed", first);
        }
    }
}
