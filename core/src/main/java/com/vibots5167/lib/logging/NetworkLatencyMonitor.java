package com.vibots5167.lib.logging;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.ConnectionInfo;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.IntegerPublisher;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.util.datalog.DoubleLogEntry;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.RobotController;

/**
 * Measures round-trip latency from the robot to other devices on the robot network, on a background
 * thread so a slow or missing device never stalls the robot loop.
 *
 * <pre>
 *   NetworkLatencyMonitor network = NetworkLatencyMonitor.standard().start();   // radio, PhotonVision, DS
 *   double ms = network.getLatencyMs("Radio");                                    // NaN if unreachable
 * </pre>
 *
 * Results publish to NetworkTables under {@code Network/<name>/} (LatencyMs, Reachable, Host, Failures).
 * Add a log sink to also record {@code Network/<name>/LatencyMs} (NaN when a probe fails) in a match log,
 * so a dropped connection mid-match shows up alongside everything else:
 * <pre>
 *   NetworkLatencyMonitor.standard().logTo(SignalLogger::writeDouble).start();   // Phoenix hoot log
 *   NetworkLatencyMonitor.standard().logTo(LogSink.dataLog()).start();           // WPILib .wpilog
 * </pre>
 *
 * Two probe types:
 * <ul>
 *   <li><b>TCP</b> - times a connect to an open port (or the "refused" reply from a closed one). Needs no
 *       special permissions and works in simulation.</li>
 *   <li><b>Ping</b> - runs the system ping (ICMP). Use for devices with no open TCP port, like the
 *       Driver Station laptop. Windows firewall blocks ping by default; allow "File and Printer Sharing
 *       (Echo Request - ICMPv4-In)" on the DS laptop or it will read unreachable.</li>
 * </ul>
 */
public class NetworkLatencyMonitor {

    private static final Pattern PING_TIME = Pattern.compile("time\\s*[=<]\\s*([0-9.]+)\\s*ms");
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final List<Endpoint> endpoints = new ArrayList<>();
    private final long periodMs;
    private final int timeoutMs;
    private final NetworkTable table = NetworkTableInstance.getDefault().getTable("Network");
    private volatile LogSink logSink = LogSink.NONE;
    private Thread thread;

    /** Where latency samples are recorded besides NetworkTables. */
    @FunctionalInterface
    public interface LogSink {
        /** Matches Phoenix's {@code SignalLogger.writeDouble(key, value, units)}, so a method reference fits. */
        void write(String key, double value, String units);

        LogSink NONE = (key, value, units) -> {};

        /** WPILib DataLog (the log {@code DataLogManager.start()} writes). */
        static LogSink dataLog() {
            Map<String, DoubleLogEntry> entries = new ConcurrentHashMap<>();
            return (key, value, units) -> entries
                .computeIfAbsent(key, k -> new DoubleLogEntry(DataLogManager.getLog(), k))
                .append(value);
        }
    }

    /**
     * @param periodSeconds time between rounds of probes
     * @param timeoutMs     how long to wait for each device before calling it unreachable
     */
    public NetworkLatencyMonitor(double periodSeconds, int timeoutMs) {
        this.periodMs = (long) (periodSeconds * 1000);
        this.timeoutMs = timeoutMs;
    }

    /**
     * Radio (TCP 80), PhotonVision (TCP 5800, found by its NetworkTables connection), and the Driver
     * Station laptop (ping, found by a dashboard's NetworkTables connection), every second.
     */
    public static NetworkLatencyMonitor standard() {
        return new NetworkLatencyMonitor(1.0, 500)
            .addTcp("Radio", () -> teamAddress(1), 80)
            .addTcp("PhotonVision", ntClient("photon", teamAddress(11)), 5800)
            .addPing("DriverStation", dashboardHost());
    }

    /** Time a TCP connect to {@code host:port}. */
    public NetworkLatencyMonitor addTcp(String name, Supplier<String> host, int port) {
        endpoints.add(new Endpoint(name, host, port));
        return this;
    }

    /** Time an ICMP ping to {@code host}. */
    public NetworkLatencyMonitor addPing(String name, Supplier<String> host) {
        endpoints.add(new Endpoint(name, host, 0));
        return this;
    }

    /** Also record every sample here (see {@link LogSink}). */
    public NetworkLatencyMonitor logTo(LogSink sink) {
        logSink = sink != null ? sink : LogSink.NONE;
        return this;
    }

