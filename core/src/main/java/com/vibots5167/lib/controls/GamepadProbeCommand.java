package com.vibots5167.lib.controls;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;

/**
 * Shows what the Driver Station reports for one port, to build a {@link GamepadLayout} for a new controller.
 * Works while disabled. SmartDashboard "GamepadProbe/<port>/..." shows the name, XInput flag, counts,
 * detected layout and live values in both modes.
 *
 * <b>Free mode</b> ({@code new GamepadProbeCommand(port)}): press controls one at a time and read the console:
 * <pre>
 *   [GamepadProbe 2] button 3 pressed
 *   [GamepadProbe 2] axis 4 moved to 1.00 (rest 0.00)
 *   [GamepadProbe 2] POV 90
 * </pre>
 *
 * <b>Guided mode</b> ({@code new GamepadProbeCommand(port, true)}): follow the prompt on the console and on
 * SmartDashboard "GamepadProbe/<port>/Prompt" - press (and release) each control it asks for. Set
 * "GamepadProbe/<port>/Skip" to true to skip a control the controller doesn't have. When done, a ready-to-paste
 * GamepadLayout constant is printed to the console; pass it to GamepadLayouts.register().
 */
public class GamepadProbeCommand extends Command {

    private static final double AXIS_REPORT_THRESHOLD = 0.5;
    private static final double STABLE_SECONDS = 0.2;

    private final int port;
    private final boolean guided;
    private final GenericHID hid;
    private final String prefix;

    // free mode
    private String lastName = null;
    private boolean[] lastButtons = new boolean[0];
    private double[] restAxes = new double[0];
    private boolean[] axisReported = new boolean[0];
    private int lastPov = -1;

    // guided mode
    private Wizard wizard;

    public GamepadProbeCommand(int port) {
        this(port, false);
    }

    public GamepadProbeCommand(int port, boolean guided) {
        this.port = port;
        this.guided = guided;
        this.hid = new GenericHID(port);
        this.prefix = "GamepadProbe/" + port + "/";
    }

    @Override
    public boolean runsWhenDisabled() {
        return true;
    }

    @Override
    public void initialize() {
        lastName = null;
        lastPov = -1;
        if (guided) {
            SmartDashboard.putBoolean(prefix + "Skip", false);
            wizard = new Wizard();
            System.out.println("[GamepadProbe " + port + "] guided mode - follow the prompts, release each control "
                + "before the next. Set " + prefix + "Skip to skip a control the controller doesn't have.");
        } else {
            System.out.println("[GamepadProbe " + port + "] started - press each control one at a time");
        }
    }

