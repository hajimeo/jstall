package me.bechberger.jstall.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for loading sample thread dumps from test resources.
 */
public class ThreadDumpTestResources {

    private static final Path RESOURCE_DIR = Paths.get("src/test/resources/thread-dumps");

    /**
     * Loads a thread dump from test resources.
     * Available dumps: deadlock.txt, busy-work-{000,001,002}.txt, normal-{000,001,002}.txt
     */
    public static String loadThreadDump(String filename) throws IOException {
        Path file = RESOURCE_DIR.resolve(filename);
        if (!Files.exists(file)) {
            throw new IOException("Thread dump not found: " + filename);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Loads all busy-work thread dumps (0, 1, 2)
     */
    public static String[] loadBusyWorkDumps() throws IOException {
        return new String[]{
            loadThreadDump("busy-work-000.txt"),
            loadThreadDump("busy-work-001.txt"),
            loadThreadDump("busy-work-002.txt")
        };
    }

    /**
     * Loads all normal thread dumps (0, 1, 2)
     */
    public static String[] loadNormalDumps() throws IOException {
        return new String[]{
            loadThreadDump("normal-000.txt"),
            loadThreadDump("normal-001.txt"),
            loadThreadDump("normal-002.txt")
        };
    }

    /**
     * Loads the deadlock thread dump
     */
    public static String loadDeadlockDump() throws IOException {
        return loadThreadDump("deadlock.txt");
    }
}
