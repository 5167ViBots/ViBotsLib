package com.vibots5167.lib.drive;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.Timer;

/**
 * Turns driver stick values into smooth velocity commands.
 *
 * - Round deadband on the translation stick, rescaled so speed ramps up from 0 (no jump at the
 *   deadband edge) and push direction is kept (no snapping to the axes).
 * - Rotation deadband (rescaled), optionally squared for finer control near center.
 * - Acceleration limit on the translation velocity as one vector (direction is preserved while it ramps)
 *   and on rotation rate. Match logs showed wheel accelerations 2-8x what carpet traction allows.
 *
 * <pre>
 *   // deadband 0.08, 9 m/s^2, 18 rad/s^2, squared rotation
 *   DriverInputShaper shaper = new DriverInputShaper(0.08, 9.0, 18.0, true);
 * </pre>
 *
 * Call {@link #update} once per loop, then read {@link #getVx()}, {@link #getVy()}, {@link #getOmega()}.
 * Call {@link #reset(double, double, double)} with the robot's measured velocity when the drive command
 * (re)starts, so it continues smoothly from however the robot is moving (stopped after disable/brake,
 * still moving after an aim command).
 */
public class DriverInputShaper {

    private final double deadband;
    private final double maxLinearAccel;
    private final double maxAngularAccel;
    private final boolean squareRotation;

    private double vx, vy, omega;          // current (limited) command
    private double lastTime = Double.NaN;

    /**
     * @param deadband        stick deadband, 0..1 (round on translation; resting sticks typically read under 0.05)
     * @param maxLinearAccel  m/s^2 translation acceleration limit (carpet traction is ~10-12)
     * @param maxAngularAccel rad/s^2 rotation acceleration limit
     * @param squareRotation  square the rotation stick for finer control near center
     */
    public DriverInputShaper(double deadband, double maxLinearAccel, double maxAngularAccel, boolean squareRotation) {
        this.deadband = deadband;
        this.maxLinearAccel = maxLinearAccel;
        this.maxAngularAccel = maxAngularAccel;
        this.squareRotation = squareRotation;
    }

    public void reset() {
        reset(0, 0, 0);
    }

    /** Start the limiter from the given velocity (same frame as the commands being produced). */
    public void reset(double vx, double vy, double omega) {
        this.vx = vx;
        this.vy = vy;
        this.omega = omega;
        lastTime = Double.NaN;
    }

    /**
     * @param forward  stick forward, -1..1 (+ = forward)
     * @param left     stick left, -1..1 (+ = left)
     * @param ccw      rotation stick, -1..1 (+ = counterclockwise)
     * @param maxSpeed m/s at full deflection
     * @param maxRate  rad/s at full deflection
     */
    public void update(double forward, double left, double ccw, double maxSpeed, double maxRate) {
        double now = Timer.getFPGATimestamp();
        double dt = Double.isNaN(lastTime) ? 0.02 : MathUtil.clamp(now - lastTime, 0.0, 0.1);
        lastTime = now;

        // round deadband on the translation stick, rescaled to 0..1
        double db = deadband;
        double mag = Math.hypot(forward, left);
        double targetVx = 0, targetVy = 0;
        if (mag > db) {
            double scaled = (Math.min(mag, 1.0) - db) / (1.0 - db);
            targetVx = forward / mag * scaled * maxSpeed;
            targetVy = left / mag * scaled * maxSpeed;
        }

        double rot = MathUtil.applyDeadband(ccw, db);
        if (squareRotation) {
            rot = Math.copySign(rot * rot, rot);
        }
        double targetOmega = rot * maxRate;

        // limit translation acceleration as a vector
        double dvx = targetVx - vx, dvy = targetVy - vy;
        double dv = Math.hypot(dvx, dvy);
        double maxStep = maxLinearAccel * dt;
        if (dv > maxStep) {
            dvx *= maxStep / dv;
            dvy *= maxStep / dv;
        }
        vx += dvx;
        vy += dvy;

        double maxRotStep = maxAngularAccel * dt;
        omega += MathUtil.clamp(targetOmega - omega, -maxRotStep, maxRotStep);
    }

    public double getVx() {
        return vx;
    }

    public double getVy() {
        return vy;
    }

    public double getOmega() {
        return omega;
    }
}