    @Override
    public void execute() {
        String name = DriverStation.getJoystickName(port);
        boolean xinput = DriverStation.getJoystickIsXbox(port);
        int buttons = DriverStation.getStickButtonCount(port);
        int axes = DriverStation.getStickAxisCount(port);
        int povs = DriverStation.getStickPOVCount(port);

        if (!name.equals(lastName) || lastButtons.length != buttons || restAxes.length != axes) {
            lastName = name;
            lastButtons = new boolean[buttons];
            axisReported = new boolean[axes];
            restAxes = new double[axes];
            for (int a = 0; a < axes; a++) {
                restAxes[a] = hid.getRawAxis(a); // whatever the axes read now is "rest"
            }
            GamepadLayout detected = CommandGamepad.findLayout(name, xinput);
            System.out.printf("[GamepadProbe %d] \"%s\" XInput=%b type=%d buttons=%d axes=%d povs=%d -> %s%n",
                port, name, xinput, DriverStation.getJoystickType(port), buttons, axes, povs, detected);
            if (!guided) {
                StringBuilder rest = new StringBuilder();
                for (int a = 0; a < axes; a++) {
                    rest.append(String.format("%s%d:%.2f", a == 0 ? "" : "  ", a, restAxes[a]));
                }
                System.out.println("[GamepadProbe " + port + "] axes at rest: " + rest);
            }
            SmartDashboard.putString(prefix + "Name", name);
            SmartDashboard.putBoolean(prefix + "XInput", xinput);
            SmartDashboard.putString(prefix + "Counts", buttons + " buttons, " + axes + " axes, " + povs + " POVs");
            SmartDashboard.putString(prefix + "Detected Layout", detected.toString());
            if (guided) {
                wizard.restart();
            }
        }

        boolean[] down = new boolean[buttons];
        StringBuilder pressed = new StringBuilder();
        for (int b = 1; b <= buttons; b++) {
            down[b - 1] = hid.getRawButton(b);
            if (down[b - 1]) {
                pressed.append(pressed.length() == 0 ? "" : " ").append(b);
            }
            if (!guided && down[b - 1] != lastButtons[b - 1]) {
                System.out.println("[GamepadProbe " + port + "] button " + b + (down[b - 1] ? " pressed" : " released"));
            }
            lastButtons[b - 1] = down[b - 1];
        }

        double[] values = new double[axes];
        for (int a = 0; a < axes; a++) {
            values[a] = hid.getRawAxis(a);
            boolean moved = Math.abs(values[a] - restAxes[a]) > AXIS_REPORT_THRESHOLD;
            if (!guided && moved && !axisReported[a]) {
                System.out.printf("[GamepadProbe %d] axis %d moved to %.2f (rest %.2f)%n", port, a, values[a], restAxes[a]);
            }
            axisReported[a] = moved;
        }

        int pov = povs > 0 ? hid.getPOV() : -1;
        if (!guided && pov != lastPov) {
            System.out.println("[GamepadProbe " + port + "] POV " + pov);
        }
        lastPov = pov;

        SmartDashboard.putString(prefix + "Buttons Pressed", pressed.toString());
        SmartDashboard.putNumberArray(prefix + "Axes", values);
        SmartDashboard.putNumber(prefix + "POV", pov);

        if (guided && !name.isEmpty()) {
            wizard.update(name, xinput, down, values, pov);
        }
    }

    @Override
    public boolean isFinished() {
        return guided && wizard != null && wizard.done;
    }

    // ------------------------------------------------------------------ guided mode

    private enum Kind { BUTTON, TRIGGER, STICK_UP, STICK_RIGHT, DPAD }

    private enum Step {
        FACE_UP("face button UP (Xbox Y / PS Triangle)", Kind.BUTTON),
        FACE_DOWN("face button DOWN (Xbox A / PS Cross)", Kind.BUTTON),
        FACE_LEFT("face button LEFT (Xbox X / PS Square)", Kind.BUTTON),
        FACE_RIGHT("face button RIGHT (Xbox B / PS Circle)", Kind.BUTTON),
        BUMPER_LEFT("LEFT bumper (LB / L1)", Kind.BUTTON),
        BUMPER_RIGHT("RIGHT bumper (RB / R1)", Kind.BUTTON),
        TRIGGER_LEFT("LEFT trigger (LT / L2) - press fully, then release", Kind.TRIGGER),
        TRIGGER_RIGHT("RIGHT trigger (RT / R2) - press fully, then release", Kind.TRIGGER),
        START("Start / Options", Kind.BUTTON),
        BACK("Back / View / Share / Create", Kind.BUTTON),
        HOME("Home / PS button (skip if none)", Kind.BUTTON),
        TOUCHPAD("touchpad click (skip if none)", Kind.BUTTON),
        LEFT_CLICK("press the LEFT stick in (LS / L3)", Kind.BUTTON),
        RIGHT_CLICK("press the RIGHT stick in (RS / R3)", Kind.BUTTON),
        LEFT_UP("push the LEFT stick fully UP, then release", Kind.STICK_UP),
        LEFT_RIGHT("push the LEFT stick fully RIGHT, then release", Kind.STICK_RIGHT),
        RIGHT_UP("push the RIGHT stick fully UP, then release", Kind.STICK_UP),
        RIGHT_RIGHT("push the RIGHT stick fully RIGHT, then release", Kind.STICK_RIGHT),
        DPAD_UP("D-pad UP", Kind.DPAD),
        DPAD_RIGHT("D-pad RIGHT", Kind.DPAD),
        DPAD_DOWN("D-pad DOWN", Kind.DPAD),
        DPAD_LEFT("D-pad LEFT", Kind.DPAD);

