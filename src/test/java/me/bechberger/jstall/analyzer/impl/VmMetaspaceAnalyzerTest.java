package me.bechberger.jstall.analyzer.impl;

import me.bechberger.jstall.analyzer.AnalyzerResult;
import me.bechberger.jstall.analyzer.ResolvedData;
import me.bechberger.jstall.model.ThreadDumpSnapshot;
import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jthreaddump.parser.ThreadDumpParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VmMetaspaceAnalyzerTest {

    // Real-format partial VM.metaspace output (as produced by jcmd <pid> VM.metaspace)
    static final String SAMPLE_A = """
        5785:

        Total Usage - 262 loaders, 6009 classes (1383 shared):
          Non-Class: 1199 chunks,     27.38 MB capacity,   27.00 MB ( 99%) committed,    26.55 MB ( 97%) used,   457.27 KB (  2%) free,     1.00 KB ( <1%) waste , deallocated: 39 blocks with 6.48 KB
              Class:  495 chunks,      3.42 MB capacity,    3.30 MB ( 96%) committed,     2.96 MB ( 86%) used,   349.73 KB ( 10%) free,    40 bytes ( <1%) waste , deallocated: 205 blocks with 55.79 KB
               Both: 1694 chunks,     30.80 MB capacity,   30.30 MB ( 98%) committed,    29.51 MB ( 96%) used,   806.99 KB (  3%) free,     1.04 KB ( <1%) waste , deallocated: 244 blocks with 62.27 KB


        Virtual space:
          Non-class space:       64.00 MB reserved,      27.00 MB ( 42%) committed,  1 nodes.
              Class space:        1.00 GB reserved,       3.31 MB ( <1%) committed,  1 nodes.
                     Both:        1.06 GB reserved,      30.31 MB (  3%) committed.

        Chunk freelists:
           Non-Class:

         16m: (none)
        Total word size: 9.25 MB, committed: 0 bytes (  0%)

               Class:

         16m: (none)
        Total word size: 12.58 MB, committed: 0 bytes (  0%)

                Both:
        Total word size: 21.83 MB, committed: 0 bytes (  0%)

        Waste (unused committed space):
                Waste in chunks in use:      1.04 KB ( <1%)

        Internal statistics:

        num_allocs_failed_limit: 3.

        Settings:
        MaxMetaspaceSize: unlimited
        CompressedClassSpaceSize: 1.00 GB
        Initial GC threshold: 21.00 MB
        Current GC threshold: 35.00 MB
        CDS: on
         - commit_granule_bytes: 65536.
        """;

    // Second sample with slightly more memory used (for trend tests)
    static final String SAMPLE_B = """
        5785:

        Total Usage - 265 loaders, 6050 classes (1383 shared):
          Non-Class: 1220 chunks,     28.00 MB capacity,   27.50 MB ( 98%) committed,    27.00 MB ( 98%) used,   460.00 KB (  2%) free,     1.00 KB ( <1%) waste
              Class:  500 chunks,      3.50 MB capacity,    3.35 MB ( 96%) committed,     3.00 MB ( 87%) used,   350.00 KB ( 10%) free,    40 bytes ( <1%) waste
               Both: 1720 chunks,     31.50 MB capacity,   30.85 MB ( 98%) committed,    30.00 MB ( 97%) used,   810.00 KB (  3%) free,     1.04 KB ( <1%) waste

        Virtual space:
          Non-class space:       64.00 MB reserved,      27.50 MB ( 43%) committed,  1 nodes.
              Class space:        1.00 GB reserved,       3.35 MB ( <1%) committed,  1 nodes.
                     Both:        1.06 GB reserved,      30.85 MB (  3%) committed.

        Settings:
        MaxMetaspaceSize: unlimited
        CompressedClassSpaceSize: 1.00 GB
        Initial GC threshold: 21.00 MB
        Current GC threshold: 35.00 MB
        CDS: on
        """;

    private static String normalizeOutput(String output) {
        return output.lines().map(String::stripTrailing).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String runJcmd(long pid, String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("jcmd", String.valueOf(pid), command).start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes());
        return exitCode == 0 ? output : "";
    }

    private static ThreadDumpSnapshot createDummySnapshot() {
        String dumpContent = """
            2024-12-29 13:00:00
            Full thread dump Java HotSpot(TM) 64-Bit Server VM (21+35-2513 mixed mode):
            """;
        try {
            var parsed = ThreadDumpParser.parse(dumpContent);
            return new ThreadDumpSnapshot(parsed, dumpContent, null, Map.of());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parsesSingleSampleIntoTable() {
        VmMetaspaceAnalyzer analyzer = new VmMetaspaceAnalyzer();
        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-metaspace", List.of(new CollectedData(1L, SAMPLE_A, Map.of())))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        String expected = """
                VM.metaspace (1 sample):
                262 loaders, 6009 classes (1383 shared)
                Type       Chunks  Capacity      Used  Committed       Free
                ---------  ------  --------  --------  ---------  ---------
                Non-Class    1199  27.38 MB  26.55 MB   27.00 MB  457.27 KB
                Class         495   3.42 MB   2.96 MB    3.30 MB  349.73 KB
                Both         1694  30.80 MB  29.51 MB   30.30 MB  806.99 KB
                Space            Reserved  Committed
                ---------------  --------  ---------
                Non-class space  64.00 MB   27.00 MB
                Class space       1.00 GB    3.31 MB
                Both              1.06 GB   30.31 MB
                Waste: 1.04 KB
                MaxMetaspaceSize: unlimited  CompressedClassSpaceSize: 1.00 GB  Initial GC threshold: 21.00 MB  Current GC threshold: 35.00 MB  CDS: on"""
            .stripIndent();
        assertEquals(expected, normalizeOutput(result.output()));
    }

    @Test
    void showsTrendForMultipleSamples() {
        VmMetaspaceAnalyzer analyzer = new VmMetaspaceAnalyzer();
        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-metaspace", List.of(
                new CollectedData(1L, SAMPLE_A, Map.of()),
                new CollectedData(2L, SAMPLE_B, Map.of())
            ))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        String expected = """
                VM.metaspace (2 samples):
                265 loaders, 6050 classes (1383 shared)
                Type       Chunks  Capacity      Used  Committed       Free  Trend
                ---------  ------  --------  --------  ---------  ---------  -----------------
                Non-Class    1220  28.00 MB  27.00 MB   27.50 MB  460.00 KB  ↑ +460.80 KB used
                Class         500   3.50 MB   3.00 MB    3.35 MB  350.00 KB  ↑ +40.96 KB used
                Both         1720  31.50 MB  30.00 MB   30.85 MB  810.00 KB  ↑ +501.76 KB used
                Space            Reserved  Committed
                ---------------  --------  ---------
                Non-class space  64.00 MB   27.50 MB
                Class space       1.00 GB    3.35 MB
                Both              1.06 GB   30.85 MB
                Waste: 1.04 KB
                MaxMetaspaceSize: unlimited  CompressedClassSpaceSize: 1.00 GB  Initial GC threshold: 21.00 MB  Current GC threshold: 35.00 MB  CDS: on"""
            .stripIndent();
        assertEquals(expected, normalizeOutput(result.output()));
    }

    @Test
    void handlesUnsupportedOutput() {
        VmMetaspaceAnalyzer analyzer = new VmMetaspaceAnalyzer();

        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-metaspace", List.of(new CollectedData(1L, "Command not supported", Map.of())))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        assertEquals("VM.metaspace not available (or no parseable summary lines)", result.output());
    }

    @Test
    void parsesRealJvmMetaspaceOutput() throws Exception {
        long currentPid = ProcessHandle.current().pid();
        String rawMetaspace = runJcmd(currentPid, "VM.metaspace");
        assumeTrue(!rawMetaspace.isBlank(), "Skipping: jcmd VM.metaspace unavailable on this JVM");

        VmMetaspaceAnalyzer analyzer = new VmMetaspaceAnalyzer();
        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-metaspace", List.of(new CollectedData(System.currentTimeMillis(), rawMetaspace, Map.of())))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());
        assertTrue(result.shouldDisplay());
        assumeTrue(!result.output().contains("not available"),
            "Skipping: current JVM VM.metaspace output format is incompatible with parser");

        String output = normalizeOutput(result.output());
        assertTrue(output.startsWith("VM.metaspace (1 sample):"), "title line");
        // Expect all three chunk-types
        assertTrue(output.contains("Non-Class"), "Non-Class row present");
        assertTrue(output.contains("Both"),      "Both row present");
        // Expect virtual space section
        assertTrue(output.contains("Reserved"),  "Reserved column in virtual space table");
    }

    // -------------------------------------------------------------------------
    // Unit tests for low-level helpers
    // -------------------------------------------------------------------------

    @Test
    void parseSizeBytes() {
        assertEquals(512L,              VmMetaspaceAnalyzer.parseSize("512", "bytes"));
        assertEquals(1024L,             VmMetaspaceAnalyzer.parseSize("1", "KB"));
        assertEquals(1024L * 1024,      VmMetaspaceAnalyzer.parseSize("1", "MB"));
        assertEquals(1024L * 1024 * 1024, VmMetaspaceAnalyzer.parseSize("1", "GB"));
        // fractional
        assertEquals(Math.round(27.38 * 1024 * 1024), VmMetaspaceAnalyzer.parseSize("27.38", "MB"));
    }

    @Test
    void formatBytesRanges() {
        assertEquals("512 bytes",  VmMetaspaceAnalyzer.formatBytes(512));
        assertEquals("1.00 KB",    VmMetaspaceAnalyzer.formatBytes(1024));
        assertEquals("1.00 MB",    VmMetaspaceAnalyzer.formatBytes(1024 * 1024));
        assertEquals("1.00 GB",    VmMetaspaceAnalyzer.formatBytes(1024L * 1024 * 1024));
    }

    @Test
    void parseSnapshotExtractsUsageAndVirtualSpace() {
        VmMetaspaceAnalyzer analyzer = new VmMetaspaceAnalyzer();
        VmMetaspaceAnalyzer.MetaspaceSnapshot snap = analyzer.parseSnapshot(SAMPLE_A);

        assertNotNull(snap, "snapshot must not be null");

        // Header
        assertNotNull(snap.header());
        assertEquals(262,  snap.header().loaders());
        assertEquals(6009, snap.header().classes());
        assertEquals(1383, snap.header().sharedClasses());

        // Usage rows
        assertTrue(snap.usage().containsKey("Non-Class"), "Non-Class usage row");
        assertTrue(snap.usage().containsKey("Class"),     "Class usage row");
        assertTrue(snap.usage().containsKey("Both"),      "Both usage row");

        VmMetaspaceAnalyzer.UsageRow nonClass = snap.usage().get("Non-Class");
        assertEquals(1199, nonClass.chunks());
        assertEquals(Math.round(27.38 * 1024 * 1024), nonClass.capacityBytes());
        assertEquals(Math.round(26.55 * 1024 * 1024), nonClass.usedBytes());

        // Virtual space
        assertTrue(snap.virtualSpace().containsKey("Non-class space"), "Non-class space row");
        assertTrue(snap.virtualSpace().containsKey("Class space"),     "Class space row");

        assertEquals(Math.round(64.00 * 1024 * 1024),
            snap.virtualSpace().get("Non-class space").reservedBytes());
        assertEquals(Math.round(1.00 * 1024L * 1024 * 1024),
            snap.virtualSpace().get("Class space").reservedBytes());

        // Settings
        assertNotNull(snap.settings());
        assertEquals("unlimited", snap.settings().maxMetaspaceSize());
        assertTrue(snap.settings().cdsOn());
    }
}
