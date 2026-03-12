package me.bechberger.jstall.cli;

import me.bechberger.femtocli.RunResult;
import me.bechberger.jstall.Main;
import me.bechberger.jstall.provider.RecordingTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecordArchiveCommandTest {

    @Test
    void testRecordSummaryPrintsReadme(@TempDir Path tempDir) throws Exception {
        Path recordingFile = tempDir.resolve("summary-recording.zip");
        createRecording(recordingFile);

        RunResult result = Util.run("record", "summary", recordingFile.toString());

        assertEquals(0, result.exitCode(), () ->
            "Summary command should succeed. stderr: " + result.err());
        assertTrue(result.out().contains("JStall Recording Archive"), () ->
            "Should print README content. Output: " + result.out());
        assertTrue(result.out().contains("Recorded JVMs:"), () ->
            "Should include JVM section. Output: " + result.out());
        assertTrue(result.out().contains("12345"), () ->
            "Should mention recorded PID. Output: " + result.out());
    }

    @Test
    void testRecordSummaryMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.zip");

        RunResult result = Util.run("record", "summary", missing.toString());

        assertNotEquals(0, result.exitCode(), "Summary should fail for missing file");
        assertTrue(result.err().contains("Error reading recording summary:"), () ->
            "Should print summary error message. stderr: " + result.err());
    }

    @Test
    void testRecordExtractExtractsArchive(@TempDir Path tempDir) throws Exception {
        Path recordingFile = tempDir.resolve("extract-recording.zip");
        Path outputDir = tempDir.resolve("extracted");
        createRecording(recordingFile);

        RunResult result = Util.run("record", "extract", recordingFile.toString(), outputDir.toString());

        assertEquals(0, result.exitCode(), () ->
            "Extract command should succeed. stderr: " + result.err());
        assertTrue(result.out().contains("Extracted"), () ->
            "Should print extraction summary. stdout: " + result.out());

        assertTrue(Files.exists(outputDir.resolve("README.md")), "README should be extracted");
        assertTrue(Files.exists(outputDir.resolve("metadata.json")), "metadata.json should be extracted");
        assertTrue(Files.exists(outputDir.resolve("12345/thread-dumps/000-1700000000000.txt")),
            "Thread dump should be extracted");
    }

    @Test
    void testRecordExtractRejectsZipSlip(@TempDir Path tempDir) throws Exception {
        Path maliciousZip = tempDir.resolve("malicious.zip");
        Path outputDir = tempDir.resolve("out");
        Path escaped = tempDir.resolve("escaped.txt");

        try (var zipOut = new java.util.zip.ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            zipOut.putNextEntry(new java.util.zip.ZipEntry("../escaped.txt"));
            zipOut.write("evil".getBytes());
            zipOut.closeEntry();
        }

        RunResult result = Util.run("record", "extract", maliciousZip.toString(), outputDir.toString());

        assertNotEquals(0, result.exitCode(), "Extract should fail for ZIP slip entries");
    }

    @Test
    void testRecordCreateFailsEarlyForMissingPid(@TempDir Path tempDir) {
        Path outputZip = tempDir.resolve("missing-pid.zip");

        RunResult result = Util.run("record", "999999999", "-o", outputZip.toString());

        assertNotEquals(0, result.exitCode(), "Record should fail for non-existent PID");
        assertTrue(result.err().contains("No JVM targets found for: 999999999"), () ->
            "Should fail early with missing target message. stderr: " + result.err());
        assertFalse(Files.exists(outputZip), "Should not create output ZIP when no target is recordable");
    }

    private static void createRecording(Path outputFile) throws Exception {
        new RecordingTestBuilder(Main.VERSION)
            .withJvm(12345, "com.example.App")
                .withThreadDump("\"main\" #1\n", 1700000000000L)
                .withSystemProperties("java.version=21\n", 1700000001000L)
                .build()
            .build(outputFile);
    }
}