    /** Start probing on a daemon thread. Call once, after adding endpoints. */
    public synchronized NetworkLatencyMonitor start() {
        if (thread == null) {
            thread = new Thread(this::run, "NetworkLatencyMonitor");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        return this;
    }

    /** Last round-trip time in milliseconds, or NaN if the last probe failed or the name is unknown. */
    public double getLatencyMs(String name) {
        Endpoint e = find(name);
        return e != null ? e.latencyMs : Double.NaN;
    }

    public boolean isReachable(String name) {
        return !Double.isNaN(getLatencyMs(name));
    }

    // ------------------------------------------------------------------ host helpers

    /** 10.TE.AM.x for this robot's team number, e.g. teamAddress(1) = 10.51.67.1 for team 5167. */
    public static String teamAddress(int lastOctet) {
        int team = RobotController.getTeamNumber();
        return "10." + (team / 100) + "." + (team % 100) + "." + lastOctet;
    }

    /**
     * IP of the first NetworkTables client whose id contains {@code idContains} (case-insensitive),
     * or {@code fallback} if none is connected.
     */
    public static Supplier<String> ntClient(String idContains, String fallback) {
        String needle = idContains.toLowerCase(Locale.ROOT);
        return () -> {
            for (ConnectionInfo c : NetworkTableInstance.getDefault().getConnections()) {
                if (c.remote_id.toLowerCase(Locale.ROOT).contains(needle)) {
                    return c.remote_ip;
                }
            }
            return fallback;
        };
    }

    /**
     * IP of the Driver Station laptop, taken from a dashboard's (Shuffleboard, Elastic, AdvantageScope,
     * ...) NetworkTables connection. Null when no dashboard is connected - the robot can't otherwise
     * see the DS's address.
     */
    public static Supplier<String> dashboardHost() {
        return () -> {
            for (ConnectionInfo c : NetworkTableInstance.getDefault().getConnections()) {
                String id = c.remote_id.toLowerCase(Locale.ROOT);
                if (!id.contains("photon") && !c.remote_ip.startsWith("127.")) {
                    return c.remote_ip;
                }
            }
            return null;
        };
    }

    // ------------------------------------------------------------------ probing

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            long start = System.nanoTime();
            for (Endpoint e : endpoints) {
                e.probe();
            }
            long sleepMs = periodMs - (System.nanoTime() - start) / 1_000_000;
            try {
                Thread.sleep(Math.max(sleepMs, 50));
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    private Endpoint find(String name) {
        for (Endpoint e : endpoints) {
            if (e.name.equals(name)) {
                return e;
            }
        }
        return null;
    }

    /** Round trip in ms, or NaN. */
    private double tcp(String host, int port) {
        try (Socket socket = new Socket()) {
            InetAddress address = InetAddress.getByName(host); // resolve before timing
            long t0 = System.nanoTime();
            try {
                socket.connect(new InetSocketAddress(address, port), timeoutMs);
            } catch (ConnectException refused) {
                // Port closed but the device answered - still a valid round trip.
                if (refused.getMessage() == null || !refused.getMessage().toLowerCase(Locale.ROOT).contains("refused")) {
                    return Double.NaN;
                }
            }
            return (System.nanoTime() - t0) / 1e6;
        } catch (Exception ex) {
            return Double.NaN;
        }
    }

    /** Round trip in ms, or NaN. */
    private double ping(String host) {
        List<String> cmd = WINDOWS
            ? List.of("ping", "-n", "1", "-w", Integer.toString(timeoutMs), host)
            : List.of("ping", "-c", "1", "-W", Integer.toString(Math.max(1, (timeoutMs + 999) / 1000)), host);
        Process process = null;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            double result = Double.NaN;
            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = out.readLine()) != null) {
                    Matcher m = PING_TIME.matcher(line);
                    if (m.find() && Double.isNaN(result)) {
                        result = Double.parseDouble(m.group(1));
                    }
                }
            }
            process.waitFor(timeoutMs + 1000L, TimeUnit.MILLISECONDS);
            return result;
        } catch (Exception ex) {
            return Double.NaN;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private final class Endpoint {
        final String name;
        final Supplier<String> host;
        final int tcpPort; // 0 = ping
        final String logKey;
        final DoublePublisher latencyPub;
        final BooleanPublisher reachablePub;
        final StringPublisher hostPub;
        final IntegerPublisher failuresPub;

        volatile double latencyMs = Double.NaN;
        long failures;

        Endpoint(String name, Supplier<String> host, int tcpPort) {
            this.name = name;
            this.host = host;
            this.tcpPort = tcpPort;
            this.logKey = "Network/" + name + "/LatencyMs";
            NetworkTable t = table.getSubTable(name);
            latencyPub = t.getDoubleTopic("LatencyMs").publish();
            reachablePub = t.getBooleanTopic("Reachable").publish();
            hostPub = t.getStringTopic("Host").publish();
            failuresPub = t.getIntegerTopic("Failures").publish();
        }

        void probe() {
            String h;
            try {
                h = host.get();
            } catch (Exception ex) {
                h = null;
            }
            double ms = Double.NaN;
            if (h != null && !h.isEmpty()) {
                ms = tcpPort > 0 ? tcp(h, tcpPort) : ping(h);
            }
            if (Double.isNaN(ms)) {
                failures++;
            }
            latencyMs = ms;
            latencyPub.set(ms);
            reachablePub.set(!Double.isNaN(ms));
            hostPub.set(h != null ? h : "");
            failuresPub.set(failures);
            try {
                logSink.write(logKey, ms, "ms");
            } catch (Exception ex) {
                // never let logging kill the probe thread
            }
        }
    }
}
