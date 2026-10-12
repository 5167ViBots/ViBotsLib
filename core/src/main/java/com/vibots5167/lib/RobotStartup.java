package com.vibots5167.lib;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.vibots5167.lib.logging.LogCleanup;
import com.vibots5167.lib.logging.NetworkLatencyMonitor;

import edu.wpi.first.wpilibj.RobotBase;

/**
 * One-call robot startup: free internal flash, start match logging (USB stick only), start network
 * latency monitoring. Call once at the top of robotInit / the Robot constructor, before anything opens a log.
 *
 * <pre>
 *   RobotStartup.standard().run();                       // defaults below
 *
 *   RobotStartup.builder()                               // pick and choose
 *       .cleanLogs(Path.of("/home/lvuser/logs"), 300L &lt;&lt; 20)
 *       .latency(NetworkLatencyMonitor.standard().addTcp("Limelight", () -> "10.51.67.12", 5801))
 *       .run();
 * </pre>
 *
 * CTRE robots: use {@code CtreStartup.standard()} from ViBotsLib-CTRE, which fills in the Phoenix
 * signal logger hooks.
 *
 * Steps run in this order, each only if configured:
 * <ol>
 *   <li>{@link LogCleanup} - delete the oldest logs on internal flash until enough space is free.</li>
 *   <li>USB logging - if a USB stick is mounted, run the start hook; otherwise run the no-USB hook
 *       (e.g. stop a vendor logger auto-starting on internal flash). Real robot only.</li>
 *   <li>{@link NetworkLatencyMonitor} - started on its background thread. Real robot only.</li>
 * </ol>
 */
public final class RobotStartup {

    /** Where the roboRIO mounts a USB stick. */
    public static final Path USB_DRIVE = Path.of("/u");

    private Path cleanupDir;
    private long cleanupMinFreeBytes;
    private Runnable usbLogStart;
    private Runnable noUsbLog;
    private Supplier<NetworkLatencyMonitor> latency;
    private NetworkLatencyMonitor.LogSink latencySink;

    private NetworkLatencyMonitor latencyMonitor;
    private boolean ran;

    private RobotStartup() {}

    /** Nothing enabled; add steps with the builder methods. */
    public static RobotStartup builder() {
        return new RobotStartup();
    }

    /** Log cleanup with defaults and the standard latency monitor (radio, PhotonVision, DS). */
    public static RobotStartup standard() {
        return builder().cleanLogs().latency();
    }

    // ------------------------------------------------------------------ configuration

    /** Delete old logs in /home/lvuser/logs until 200 MB are free. */
    public RobotStartup cleanLogs() {
        return cleanLogs(LogCleanup.DEFAULT_LOG_DIR, LogCleanup.DEFAULT_MIN_FREE_BYTES);
    }

    public RobotStartup cleanLogs(Path logDir, long minFreeBytes) {
        cleanupDir = logDir;
        cleanupMinFreeBytes = minFreeBytes;
        return this;
    }

    /**
     * Log only to a USB stick: run {@code start} when one is mounted at /u, otherwise run {@code noUsb}
     * (either may be null).
     */
    public RobotStartup usbLogging(Runnable start, Runnable noUsb) {
        usbLogStart = start;
        noUsbLog = noUsb;
        return this;
    }

    /** The standard latency monitor (radio, PhotonVision, DS). */
    public RobotStartup latency() {
        latency = NetworkLatencyMonitor::standard;
        return this;
    }

    /** A custom latency monitor (not started yet - run() starts it). */
    public RobotStartup latency(NetworkLatencyMonitor monitor) {
        latency = () -> monitor;
        return this;
    }

    /** Also record latency samples here, e.g. {@code SignalLogger::writeDouble} or {@code LogSink.dataLog()}. */
    public RobotStartup logLatencyTo(NetworkLatencyMonitor.LogSink sink) {
        latencySink = sink;
        return this;
    }

    public RobotStartup noLogCleanup() {
        cleanupDir = null;
        return this;
    }

    public RobotStartup noLatency() {
        latency = null;
        return this;
    }

    // ------------------------------------------------------------------ run

    /** Run the configured steps. Only the first call does anything. */
    public synchronized RobotStartup run() {
        if (ran) {
            return this;
        }
        ran = true;

        if (cleanupDir != null) {
            LogCleanup.run(cleanupDir, cleanupMinFreeBytes);
        }

        if (RobotBase.isReal() && (usbLogStart != null || noUsbLog != null)) {
            if (Files.isDirectory(USB_DRIVE)) {
                System.out.println("[RobotStartup] USB drive found - starting logging");
                runHook(usbLogStart);
            } else {
                System.out.println("[RobotStartup] No USB drive at " + USB_DRIVE + " - logging not started");
                runHook(noUsbLog);
            }
        }

        if (latency != null && RobotBase.isReal()) {
            latencyMonitor = latency.get();
            if (latencySink != null) {
                latencyMonitor.logTo(latencySink);
            }
            latencyMonitor.start();
        }
        return this;
    }

    /** The started latency monitor, or null if it is off, in simulation, or run() hasn't been called. */
    public NetworkLatencyMonitor getLatencyMonitor() {
        return latencyMonitor;
    }

    private static void runHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        try {
            hook.run();
        } catch (Exception e) {
            System.out.println("[RobotStartup] logging hook failed: " + e);
        }
    }
}
