package com.vibots5167.lib.controls;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj2.command.button.Trigger;

/**
 * One interface for any gamepad, named by position instead of by brand.
 *
 * <pre>
 *   CommandGamepad driver = new CommandGamepad(0);                              // auto-detect by DS name
 *   CommandGamepad pinned = new CommandGamepad(1, GamepadLayouts.PLAYSTATION);  // fixed layout
 *   driver.buttons.down.onTrue(...);        // Xbox A, PS Cross
 *   driver.dpad.up.whileTrue(...);
 *   driver.bumpers.left.onTrue(...);         // LB / L1
 *   driver.leftTrigger.pressed.whileTrue(); // LT / L2 past halfway
 *   double fwd = -driver.leftStick.y();     // WPILib convention: +Y is stick pulled DOWN
 * </pre>
 *
 * Buttons are command-based {@link Trigger}s; axes are read with methods. Analog triggers read
 * 0 (released) .. 1 (fully pressed) on every layout.
 *
 * Auto-detect picks the first layout in {@link GamepadLayouts#REGISTRY} that matches the Driver Station's
 * name / XInput flag for the port. The choice is cached and only redone when the name changes. A connected
 * controller that no layout matches reads as {@link GamepadLayouts#UNMAPPED} (all false / 0) and logs one
 * DS warning with its name - use GamepadProbeCommand to add a layout for it.
 */
public class CommandGamepad {

    private static final double TRIGGER_PRESSED_THRESHOLD = 0.5;

    private final int port;
    private final GamepadLayout fixedLayout; // null = auto-detect
    private final GenericHID hid;

    private String cachedName;
    private boolean cachedXInput;
    private GamepadLayout cachedLayout = GamepadLayouts.UNMAPPED;

    /** Directional pad. Cardinal directions include the neighboring diagonals (up = up-left, up, up-right). */
    public final DPad dpad = new DPad();
    /** Face buttons by position: up = Y / Triangle, down = A / Cross, left = X / Square, right = B / Circle. */
    public final Buttons buttons = new Buttons();
    /** LB / L1 and RB / R1. */
    public final Bumpers bumpers = new Bumpers();
    /** Start / Options, Back / Share-Create, Home (PS button; not readable on Xbox), Touchpad (PS only). */
    public final Menu menu = new Menu();
    public final Stick leftStick = new Stick(true);
    public final Stick rightStick = new Stick(false);
    /** LT / L2 and RT / R2. */
    public final TriggerAxis leftTrigger = new TriggerAxis(true);
    public final TriggerAxis rightTrigger = new TriggerAxis(false);

    /** Auto-detect the layout from the Driver Station's name for this port. */
    public CommandGamepad(int port) {
        this(port, null);
    }

    /** Always use this layout, whatever the Driver Station reports. */
    public CommandGamepad(int port, GamepadLayout layout) {
        this.port = port;
        this.fixedLayout = layout;
        this.hid = new GenericHID(port);
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return DriverStation.isJoystickConnected(port);
    }

    /** Layout currently in use (UNMAPPED when nothing usable is connected). */
    public GamepadLayout getLayout() {
        return layout();
    }

    /** Rumble both sides, 0..1. */
    public void rumble(double strength) {
        hid.setRumble(GenericHID.RumbleType.kBothRumble, strength);
    }

    /** Underlying WPILib HID, for anything this class doesn't cover. */
    public GenericHID getHID() {
        return hid;
    }

    /** First registry layout matching this DS name / XInput flag, or UNMAPPED. */
    public static GamepadLayout findLayout(String dsName, boolean isXInput) {
        if (dsName == null || dsName.isEmpty()) {
            return GamepadLayouts.UNMAPPED;
        }
        for (GamepadLayout layout : GamepadLayouts.REGISTRY) {
            if (layout.matches(dsName, isXInput)) {
                return layout;
            }
        }
        return GamepadLayouts.UNMAPPED;
    }

    private GamepadLayout layout() {
        if (fixedLayout != null) {
            return fixedLayout;
        }
        String name = DriverStation.getJoystickName(port);
        boolean xinput = DriverStation.getJoystickIsXbox(port);
        if (!name.equals(cachedName) || xinput != cachedXInput) {
            cachedName = name;
            cachedXInput = xinput;
            cachedLayout = findLayout(name, xinput);
            if (cachedLayout == GamepadLayouts.UNMAPPED && !name.isEmpty()) {
                DriverStation.reportWarning("CommandGamepad port " + port + ": no layout for \"" + name
                    + "\" (XInput=" + xinput + ") - controls disabled. Add one with GamepadLayouts.register().", false);
            } else if (!name.isEmpty()) {
                System.out.println("[CommandGamepad] port " + port + ": \"" + name + "\" -> " + cachedLayout);
            }
        }
        return cachedLayout;
    }

