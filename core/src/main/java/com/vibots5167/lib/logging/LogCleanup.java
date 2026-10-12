package com.vibots5167.lib.logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import edu.wpi.first.wpilibj.RobotBase;

/**
 * Frees roboRIO internal flash at startup by deleting the oldest log files.
 *
 * Meant for robots that log to a USB stick, so anything on internal flash is left over from older code
 * or Phoenix's no-USB fallback. A full disk caused low-memory problems in practice. Call from robotInit:
 * <pre>
 *   LogCleanup.run();                                              // /home/lvuser/logs, keep 200 MB free
 *   LogCleanup.run(Path.of("/home/lvuser/logs"), 300L &lt;&lt; 20);      // custom
 * </pre>
 */
public final class LogCleanup {

    public static final Path DEFAULT_LOG_DIR = Path.of("/home/lvuser/logs");
    public static final long DEFAULT_MIN_FREE_BYTES = 200L * 1024 * 1024; // keep at least 200 MB free

    private LogCleanup() {}

    /** Delete the oldest .hoot / .wpilog files in /home/lvuser/logs until 200 MB are free. */
    public static void run() {
        run(DEFAULT_LOG_DIR, DEFAULT_MIN_FREE_BYTES);
    }

    /** Delete the oldest .hoot / .wpilog files under {@code logDir} until {@code minFreeBytes} are free. Real robot only. */
    public static void run(Path logDir, long minFreeBytes) {
        if (!RobotBase.isReal() || !Files.isDirectory(logDir)) {
            return;
        }
        File disk = logDir.toFile();
        long free = disk.getUsableSpace();
        if (free >= minFreeBytes) {
            System.out.printf("[LogCleanup] %d MB free on internal flash, nothing to do%n", free >> 20);
            return;
        }

        List<Path> logs;
        try (Stream<Path> files = Files.walk(logDir)) {
            logs = files
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".hoot") || p.toString().endsWith(".wpilog"))
                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified())) // oldest first
                .toList();
        } catch (IOException e) {
            System.out.println("[LogCleanup] Could not list " + logDir + ": " + e);
            return;
        }

        int deleted = 0;
        for (Path log : logs) {
            if (disk.getUsableSpace() >= minFreeBytes) {
                break;
            }
            try {
                Files.delete(log);
                deleted++;
            } catch (IOException e) {
                System.out.println("[LogCleanup] Could not delete " + log + ": " + e);
            }
        }
        System.out.printf("[LogCleanup] Deleted %d old log(s), %d MB -> %d MB free on internal flash%n",
            deleted, free >> 20, disk.getUsableSpace() >> 20);
    }
}
