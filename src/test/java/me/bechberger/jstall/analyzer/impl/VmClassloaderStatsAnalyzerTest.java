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

class VmClassloaderStatsAnalyzerTest {

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
    void groupsByTypeSortsAndAddsTotal() {
        String sample = """
            7651:
            ClassLoader         Parent              CLD*               Classes   ChunkSz   BlockSz  Type
            0x00000090010b2e50  0x0000009000069008  0x0000000aae00bf20      18    141312    119848  org.eclipse.osgi.internal.loader.EquinoxClassLoader
            0x00000090000976d8  0x0000000000000000  0x0000000aadccb7a0       1      4096      3176  jdk.internal.reflect.DelegatingClassLoader
            0x00000090010b2e50  0x0000009000069008  0x0000000aadc3a260      34    187392    155984  org.eclipse.osgi.internal.loader.EquinoxClassLoader
            """;

        VmClassloaderStatsAnalyzer analyzer = new VmClassloaderStatsAnalyzer();
        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-classloader-stats", List.of(new CollectedData(1L, sample, Map.of())))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        String output = result.output();
        assertTrue(output.contains("VM.classloader_stats (1 sample):"));

        assertTrue(output.contains("org.eclipse.osgi.internal.loader.EquinoxClassLoader"));
        assertTrue(output.contains("52"));
        assertTrue(output.contains("328,704"));
        assertTrue(output.contains("275,832"));

        assertTrue(output.contains("jdk.internal.reflect.DelegatingClassLoader"));
        assertTrue(output.contains("1"));

        assertTrue(output.contains("Total"));
        assertTrue(output.contains("53"));
        assertTrue(output.contains("332,800"));
        assertTrue(output.contains("279,008"));

        assertTrue(output.indexOf("org.eclipse.osgi.internal.loader.EquinoxClassLoader")
            < output.indexOf("jdk.internal.reflect.DelegatingClassLoader"));
    }

    @Test
    void showsTrendForMultipleSamples() {
        String previous = """
            7651:
            ClassLoader         Parent              CLD*               Classes   ChunkSz   BlockSz  Type
            0x01  0x02  0x03      30    200000    150000  org.eclipse.osgi.internal.loader.EquinoxClassLoader
            0x04  0x05  0x06       5      8000      7000  jdk.internal.reflect.DelegatingClassLoader
            """;

        String latest = """
            7651:
            ClassLoader         Parent              CLD*               Classes   ChunkSz   BlockSz  Type
            0x11  0x12  0x13      34    240000    180000  org.eclipse.osgi.internal.loader.EquinoxClassLoader
            0x14  0x15  0x16       1      4096      3176  jdk.internal.reflect.DelegatingClassLoader
            """;

        VmClassloaderStatsAnalyzer analyzer = new VmClassloaderStatsAnalyzer();
        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-classloader-stats", List.of(
                new CollectedData(1L, previous, Map.of()),
                new CollectedData(2L, latest, Map.of())
            ))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        String output = result.output();
        assertTrue(output.contains("VM.classloader_stats (2 samples):"));
        assertTrue(output.contains("Trend"));

        assertTrue(output.contains("↑ +4 cls, +40000 chunk, +30000 block"));
        assertTrue(output.contains("↓ -4 cls, -3904 chunk, -3824 block"));
        assertTrue(output.contains("Total"));
        assertTrue(output.contains("→ +0 cls, +36096 chunk, +26176 block"));
    }

    @Test
    void returnsNotAvailableForUnsupportedOutput() {
        VmClassloaderStatsAnalyzer analyzer = new VmClassloaderStatsAnalyzer();

        ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
            List.of(createDummySnapshot()),
            Map.of("vm-classloader-stats", List.of(
                new CollectedData(1L, "Command not supported", Map.of())
            ))
        );

        AnalyzerResult result = analyzer.analyze(data, Map.of());

        assertTrue(result.shouldDisplay());
        assertTrue(result.output().contains("not available"));
    }
}