        final String label;
        final Kind kind;

        Step(String label, Kind kind) {
            this.label = label;
            this.kind = kind;
        }
    }

    /** What one step captured. */
    private static final class Result {
        int button;              // 0 = none
        int axis = -1;           // -1 = none
        double peakDeviation;    // signed, from rest
        double releasedValue;    // axis value after release (trigger rest)
        boolean pov;
        boolean skipped;
    }

    private final class Wizard {
        private final Map<Step, Result> results = new EnumMap<>(Step.class);
        private int stepIndex;
        private boolean done;

        private boolean armed;         // everything released, waiting for the control
        private boolean held;          // control is being pressed
        private double stableSince;
        private double[] lastValues = new double[0];
        private double[] rest = new double[0];
        private int firstButton;
        private int peakAxis;
        private double peakDeviation;
        private boolean sawPov;
        private String name = "";
        private boolean xinput;

        void restart() {
            results.clear();
            stepIndex = 0;
            done = false;
            armed = false;
            held = false;
            stableSince = Timer.getFPGATimestamp();
        }

        Step step() {
            return Step.values()[stepIndex];
        }

        void update(String name, boolean xinput, boolean[] buttons, double[] values, int pov) {
            if (done) {
                return;
            }
            this.name = name.trim();
            this.xinput = xinput;
            double now = Timer.getFPGATimestamp();

            // stability: no axis changed much since last loop
            boolean steady = lastValues.length == values.length;
            for (int a = 0; steady && a < values.length; a++) {
                steady = Math.abs(values[a] - lastValues[a]) < 0.05;
            }
            lastValues = values.clone();
            if (!steady) {
                stableSince = now;
            }
            boolean stable = now - stableSince >= STABLE_SECONDS;
            boolean anyButton = false;
            for (boolean b : buttons) {
                anyButton |= b;
            }

            if (SmartDashboard.getBoolean(prefix + "Skip", false)) {
                SmartDashboard.putBoolean(prefix + "Skip", false);
                Result r = new Result();
                r.skipped = true;
                finishStep(r, "skipped");
                return;
            }

            if (!armed) {
                // wait until everything is released and still, then treat current axes as rest
                if (!anyButton && pov < 0 && stable) {
                    rest = values.clone();
                    armed = true;
                    held = false;
                    prompt("Press: " + step().label);
                }
                return;
            }

            if (!held) {
                int pressedButton = firstPressed(buttons);
                int movedAxis = largestDeviation(values);
                boolean axisMoved = movedAxis >= 0 && Math.abs(values[movedAxis] - rest[movedAxis]) > AXIS_REPORT_THRESHOLD;
                if (pressedButton > 0 || axisMoved || pov >= 0) {
                    held = true;
                    firstButton = pressedButton;
                    peakAxis = axisMoved ? movedAxis : -1;
                    peakDeviation = axisMoved ? values[movedAxis] - rest[movedAxis] : 0;
                    sawPov = pov >= 0;
                }
                return;
            }

            // held: keep the strongest axis movement, first button, any POV
            if (firstButton == 0) {
                firstButton = firstPressed(buttons);
            }
            int a = largestDeviation(values);
            if (a >= 0 && Math.abs(values[a] - rest[a]) > Math.abs(peakDeviation)
                    && Math.abs(values[a] - rest[a]) > AXIS_REPORT_THRESHOLD) {
                peakAxis = a;
                peakDeviation = values[a] - rest[a];
            }
            sawPov |= pov >= 0;

            boolean axisReleased = peakAxis < 0
                || Math.abs(values[peakAxis] - (rest[peakAxis] + peakDeviation)) > AXIS_REPORT_THRESHOLD;
            if (anyButton || pov >= 0 || !axisReleased || !stable) {
                return;
            }

            // released - decide what this step was
            Result r = new Result();
            Kind kind = step().kind;
            String error = null;
            switch (kind) {
                case BUTTON -> {
                    if (firstButton == 0) error = "expected a button";
                    else r.button = firstButton;
                }
                case TRIGGER -> {
                    if (peakAxis >= 0) {
                        r.axis = peakAxis;
                        r.peakDeviation = peakDeviation;
                        r.releasedValue = values[peakAxis];
                    } else if (firstButton > 0) {
                        r.button = firstButton;
                    } else {
                        error = "expected an axis or button";
                    }
                }
                case STICK_UP, STICK_RIGHT -> {
                    if (peakAxis < 0) error = "expected a stick axis";
                    else {
                        r.axis = peakAxis;
                        r.peakDeviation = peakDeviation;
                    }
                }
                case DPAD -> {
                    if (sawPov) r.pov = true;
                    else if (firstButton > 0) r.button = firstButton;
                    else error = "expected the D-pad";
                }
            }
            if (error == null && r.button > 0) {
                for (Map.Entry<Step, Result> e : results.entrySet()) {
                    if (e.getValue().button == r.button && e.getKey().kind != Kind.TRIGGER) {
                        error = "button " + r.button + " is already " + e.getKey();
                    }
                }
            }
            armed = false;
            if (error != null) {
                prompt(error + " - try again");
                return;
            }
            finishStep(r, describe(r));
        }

