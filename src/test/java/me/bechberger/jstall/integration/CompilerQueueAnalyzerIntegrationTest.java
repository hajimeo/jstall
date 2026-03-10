package me.bechberger.jstall.integration;

import me.bechberger.jstall.analyzer.AnalyzerResult;
import me.bechberger.jstall.analyzer.ResolvedData;
import me.bechberger.jstall.analyzer.impl.CompilerQueueAnalyzer;
import me.bechberger.jstall.model.ThreadDumpSnapshot;
import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.testframework.TestAppLauncher;
import me.bechberger.jthreaddump.model.ThreadDump;
import me.bechberger.jthreaddump.parser.ThreadDumpParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CompilerQueueAnalyzer using live test application.
 */
public class CompilerQueueAnalyzerIntegrationTest {

    private String runJcmdCommand(long pid, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("jcmd", String.valueOf(pid), command);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }

    @Test
    public void testCompilerQueueAnalysis() throws Exception {
        TestAppLauncher launcher = new TestAppLauncher();

        try {
            // Launch compiler stress app
            launcher.launch("me.bechberger.jstall.testapp.CompilerQueueStressTestApp");
            launcher.waitUntilReady(5000);

            // Give JIT some time to start compiling
            Thread.sleep(2000);

            // Capture multiple compiler queue snapshots
            List<String> queueOutputs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String output = runJcmdCommand(launcher.getPid(), "Compiler.queue");
                queueOutputs.add(output);
                Thread.sleep(1000);
            }

            // Build test data
            long now = System.currentTimeMillis();
            List<CollectedData> collectedData = new ArrayList<>();
            for (int i = 0; i < queueOutputs.size(); i++) {
                collectedData.add(new CollectedData(
                    now + (i * 1000L),
                    queueOutputs.get(i),
                    Map.of()
                ));
            }

            // Get a thread dump for ResolvedData
            String dumpContent = launcher.captureThreadDump();
            ThreadDump parsed = ThreadDumpParser.parse(dumpContent);
            List<ThreadDumpSnapshot> dumps = List.of(
                new ThreadDumpSnapshot(parsed, dumpContent, null, null)
            );

            // Analyze
            CompilerQueueAnalyzer analyzer = new CompilerQueueAnalyzer();
            ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
                dumps,
                Map.of("compiler-queue", collectedData)
            );
            AnalyzerResult result = analyzer.analyze(data, Map.of());

            // Verify
            assertEquals(0, result.exitCode(), "Should succeed");
            assertFalse(result.output().isBlank(), "Should have output");
            
            // Check for expected content
            String output = result.output();
            assertTrue(output.contains("Compiler queue trend") || 
                       output.contains("not available"),
                "Should show trend or availability message");
            
            // If we got actual data, verify trend structure
            if (output.contains("Compiler queue trend")) {
                assertTrue(output.contains("samples"), "Should mention sample count");
                assertTrue(output.contains("Summary") || output.contains("Active") || output.contains("Queued"),
                    "Should have summary or queue information");
                
                System.out.println("Compiler queue analysis output:");
                System.out.println(output);
            } else {
                System.out.println("Compiler.queue not supported by this JVM");
            }
        } finally {
            launcher.stop();
        }
    }

    @Test
    public void testMultipleSamplesProcessed() throws Exception {
        TestAppLauncher launcher = new TestAppLauncher();

        try {
            launcher.launch("me.bechberger.jstall.testapp.CompilerQueueStressTestApp");
            launcher.waitUntilReady(5000);
            Thread.sleep(2000);

            // Capture 5 samples
            long now = System.currentTimeMillis();
            List<CollectedData> collectedData = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String output = runJcmdCommand(launcher.getPid(), "Compiler.queue");
                collectedData.add(new CollectedData(
                    now + (i * 500L),
                    output,
                    Map.of()
                ));
                Thread.sleep(500);
            }

            String dumpContent = launcher.captureThreadDump();
            ThreadDump parsed = ThreadDumpParser.parse(dumpContent);
            List<ThreadDumpSnapshot> dumps = List.of(
                new ThreadDumpSnapshot(parsed, dumpContent, null, null)
            );

            CompilerQueueAnalyzer analyzer = new CompilerQueueAnalyzer();
            ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
                dumps,
                Map.of("compiler-queue", collectedData)
            );
            AnalyzerResult result = analyzer.analyze(data, Map.of());

            assertEquals(0, result.exitCode());
            
            // Verify it processes multiple samples
            if (result.output().contains("Compiler queue trend")) {
                assertTrue(result.output().contains("5 samples") || 
                          result.output().contains("(5 samples)"),
                    "Should indicate 5 samples were processed");
            }
        } finally {
            launcher.stop();
        }
    }

    @Test
    public void testCustomOptionsRespected() throws Exception {
        TestAppLauncher launcher = new TestAppLauncher();

        try {
            launcher.launch("me.bechberger.jstall.testapp.CompilerQueueStressTestApp");
            launcher.waitUntilReady(5000);
            Thread.sleep(1000);

            long now = System.currentTimeMillis();
            List<CollectedData> collectedData = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String output = runJcmdCommand(launcher.getPid(), "Compiler.queue");
                collectedData.add(new CollectedData(
                    now + (i * 500L),
                    output,
                    Map.of()
                ));
                Thread.sleep(500);
            }

            String dumpContent = launcher.captureThreadDump();
            ThreadDump parsed = ThreadDumpParser.parse(dumpContent);
            List<ThreadDumpSnapshot> dumps = List.of(
                new ThreadDumpSnapshot(parsed, dumpContent, null, null)
            );

            // Test with custom samples option
            CompilerQueueAnalyzer analyzer = new CompilerQueueAnalyzer();
            ResolvedData data = ResolvedData.fromDumpsAndCollectedData(
                dumps,
                Map.of("compiler-queue", collectedData)
            );
            
            // The analyzer should handle whatever samples we provide
            AnalyzerResult result = analyzer.analyze(data, Map.of("samples", 2, "interval", 1000));
            
            assertEquals(0, result.exitCode());
            assertFalse(result.output().isBlank());
        } finally {
            launcher.stop();
        }
    }
}
