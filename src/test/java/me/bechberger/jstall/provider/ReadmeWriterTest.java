package me.bechberger.jstall.provider;

import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.provider.requirement.CollectionSchedule;
import me.bechberger.jstall.provider.requirement.DataRequirements;
import me.bechberger.jstall.provider.requirement.JcmdRequirement;
import me.bechberger.jstall.util.JVMDiscovery;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ReadmeWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-09T14:30:00Z"), ZoneOffset.UTC);

    private static final String SYSTEM_PROPS_CURRENT = """
            3183:
            #Mon Mar 09 12:38:52 CET 2026
            java.version=21.0.6
            java.version.date=2026-01-14
            java.vendor=Eclipse Adoptium
            java.vendor.version=Temurin-21.0.6+7
            java.runtime.name=OpenJDK Runtime Environment
            os.name=Mac OS X
            os.arch=aarch64
            """;

    private static final String SYSTEM_PROPS_OUTDATED = """
            9999:
            java.version=11.0.2
            java.version.date=2019-01-15
            java.vendor=Oracle Corporation
            java.vendor.version=
            java.runtime.name=Java(TM) SE Runtime Environment
            os.name=Linux
            os.arch=amd64
            """;

    private static RecordingProvider.CollectedJvmData successfulJvm(
            long pid, String mainClass, String systemPropsRaw,
            long startedAt, long finishedAt,
            JcmdRequirement sysPropReq, JcmdRequirement threadDumpReq,
            List<CollectedData> threadDumpSamples) {

        Map<me.bechberger.jstall.provider.requirement.DataRequirement, List<CollectedData>> data =
                new LinkedHashMap<>();
        data.put(threadDumpReq, threadDumpSamples);
        data.put(sysPropReq, List.of(new CollectedData(startedAt, systemPropsRaw, Map.of())));
        return RecordingProvider.CollectedJvmData.success(
                new JVMDiscovery.JVMProcess(pid, mainClass), data, startedAt, finishedAt);
    }

    private static RecordingProvider.CollectedJvmData failedJvm(long pid, String mainClass, long startedAt) {
        return RecordingProvider.CollectedJvmData.failure(
                new JVMDiscovery.JVMProcess(pid, mainClass), startedAt, startedAt + 50, "Connection refused");
    }

    @Test
    void generatesTableOfContents() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(2, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        List<CollectedData> dumps = List.of(
                new CollectedData(1000001L, "dump1", Map.of()),
                new CollectedData(1000002L, "dump2", Map.of()));

        var jvm = successfulJvm(3183, "com.example.Main", SYSTEM_PROPS_CURRENT,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq, dumps);

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("## Table of Contents"), "Should have ToC");
        assertTrue(readme.contains("#jvm-3183"), "ToC should link to JVM section");
        assertTrue(readme.contains("<a id=\"jvm-3183\"></a>"), "JVM section should have anchor");
    }

    @Test
    void showsJvmVersionInfo() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(2, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        var jvm = successfulJvm(3183, "com.example.Main", SYSTEM_PROPS_CURRENT,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq,
                List.of(new CollectedData(1000001L, "dump", Map.of())));

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("21.0.6"), "Should show Java version");
        assertTrue(readme.contains("Eclipse Adoptium"), "Should show vendor");
        assertTrue(readme.contains("Mac OS X"), "Should show OS");
        assertTrue(readme.contains("2026-01-14"), "Should show release date");
    }

    @Test
    void showsWarningForOutdatedJvm() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(2, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        var jvm = successfulJvm(9999, "com.example.OldApp", SYSTEM_PROPS_OUTDATED,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq,
                List.of(new CollectedData(1000001L, "dump", Map.of())));

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("⚠️ Warning"), "Should have warning banner");
        assertTrue(readme.contains("significantly outdated"), "Should warn about outdated JVM");
        assertTrue(readme.contains("9999"), "Warning should mention the PID");
    }

    @Test
    void noWarningForCurrentJvm() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(2, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        var jvm = successfulJvm(3183, "com.example.Main", SYSTEM_PROPS_CURRENT,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq,
                List.of(new CollectedData(1000001L, "dump", Map.of())));

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertFalse(readme.contains("⚠️ Warning"), "Should NOT have warning for current JVM");
    }

    @Test
    void listsActualFiles() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(2, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        List<CollectedData> dumps = List.of(
                new CollectedData(1772815039175L, "dump1", Map.of()),
                new CollectedData(1772815044245L, "dump2", Map.of()));

        var jvm = successfulJvm(3183, "com.example.Main", SYSTEM_PROPS_CURRENT,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq, dumps);

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("3183/manifest.json"), "Should list manifest");
        assertTrue(readme.contains("3183/thread-dumps/000-1772815039175.txt"), "Should list first dump");
        assertTrue(readme.contains("3183/thread-dumps/001-1772815044245.txt"), "Should list second dump");
    }

    @Test
    void handlesFailedJvm() {
        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(2, 5000)
                .addSystemProps()
                .build();

        var jvm = failedJvm(4444, "com.example.Broken", 1709991000000L);

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("[FAILED]"), "Should mark as failed");
        assertTrue(readme.contains("❌ Failed"), "Should show failure status");
        assertTrue(readme.contains("Connection refused"), "Should show error message");
        assertFalse(readme.contains("4444/manifest.json]("), "Should NOT list files for failed JVM");
    }

    @Test
    void showsHumanReadableTimestamps() {
        JcmdRequirement threadDumpReq = new JcmdRequirement("Thread.print", null, CollectionSchedule.intervals(1, 5000));
        JcmdRequirement sysPropReq = new JcmdRequirement("VM.system_properties", null, CollectionSchedule.once());

        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(1, 5000)
                .addSystemProps()
                .build();

        var jvm = successfulJvm(3183, "com.example.Main", SYSTEM_PROPS_CURRENT,
                1709991000000L, 1709991005000L, sysPropReq, threadDumpReq,
                List.of(new CollectedData(1000001L, "dump", Map.of())));

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        // Header should show creation time
        assertTrue(readme.contains("2026-03-09 14:30:00 UTC"), "Should show human-readable creation time");
    }

    @Test
    void showsRecordingConfigAsTable() {
        DataRequirements reqs = DataRequirements.builder()
                .addThreadDumps(3, 5000)
                .addSystemProps()
                .build();

        var jvm = failedJvm(1234, "App", 1709991000000L);

        var writer = new ReadmeWriter(List.of(jvm), reqs, "0.1.0", FIXED_CLOCK);
        String readme = writer.generate();

        assertTrue(readme.contains("| Sample count | 3 |"), "Should show sample count in table");
        assertTrue(readme.contains("| Sample interval | 5000 ms |"), "Should show interval in table");
        assertTrue(readme.contains("| Total JVMs | 1 |"), "Should show JVM count in table");
    }

    @Test
    void shortMainClassExtractsLastComponent() {
        assertEquals("Main", ReadmeWriter.shortMainClass("com.example.Main"));
        assertEquals("jetbrains-toolbox", ReadmeWriter.shortMainClass("/Applications/JetBrains Toolbox.app/Contents/MacOS/jetbrains-toolbox"));
        assertEquals("<unknown>", ReadmeWriter.shortMainClass(""));
        assertEquals("<unknown>", ReadmeWriter.shortMainClass(null));
        assertEquals("SimpleApp", ReadmeWriter.shortMainClass("SimpleApp"));
    }
}
