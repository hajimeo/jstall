package me.bechberger.jstall.provider;

import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.provider.requirement.DataRequirement;
import me.bechberger.jstall.provider.requirement.DataRequirements;
import me.bechberger.jstall.provider.requirement.JcmdRequirement;
import me.bechberger.jstall.util.JMXDiagnosticHelper;
import me.bechberger.jstall.util.JVMDiscovery;
import me.bechberger.jstall.util.json.JsonParser;
import me.bechberger.jstall.util.json.JsonPrinter;
import me.bechberger.jstall.util.json.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Records data requirements from one or more JVMs into a ZIP archive.
 */
public class RecordingProvider {

    public static final int FORMAT_VERSION = 1;

    private final String jstallVersion;
    private final boolean verbose;

    public RecordingProvider(String jstallVersion) {
        this(jstallVersion, false);
    }

    public RecordingProvider(String jstallVersion, boolean verbose) {
        this.jstallVersion = jstallVersion;
        this.verbose = verbose;
    }

    /**
     * Records all discovered JVMs (optionally filtered) into the output ZIP.
     */
    public RecordingSummary recordAll(String filter,
                                      DataRequirements requirements,
                                      Path outputFile,
                                      boolean parallel) throws IOException {
        List<JVMDiscovery.JVMProcess> processes = JVMDiscovery.listJVMs(filter);
        return record(processes, requirements, outputFile, parallel);
    }

    /**
     * Records data from the specified JVM targets into a ZIP archive.
     */
    public RecordingSummary record(List<JVMDiscovery.JVMProcess> targets,
                                   DataRequirements requirements,
                                   Path outputFile,
                                   boolean parallel) throws IOException {
        if (targets == null || targets.isEmpty()) {
            throw new IOException("No JVM targets to record");
        }

        if (verbose) {
            System.out.println("Starting recording to " + outputFile.toAbsolutePath());
            System.out.println("Parallel: " + parallel);
        }

        List<JVMDiscovery.JVMProcess> orderedTargets = new ArrayList<>(targets);
        orderedTargets.sort(Comparator.comparingLong(JVMDiscovery.JVMProcess::pid));

        List<CollectedJvmData> collected = collectAllTargets(orderedTargets, requirements, parallel);

        if (verbose) {
            long successCount = collected.stream().filter(CollectedJvmData::successful).count();
            System.out.println("Collection complete: " + successCount + "/" + collected.size() + " successful");
        }

        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String recordingRoot = recordingRootFromOutput(outputFile);

        if (verbose) {
            System.out.println("Writing ZIP file to " + outputFile.toAbsolutePath());
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(outputFile))) {
            if (verbose) {
                System.out.println("  Writing metadata.json");
            }
            writeMetadata(zipOut, recordingRoot, collected, requirements);
            if (verbose) {
                System.out.println("  Writing README.md");
            }
            writeReadme(zipOut, recordingRoot, collected, requirements);
            for (CollectedJvmData targetData : collected) {
                if (verbose) {
                    System.out.println("  Writing data for PID " + targetData.process().pid());
                }
                writeJvmData(zipOut, recordingRoot, targetData, requirements);
            }
        }

        if (verbose) {
            System.out.println("Recording complete");
        }

