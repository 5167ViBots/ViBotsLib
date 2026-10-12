package com.vibots5167.lib.controls;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadLayoutTest {

    @Test
    void playStationMatchesByNameNotXInput() {
        assertTrue(GamepadLayouts.PLAYSTATION.matches("DualSense Wireless Controller", false));
        assertFalse(GamepadLayouts.PLAYSTATION.matches("DualSense Wireless Controller", true));
        assertFalse(GamepadLayouts.PLAYSTATION.matches("Logitech Dual Action", false));
    }

    @Test
    void xboxMatchesAnyXInput() {
        assertTrue(GamepadLayouts.XBOX.matches("Controller (Xbox One For Windows)", true));
        assertTrue(GamepadLayouts.XBOX.matches("Whatever", true));
        assertFalse(GamepadLayouts.XBOX.matches("Whatever", false));
    }

    @Test
    void registeredLayoutTakesPriority() {
        GamepadLayout custom = GamepadLayout.builder("Custom DualSense").names("DualSense Edge").build();
        GamepadLayouts.register(custom);
        try {
            assertSame(custom, CommandGamepad.findLayout("DualSense Edge Wireless Controller", false));
            assertSame(GamepadLayouts.PLAYSTATION, CommandGamepad.findLayout("DualSense Wireless Controller", false));
            assertSame(GamepadLayouts.UNMAPPED, CommandGamepad.findLayout("Unknown Stick", false));
        } finally {
            GamepadLayouts.REGISTRY.remove(custom);
        }
    }
}
