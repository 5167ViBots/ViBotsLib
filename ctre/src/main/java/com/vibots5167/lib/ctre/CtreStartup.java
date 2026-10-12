package com.vibots5167.lib.ctre;

import com.ctre.phoenix6.SignalLogger;
import com.vibots5167.lib.RobotStartup;

/**
 * {@link RobotStartup} presets for robots using Phoenix 6.
 *
 * <pre>
 *   CtreStartup.standard().run();   // in robotInit, before anything else
 * </pre>
 *
 * Adds to {@link RobotStartup#standard()}:
 * <ul>
 *   <li>Phoenix signal logging (hoot) only to a USB stick. With no stick, Phoenix's automatic logging is
 *       turned off so an FMS match doesn't fill internal flash (which caused low-memory problems).</li>
 *   <li>Network latency samples recorded in the hoot log, next to everything else.</li>
 * </ul>
 * Remove any other {@code SignalLogger.start()} calls from robot code when using this.
 */
public final class CtreStartup {

    private CtreStartup() {}

    /** Log cleanup, USB-only hoot logging, and the standard latency monitor logging to hoot. */
    public static RobotStartup standard() {
        return applyTo(RobotStartup.standard());
    }

    /** Add the Phoenix logging hooks to an existing configuration. */
    public static RobotStartup applyTo(RobotStartup startup) {
        try {
            return startup
                .usbLogging(SignalLogger::start, () -> SignalLogger.enableAutoLogging(false))
                .logLatencyTo(SignalLogger::writeDouble);
        } catch (NoClassDefFoundError e) {
            throw missingPhoenix(e);
        }
    }

    /**
     * ViBotsLib-CTRE doesn't declare Phoenix as a vendordep requirement (so either Phoenix 6 or Phoenix 6
     * replay works), so a missing Phoenix only shows up when this code runs.
     */
    private static IllegalStateException missingPhoenix(NoClassDefFoundError e) {
        return new IllegalStateException("ViBotsLib-CTRE needs the Phoenix 6 (or Phoenix 6 replay) vendordep. "
            + "Install it from WPILib: Manage Vendor Libraries.", e);
    }
}