    // Bounds-checked reads: asking the DS for a button/axis the controller doesn't have spams warnings.

    private boolean rawButton(int number) {
        return number > 0 && number <= DriverStation.getStickButtonCount(port) && hid.getRawButton(number);
    }

    private double rawAxis(int axis) {
        return axis >= 0 && axis < DriverStation.getStickAxisCount(port) ? hid.getRawAxis(axis) : 0.0;
    }

    private int pov() {
        GamepadLayout l = layout();
        if (l.dpadSource == GamepadLayout.DPadSource.POV) {
            return DriverStation.getStickPOVCount(port) > 0 ? hid.getPOV() : -1;
        }
        int dx = (rawButton(l.dpadRight) ? 1 : 0) - (rawButton(l.dpadLeft) ? 1 : 0);
        int dy = (rawButton(l.dpadUp) ? 1 : 0) - (rawButton(l.dpadDown) ? 1 : 0);
        if (dx == 0 && dy == 0) {
            return -1;
        }
        return (int) Math.round((Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360); // clockwise from up
    }

    private boolean povWithin(int center) {
        int pov = pov();
        if (pov < 0) {
            return false;
        }
        int diff = Math.abs(((pov - center) % 360 + 540) % 360 - 180);
        return diff <= 45;
    }

    /** Trigger for a button chosen from the active layout (re-read every loop, so auto-detect can switch). */
    private Trigger button(java.util.function.ToIntFunction<GamepadLayout> number) {
        return new Trigger(() -> rawButton(number.applyAsInt(layout())));
    }

    // ------------------------------------------------------------------ groups

    public class DPad {
        public final Trigger up = new Trigger(() -> povWithin(0));
        public final Trigger right = new Trigger(() -> povWithin(90));
        public final Trigger down = new Trigger(() -> povWithin(180));
        public final Trigger left = new Trigger(() -> povWithin(270));
        /** Any direction pressed. */
        public final Trigger any = new Trigger(() -> pov() >= 0);

        /** Angle in degrees clockwise from up (0, 45, ... 315), or -1 when released. */
        public int angle() {
            return pov();
        }
    }

    public class Buttons {
        public final Trigger up = button(l -> l.faceUp);
        public final Trigger down = button(l -> l.faceDown);
        public final Trigger left = button(l -> l.faceLeft);
        public final Trigger right = button(l -> l.faceRight);
    }

    public class Bumpers {
        public final Trigger left = button(l -> l.bumperLeft);
        public final Trigger right = button(l -> l.bumperRight);
    }

    public class Menu {
        /** Xbox Start, PS Options. */
        public final Trigger start = button(l -> l.start);
        /** Xbox Back (View), PS4 Share, PS5 Create. */
        public final Trigger back = button(l -> l.back);
        /** PS button. Never true on Xbox (the Guide button isn't passed through by the Driver Station). */
        public final Trigger home = button(l -> l.home);
        /** PS touchpad click. Never true on Xbox. */
        public final Trigger touchpad = button(l -> l.touchpad);
    }

    public class Stick {
        private final boolean isLeft;
        /** Pressing the stick in (LS / L3, RS / R3). */
        public final Trigger click;

        private Stick(boolean isLeft) {
            this.isLeft = isLeft;
            this.click = button(l -> isLeft ? l.leftStickClick : l.rightStickClick);
        }

        /** -1 (left) .. 1 (right). */
        public double x() {
            GamepadLayout l = layout();
            return rawAxis(isLeft ? l.leftX : l.rightX);
        }

        /** -1 (pushed up) .. 1 (pulled down) - WPILib convention, negate for "forward". */
        public double y() {
            GamepadLayout l = layout();
            return rawAxis(isLeft ? l.leftY : l.rightY);
        }
    }

    public class TriggerAxis {
        private final boolean isLeft;
        /** Pressed past halfway. */
        public final Trigger pressed;

        private TriggerAxis(boolean isLeft) {
            this.isLeft = isLeft;
            this.pressed = new Trigger(() -> value() > TRIGGER_PRESSED_THRESHOLD);
        }

        /** 0 (released) .. 1 (fully pressed) on every layout; digital-only triggers read 0 or 1. */
        public double value() {
            GamepadLayout l = layout();
            int n = isLeft ? l.leftTrigger : l.rightTrigger;
            return switch (l.triggerKind) {
                case SIGNED_AXIS -> (rawAxis(n) + 1.0) / 2.0;
                case UNSIGNED_AXIS -> rawAxis(n);
                case BUTTON -> rawButton(n) ? 1.0 : 0.0;
            };
        }

        /** Trigger for a custom threshold, 0..1. */
        public Trigger pressedPast(double threshold) {
            return new Trigger(() -> value() > threshold);
        }
    }
}