        private void finishStep(Result r, String what) {
            Step s = step();
            results.put(s, r);
            System.out.println("[GamepadProbe " + port + "]   " + s + ": " + what);
            armed = false;
            stepIndex++;
            // a POV D-pad covers all four directions
            if (s == Step.DPAD_UP && r.pov) {
                stepIndex = Step.values().length;
            }
            if (stepIndex >= Step.values().length) {
                done = true;
                String code = generate();
                System.out.println(code);
                prompt("Done - layout code printed to the console");
            }
        }

        private void prompt(String text) {
            SmartDashboard.putString(prefix + "Prompt", text);
            System.out.println("[GamepadProbe " + port + "] " + text);
        }

        private int firstPressed(boolean[] buttons) {
            for (int b = 0; b < buttons.length; b++) {
                if (buttons[b]) {
                    return b + 1;
                }
            }
            return 0;
        }

        private int largestDeviation(double[] values) {
            int best = -1;
            double bestDev = 0;
            for (int a = 0; a < values.length && a < rest.length; a++) {
                double d = Math.abs(values[a] - rest[a]);
                if (d > bestDev) {
                    bestDev = d;
                    best = a;
                }
            }
            return best;
        }

        private String describe(Result r) {
            if (r.pov) return "POV hat";
            if (r.axis >= 0) return String.format("axis %d (peak %+.2f from rest)", r.axis, r.peakDeviation);
            return "button " + r.button;
        }

        private int button(Step s) {
            Result r = results.get(s);
            return r == null ? 0 : r.button;
        }

        private int axis(Step s) {
            Result r = results.get(s);
            return r == null ? -1 : r.axis;
        }

