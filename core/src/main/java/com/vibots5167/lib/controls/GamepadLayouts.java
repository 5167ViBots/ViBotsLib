package com.vibots5167.lib.controls;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Known controller layouts, and the registry CommandGamepad's auto-detect searches.
 *
 * To add a controller: run GamepadProbeCommand on its port, press every control, write a
 * GamepadLayout with its DS name pattern(s), and {@link #register} it in robot code before the gamepads are read.
 */
public final class GamepadLayouts {

    private GamepadLayouts() {}

    /** Every XInput controller: Xbox 360/One/Series, and third-party pads in XInput ("X") mode. */
    public static final GamepadLayout XBOX = GamepadLayout.builder("Xbox (XInput)")
        .xinput()                                   // no name patterns: matches any XInput controller
        .face(4, 1, 3, 2)                           // Y, A, X, B
        .bumpers(5, 6)
        .menu(8, 7, 0, 0)                           // Start, Back; Guide isn't passed through by the DS
        .stickClicks(9, 10)
        .leftStick(0, 1)
        .rightStick(4, 5)
        .triggerAxes(2, 3, false)
        .build();

    /** Sony DualShock 4, DualSense, DualSense Edge (all share one layout). */
    public static final GamepadLayout PLAYSTATION = GamepadLayout.builder("PlayStation")
        .names("Wireless Controller", "DualSense", "DualShock")
        .face(4, 2, 1, 3)                           // Triangle, Cross, Square, Circle
        .bumpers(5, 6)
        .menu(10, 9, 13, 14)                        // Options, Share/Create, PS, touchpad
        .stickClicks(11, 12)
        .leftStick(0, 1)
        .rightStick(2, 5)
        .triggerAxes(3, 4, true)
        .build();

    /** Nothing connected, or a controller no layout matches: every control reads false / 0. */
    public static final GamepadLayout UNMAPPED = GamepadLayout.builder("Unmapped").build();

    /** Auto-detect search order - first match wins, so more specific layouts come first. */
    public static final List<GamepadLayout> REGISTRY = new CopyOnWriteArrayList<>(List.of(
        PLAYSTATION,
        XBOX));

    /**
     * Add a layout to auto-detect, ahead of the built-in ones (so it can override them for a specific
     * controller name). Call before the controller is first read, e.g. in the RobotContainer constructor.
     */
    public static void register(GamepadLayout layout) {
        REGISTRY.add(0, layout);
    }
}