        long success = collected.stream().filter(CollectedJvmData::successful).count();
        return new RecordingSummary(outputFile.toAbsolutePath(), collected.size(), (int) success,
            (int) (collected.size() - success));
    }

    private List<CollectedJvmData> collectAllTargets(List<JVMDiscovery.JVMProcess> targets,
                                                     DataRequirements requirements,
                                                     boolean parallel) {
        if (verbose) {
            System.out.println("Collecting data from " + targets.size() + " JVM(s) in " +
                (parallel && targets.size() > 1 ? "parallel" : "sequential") + " mode");
        }

        if (!parallel || targets.size() == 1) {
            List<CollectedJvmData> results = new ArrayList<>();
            for (JVMDiscovery.JVMProcess process : targets) {
                results.add(collectOneTarget(process, requirements));
            }
            return results;
        }

        int threads = Math.max(1, Math.min(targets.size(), Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<CollectedJvmData>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> collectOneTarget(target, requirements), executor))
                .toList();

            return futures.stream().map(f -> {
                try {
                    return f.join();
                } catch (CompletionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new RuntimeException(cause);
                }
            }).toList();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private CollectedJvmData collectOneTarget(JVMDiscovery.JVMProcess process,
                                              DataRequirements requirements) {
        long startedAt = System.currentTimeMillis();
        if (verbose) {
            System.out.println("Recording PID " + process.pid() + " (" + process.mainClass() + ")...");
        }
        try (JMXDiagnosticHelper helper = new JMXDiagnosticHelper(process.pid())) {
            if (verbose) {
                System.out.println("  Connected to JMX for PID " + process.pid());
            }
            DataCollector collector = new DataCollector(helper, requirements, null, verbose);
            Map<DataRequirement, List<CollectedData>> collected = collector.collectAll();
            long finishedAt = System.currentTimeMillis();
            if (verbose) {
                System.out.println("  Successfully collected " + collected.size() + " requirement(s) from PID " +
                    process.pid() + " in " + (finishedAt - startedAt) + "ms");
            }
            return CollectedJvmData.success(process, collected, startedAt, finishedAt);
        } catch (Exception e) {
            long finishedAt = System.currentTimeMillis();
            String errorMsg = e.getMessage();
            // Always print errors to stderr, with full stack trace in verbose mode
            System.err.println("Error recording PID " + process.pid() + " (" + process.mainClass() + "): " +
                (errorMsg != null ? errorMsg : e.getClass().getSimpleName()));
            if (verbose) {
                System.err.println("  Full stack trace:");
                e.printStackTrace(System.err);
            }
            return CollectedJvmData.failure(process, startedAt, finishedAt,
                errorMsg != null ? errorMsg : e.getClass().getSimpleName() + ": " + e.toString());
        }
    }

    private String recordingRootFromOutput(Path outputFile) {
        String fileName = outputFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (base.isBlank()) {
            base = "recording";
        }
        return base + "/";
    }

    private void writeMetadata(ZipOutputStream zipOut,
                               String recordingRoot,
                               List<CollectedJvmData> collected,
                               DataRequirements requirements) throws IOException {
        long createdAt = System.currentTimeMillis();

        List<JsonValue> jvms = new ArrayList<>();
        for (CollectedJvmData item : collected) {
            Map<String, JsonValue> fields = new LinkedHashMap<>();
            fields.put("pid", new JsonValue.JsonNumber(item.process().pid()));
            fields.put("mainClass", new JsonValue.JsonString(item.process().mainClass()));
            fields.put("success", new JsonValue.JsonBoolean(item.successful()));
            fields.put("startedAt", new JsonValue.JsonNumber(item.startedAt()));
            fields.put("finishedAt", new JsonValue.JsonNumber(item.finishedAt()));
            
            // Extract VM.flags, VM.command_line, and VM.uptime from collected data (metadata-only)
            if (item.successful()) {
                String vmFlags = extractMetadataField(item.data(), "VM.flags");
                String vmCommandLine = extractMetadataField(item.data(), "VM.command_line");
                String vmUptime = extractMetadataField(item.data(), "VM.uptime");
                if (vmFlags != null) {
                    fields.put("vmFlags", new JsonValue.JsonString(vmFlags));
                }
                if (vmCommandLine != null) {
                    fields.put("vmCommandLine", new JsonValue.JsonString(vmCommandLine));
                }
                if (vmUptime != null) {
                    fields.put("vmUptime", new JsonValue.JsonString(vmUptime));
                }
            }
            
            if (!item.successful() && item.errorMessage() != null) {
                fields.put("error", new JsonValue.JsonString(item.errorMessage()));
            }
            jvms.add(new JsonValue.JsonObject(fields));
        }

        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("formatVersion", new JsonValue.JsonNumber(FORMAT_VERSION));
        root.put("jstallVersion", new JsonValue.JsonString(jstallVersion));
        root.put("createdAt", new JsonValue.JsonNumber(createdAt));
        root.put("requirements", requirementsToJson(requirements));
        root.put("jvms", new JsonValue.JsonArray(jvms));

        writeJsonEntry(zipOut, recordingRoot + "metadata.json", new JsonValue.JsonObject(root));
    }

    private String extractMetadataField(Map<DataRequirement, List<CollectedData>> data, String command) {
        // Find the jcmd requirement for this command and return its first sample's raw data
        for (Map.Entry<DataRequirement, List<CollectedData>> entry : data.entrySet()) {
            if (entry.getKey() instanceof JcmdRequirement jcmd && command.equals(jcmd.getCommand())) {
                List<CollectedData> samples = entry.getValue();
                if (!samples.isEmpty()) {
                    return samples.get(0).rawData();
                }
            }
        }
        return null;
    }    private void writeReadme(ZipOutputStream zipOut,
                             String recordingRoot,
                             List<CollectedJvmData> collected,
                             DataRequirements requirements) throws IOException {
        String content = new ReadmeWriter(collected, requirements, jstallVersion).generate();
        writeTextEntry(zipOut, recordingRoot + "README.md", content);
    }

    private void writeJvmData(ZipOutputStream zipOut,
                              String recordingRoot,
                              CollectedJvmData targetData,
                              DataRequirements requirements) throws IOException {
        String pidPath = recordingRoot + targetData.process().pid() + "/";
        writeManifest(zipOut, pidPath, targetData, requirements);

        if (!targetData.successful()) {
            return;
        }

        for (DataRequirement requirement : requirements.getRequirements()) {
            List<CollectedData> samples = targetData.data().getOrDefault(requirement, List.of());
            if (!samples.isEmpty()) {
                requirement.persist(zipOut, pidPath, samples);
            }
        }
    }

    private void writeManifest(ZipOutputStream zipOut,
                               String pidPath,
                               CollectedJvmData targetData,
                               DataRequirements requirements) throws IOException {
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("pid", new JsonValue.JsonNumber(targetData.process().pid()));
        root.put("mainClass", new JsonValue.JsonString(targetData.process().mainClass()));
        root.put("success", new JsonValue.JsonBoolean(targetData.successful()));
        root.put("startedAt", new JsonValue.JsonNumber(targetData.startedAt()));
        root.put("finishedAt", new JsonValue.JsonNumber(targetData.finishedAt()));
        if (!targetData.successful() && targetData.errorMessage() != null) {
            root.put("error", new JsonValue.JsonString(targetData.errorMessage()));
        }

        if (targetData.successful()) {
            List<JsonValue> sampleCounts = new ArrayList<>();
            for (DataRequirement requirement : requirements.getRequirements()) {
                int count = targetData.data().getOrDefault(requirement, List.of()).size();
                sampleCounts.add(JsonValue.JsonObject.of(
                    "type", new JsonValue.JsonString(requirement.getType()),
                    "count", new JsonValue.JsonNumber(count)
                ));
            }
            root.put("sampleCounts", new JsonValue.JsonArray(sampleCounts));
        }

        root.put("requirements", requirementsToJson(requirements));

        writeJsonEntry(zipOut, pidPath + "manifest.json", new JsonValue.JsonObject(root));
    }

    private JsonValue requirementsToJson(DataRequirements requirements) {
        List<JsonValue> list = new ArrayList<>();

        for (DataRequirement requirement : requirements.getRequirements()) {
            Map<String, JsonValue> item = new LinkedHashMap<>();
            item.put("type", new JsonValue.JsonString(requirement.getType()));
            item.put("count", new JsonValue.JsonNumber(requirement.getSchedule().count()));
            item.put("intervalMs", new JsonValue.JsonNumber(requirement.getSchedule().intervalMs()));

            if (requirement instanceof JcmdRequirement jcmd) {
                item.put("command", new JsonValue.JsonString(jcmd.getCommand()));
                List<JsonValue> args = new ArrayList<>();
                if (jcmd.getArgs() != null) {
                    for (String arg : jcmd.getArgs()) {
                        args.add(new JsonValue.JsonString(arg));
                    }
                }
                item.put("args", new JsonValue.JsonArray(args));
            }

            list.add(new JsonValue.JsonObject(item));
        }

        return new JsonValue.JsonArray(list);
    }

    private void writeJsonEntry(ZipOutputStream zipOut,
                                String entryName,
                                JsonValue value) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        zipOut.write(JsonPrinter.print(value).getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
    }

    private void writeTextEntry(ZipOutputStream zipOut,
                                String entryName,
                                String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        zipOut.write(content.getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
    }

    public static JsonValue.JsonObject loadMetadata(Path recordingZip) throws IOException {
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(recordingZip.toFile())) {
            ZipEntry metadataEntry = zipFile.getEntry("metadata.json");
            if (metadataEntry == null) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry candidate = entries.nextElement();
                    String name = candidate.getName();
                    if (!candidate.isDirectory() && name.endsWith("/metadata.json")) {
                        metadataEntry = candidate;
                        break;
                    }
                }
            }
            if (metadataEntry == null) {
                throw new IOException("Recording is missing metadata.json");
            }
            String content = new String(zipFile.getInputStream(metadataEntry).readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parse(content).asObject();
        }
    }

    public record RecordingSummary(Path outputFile,
                                   int targetCount,
                                   int successCount,
                                   int failureCount) {
    }

    record CollectedJvmData(JVMDiscovery.JVMProcess process,
                                    Map<DataRequirement, List<CollectedData>> data,
                                    long startedAt,
                                    long finishedAt,
                                    String errorMessage) {
        static CollectedJvmData success(JVMDiscovery.JVMProcess process,
                                        Map<DataRequirement, List<CollectedData>> data,
                                        long startedAt,
                                        long finishedAt) {
            return new CollectedJvmData(process, data, startedAt, finishedAt, null);
        }

        static CollectedJvmData failure(JVMDiscovery.JVMProcess process,
                                        long startedAt,
                                        long finishedAt,
                                        String errorMessage) {
            return new CollectedJvmData(process, Map.of(), startedAt, finishedAt,
                errorMessage == null ? "Unknown error" : errorMessage);
        }

        boolean successful() {
            return errorMessage == null;
        }
    }
}