        private String generate() {
            List<String> warnings = new ArrayList<>();
            String constant = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
            if (constant.isEmpty() || Character.isDigit(constant.charAt(0))) {
                constant = "GAMEPAD_" + constant;
            }
            if (xinput) {
                warnings.add("This controller is XInput - GamepadLayouts.XBOX already covers every XInput controller.");
            }

            for (Step s : new Step[] { Step.LEFT_UP, Step.RIGHT_UP }) {
                Result r = results.get(s);
                if (r != null && r.axis >= 0 && r.peakDeviation > 0) {
                    warnings.add(s + ": axis reads + when pushed UP; CommandGamepad expects - (WPILib). Needs axis inversion.");
                }
            }
            for (Step s : new Step[] { Step.LEFT_RIGHT, Step.RIGHT_RIGHT }) {
                Result r = results.get(s);
                if (r != null && r.axis >= 0 && r.peakDeviation < 0) {
                    warnings.add(s + ": axis reads - when pushed RIGHT; CommandGamepad expects +. Needs axis inversion.");
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n// ===== Generated by GamepadProbeCommand from \"").append(name)
              .append("\" (XInput=").append(xinput).append(") =====\n");
            for (String w : warnings) {
                sb.append("// WARNING: ").append(w).append('\n');
            }
            sb.append("public static final GamepadLayout ").append(constant)
              .append(" = GamepadLayout.builder(\"").append(name).append("\")\n");
            if (xinput) {
                sb.append("    .xinput()\n");
            }
            sb.append("    .names(\"").append(name.replace("\"", "\\\"")).append("\")\n");
            sb.append(String.format("    .face(%d, %d, %d, %d)%n",
                button(Step.FACE_UP), button(Step.FACE_DOWN), button(Step.FACE_LEFT), button(Step.FACE_RIGHT)));
            sb.append(String.format("    .bumpers(%d, %d)%n", button(Step.BUMPER_LEFT), button(Step.BUMPER_RIGHT)));
            sb.append(String.format("    .menu(%d, %d, %d, %d)%n",
                button(Step.START), button(Step.BACK), button(Step.HOME), button(Step.TOUCHPAD)));
            sb.append(String.format("    .stickClicks(%d, %d)%n", button(Step.LEFT_CLICK), button(Step.RIGHT_CLICK)));
            sb.append(String.format("    .leftStick(%d, %d)%n", axis(Step.LEFT_RIGHT), axis(Step.LEFT_UP)));
            sb.append(String.format("    .rightStick(%d, %d)%n", axis(Step.RIGHT_RIGHT), axis(Step.RIGHT_UP)));

            Result lt = results.get(Step.TRIGGER_LEFT);
            Result rt = results.get(Step.TRIGGER_RIGHT);
            boolean ltAxis = lt != null && lt.axis >= 0;
            boolean rtAxis = rt != null && rt.axis >= 0;
            if (ltAxis || rtAxis) {
                if (ltAxis != rtAxis) {
                    sb.append("    // WARNING: one trigger is an axis and the other a button - check these\n");
                }
                Result ref = ltAxis ? lt : rt;
                boolean signed = ref.releasedValue < -AXIS_REPORT_THRESHOLD;
                sb.append(String.format("    .triggerAxes(%d, %d, %b)   // rests at %.2f%n",
                    ltAxis ? lt.axis : -1, rtAxis ? rt.axis : -1, signed, ref.releasedValue));
            } else if ((lt != null && lt.button > 0) || (rt != null && rt.button > 0)) {
                sb.append(String.format("    .triggerButtons(%d, %d)%n", button(Step.TRIGGER_LEFT), button(Step.TRIGGER_RIGHT)));
            }

            Result dUp = results.get(Step.DPAD_UP);
            if (dUp != null && !dUp.pov && !dUp.skipped) {
                sb.append(String.format("    .dpadButtons(%d, %d, %d, %d)%n",
                    button(Step.DPAD_UP), button(Step.DPAD_RIGHT), button(Step.DPAD_DOWN), button(Step.DPAD_LEFT)));
            }
            sb.append("    .build();\n");
            sb.append("// Register it in robot code before the gamepads are read: GamepadLayouts.register(...);\n");
            return sb.toString();
        }
    }
}
