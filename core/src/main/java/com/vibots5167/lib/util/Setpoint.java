package com.vibots5167.lib.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;

/**
 * A value that can be changed at runtime from buttons, clamped to a range.
 *
 * <pre>
 *   Setpoint shooterRps = new Setpoint("Launcher/Setpoint RPS", 30.0, 10.0, 60.0, 1.0, 10.0);
 *   driver.dpad.up.onTrue(shooterRps.incrementCommand());       // +1 per press
 *   driver.dpad.down.onTrue(shooterRps.decrementCommand());     // -1 per press
 *   driver.dpad.right.whileTrue(shooterRps.rampUpCommand());    // +10 per second while held
 *   driver.menu.start.onTrue(shooterRps.resetCommand());        // back to 30
 *   double rps = shooterRps.get();                              // or pass it anywhere a DoubleSupplier fits
 *   shooterRps.onChange(rps -> ...);                            // react when it changes
 * </pre>
 *
 * Commands don't require any subsystem, so they never interrupt what's using the value, and they
 * run while disabled (so values can be set up before a match).
 */
public class Setpoint implements DoubleSupplier {

    private final String name;      // SmartDashboard key, or null to not publish
    private final double defaultValue;
    private final double min;
    private final double max;
    private final double step;
    private final double ratePerSecond;
    private final List<DoubleConsumer> listeners = new ArrayList<>();

    private double value;

    /**
     * @param name          SmartDashboard key to publish the value under, or null
     * @param defaultValue  starting value, and what {@link #reset()} returns to
     * @param min           lowest allowed value
     * @param max           highest allowed value
     * @param step          change per increment / decrement
     * @param ratePerSecond change per second while a ramp command is held
     */
    public Setpoint(String name, double defaultValue, double min, double max, double step, double ratePerSecond) {
        if (min > max) {
            throw new IllegalArgumentException("Setpoint " + name + ": min > max");
        }
        this.name = name;
        this.min = min;
        this.max = max;
        this.defaultValue = MathUtil.clamp(defaultValue, min, max);
        this.step = step;
        this.ratePerSecond = ratePerSecond;
        this.value = this.defaultValue;
        publish();
    }

    /** Unpublished setpoint with no ramp rate. */
    public Setpoint(double defaultValue, double min, double max, double step) {
        this(null, defaultValue, min, max, step, 0.0);
    }

    // ------------------------------------------------------------------ value

    public double get() {
        return value;
    }

    @Override
    public double getAsDouble() {
        return value;
    }

    /** Set the value (clamped to [min, max]). Listeners run only if it actually changed. */
    public void set(double newValue) {
        double clamped = MathUtil.clamp(newValue, min, max);
        if (clamped == value) {
            return;
        }
        value = clamped;
        publish();
        for (DoubleConsumer listener : listeners) {
            listener.accept(value);
        }
    }

    public void increment() {
        set(value + step);
    }

    public void decrement() {
        set(value - step);
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean atMin() {
        return value <= min;
    }

    public boolean atMax() {
        return value >= max;
    }

    /** Run {@code listener} with the new value every time it changes. */
    public Setpoint onChange(DoubleConsumer listener) {
        listeners.add(listener);
        return this;
    }

    // ------------------------------------------------------------------ commands

    /** +step once. */
    public Command incrementCommand() {
        return Commands.runOnce(this::increment).ignoringDisable(true).withName(label("Increment"));
    }

    /** -step once. */
    public Command decrementCommand() {
        return Commands.runOnce(this::decrement).ignoringDisable(true).withName(label("Decrement"));
    }

    /** Back to the default value. */
    public Command resetCommand() {
        return Commands.runOnce(this::reset).ignoringDisable(true).withName(label("Reset"));
    }

    /** Set to a specific value once. */
    public Command setCommand(double newValue) {
        return Commands.runOnce(() -> set(newValue)).ignoringDisable(true).withName(label("Set " + newValue));
    }

    /** Increase at ratePerSecond while running (bind with whileTrue). */
    public Command rampUpCommand() {
        return rampCommand(ratePerSecond).withName(label("Ramp Up"));
    }

    /** Decrease at ratePerSecond while running (bind with whileTrue). */
    public Command rampDownCommand() {
        return rampCommand(-ratePerSecond).withName(label("Ramp Down"));
    }

    /**
     * Change at {@code ratePerSecond} times the supplier's value while running - e.g. a joystick axis
     * or trigger (-1..1) for proportional adjustment.
     */
    public Command adjustCommand(DoubleSupplier input) {
        double[] last = new double[1];
        return Commands.run(() -> {
                    double now = Timer.getFPGATimestamp();
                    double dt = Math.min(now - last[0], 0.1);
                    last[0] = now;
                    set(value + input.getAsDouble() * ratePerSecond * dt);
                })
            .beforeStarting(() -> last[0] = Timer.getFPGATimestamp())
            .ignoringDisable(true)
            .withName(label("Adjust"));
    }

    private Command rampCommand(double rate) {
        return adjustCommand(() -> Math.signum(rate)).withName(label("Ramp"));
    }

    // ------------------------------------------------------------------ helpers

    private void publish() {
        if (name != null) {
            SmartDashboard.putNumber(name, value);
        }
    }

    private String label(String action) {
        return (name != null ? name : "Setpoint") + " " + action;
    }

    @Override
    public String toString() {
        return (name != null ? name + "=" : "") + value;
    }
}
