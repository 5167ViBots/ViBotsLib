package com.vibots5167.lib.controls;

import java.util.List;
import java.util.Locale;

/**
 * Raw button / axis numbers for one controller family, plus the Driver Station names it matches.
 *
 * Build with {@link #builder(String)}. Button numbers are 1-based as the DS reports them; 0 means
 * "not present" (always reads false). Axis numbers are 0-based; -1 means "not present" (always reads 0).
 * Use the GamepadProbeCommand to find a new controller's name and numbers.
 *
 * Matching (see {@link #matches}): the DS reports whether a controller is XInput ("Xbox"). XInput is
 * standardized, so every XInput controller uses an XInput layout. Non-XInput layouts are matched by name.
 */
public final class GamepadLayout {

    public enum TriggerKind {
        /** Analog, -1 released .. 1 pressed (PlayStation). */
        SIGNED_AXIS,
        /** Analog, 0 released .. 1 pressed (Xbox). */
        UNSIGNED_AXIS,
        /** Digital only - reads 0 or 1 (e.g. Logitech F310 in D mode, Switch Pro). */
        BUTTON
    }

    public enum DPadSource {
        /** POV hat 0 (most controllers). */
        POV,
        /** Four separate buttons. */
        BUTTONS
    }

    public final String name;
    final boolean xinput;
    final List<String> namePatterns; // lower case

    final int faceUp, faceDown, faceLeft, faceRight;
    final int bumperLeft, bumperRight;
    final int start, back, home, touchpad;
    final int leftStickClick, rightStickClick;
    final int leftX, leftY, rightX, rightY;
    final TriggerKind triggerKind;
    final int leftTrigger, rightTrigger; // axis numbers, or button numbers for TriggerKind.BUTTON
    final DPadSource dpadSource;
    final int dpadUp, dpadRight, dpadDown, dpadLeft; // only for DPadSource.BUTTONS

    private GamepadLayout(Builder b) {
        name = b.name;
        xinput = b.xinput;
        namePatterns = b.namePatterns.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        faceUp = b.faceUp; faceDown = b.faceDown; faceLeft = b.faceLeft; faceRight = b.faceRight;
        bumperLeft = b.bumperLeft; bumperRight = b.bumperRight;
        start = b.start; back = b.back; home = b.home; touchpad = b.touchpad;
        leftStickClick = b.leftStickClick; rightStickClick = b.rightStickClick;
        leftX = b.leftX; leftY = b.leftY; rightX = b.rightX; rightY = b.rightY;
        triggerKind = b.triggerKind; leftTrigger = b.leftTrigger; rightTrigger = b.rightTrigger;
        dpadSource = b.dpadSource;
        dpadUp = b.dpadUp; dpadRight = b.dpadRight; dpadDown = b.dpadDown; dpadLeft = b.dpadLeft;
    }

    /**
     * True if this layout fits a controller with the given DS name and XInput flag.
     * XInput layouts with no name patterns match every XInput controller.
     */
    public boolean matches(String dsName, boolean isXInput) {
        if (isXInput != xinput) {
            return false;
        }
        if (namePatterns.isEmpty()) {
            return xinput;
        }
        String lower = dsName.toLowerCase(Locale.ROOT);
        return namePatterns.stream().anyMatch(lower::contains);
    }

    @Override
    public String toString() {
        return name;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private boolean xinput;
        private List<String> namePatterns = List.of();
        private int faceUp, faceDown, faceLeft, faceRight;
        private int bumperLeft, bumperRight;
        private int start, back, home, touchpad;
        private int leftStickClick, rightStickClick;
        private int leftX = -1, leftY = -1, rightX = -1, rightY = -1;
        private TriggerKind triggerKind = TriggerKind.UNSIGNED_AXIS;
        private int leftTrigger = -1, rightTrigger = -1;
        private DPadSource dpadSource = DPadSource.POV;
        private int dpadUp, dpadRight, dpadDown, dpadLeft;

        private Builder(String name) {
            this.name = name;
        }

        /** Controller is read through XInput (the DS reports it as Xbox). */
        public Builder xinput() {
            xinput = true;
            return this;
        }

        /** Case-insensitive substrings of the DS joystick name, e.g. "DualSense". */
        public Builder names(String... patterns) {
            namePatterns = List.of(patterns);
            return this;
        }

        /** Face buttons by position (Xbox Y/A/X/B, PlayStation Triangle/Cross/Square/Circle). */
        public Builder face(int up, int down, int left, int right) {
            faceUp = up; faceDown = down; faceLeft = left; faceRight = right;
            return this;
        }

        public Builder bumpers(int left, int right) {
            bumperLeft = left; bumperRight = right;
            return this;
        }

        /** Start/Options, Back/Share-Create, Home (PS button), touchpad click. 0 = not present. */
        public Builder menu(int start, int back, int home, int touchpad) {
            this.start = start; this.back = back; this.home = home; this.touchpad = touchpad;
            return this;
        }

        public Builder stickClicks(int left, int right) {
            leftStickClick = left; rightStickClick = right;
            return this;
        }

        public Builder leftStick(int xAxis, int yAxis) {
            leftX = xAxis; leftY = yAxis;
            return this;
        }

        public Builder rightStick(int xAxis, int yAxis) {
            rightX = xAxis; rightY = yAxis;
            return this;
        }

        /** Analog triggers on these axes. */
        public Builder triggerAxes(int left, int right, boolean signed) {
            triggerKind = signed ? TriggerKind.SIGNED_AXIS : TriggerKind.UNSIGNED_AXIS;
            leftTrigger = left; rightTrigger = right;
            return this;
        }

        /** Digital-only triggers on these buttons. */
        public Builder triggerButtons(int left, int right) {
            triggerKind = TriggerKind.BUTTON;
            leftTrigger = left; rightTrigger = right;
            return this;
        }

        /** D-pad reported as four buttons instead of a POV hat. */
        public Builder dpadButtons(int up, int right, int down, int left) {
            dpadSource = DPadSource.BUTTONS;
            dpadUp = up; dpadRight = right; dpadDown = down; dpadLeft = left;
            return this;
        }

        public GamepadLayout build() {
            return new GamepadLayout(this);
        }
    }
}
