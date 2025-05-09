/*
 * Copyright (C) 2025 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MegaMek was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package megamek.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

public class MMLoggerTest {

    private CustomLogger mockLogger;
    private MMLogger testMMLogger;
    private final static String LOGGER_NAME = "TestLogger";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockLogger = spy(new CustomLogger(LOGGER_NAME));
        testMMLogger = new MMLogger(mockLogger);
    }

    @Test
    public void testDebugLogging() {
        testMMLogger.debug("Debug message: {}", "test");
        verifyLog(Level.DEBUG, "Debug message: test");
    }

    @Test
    public void testInfoLogging() {
        testMMLogger.info("Info message: {}", "test");
        verifyLog(Level.INFO, "Info message: test");
    }

    @Test
    public void testWarnLogging() {
        testMMLogger.warn("Warn message: {}", "test");
        verifyLog(Level.WARN, "Warn message: test");
    }

    @Test
    public void testWarnLoggingWithException() {
        Exception e = new Exception("Test exception");
        testMMLogger.warn(e, "Warn message w/ Exception: {}", "test");
        verifyLog(Level.WARN, "Warn message w/ Exception: test", e);
    }

    @Test
    public void testWarnLoggingWithExceptionWithStringFormat() {
        Exception e = new Exception("Test exception");
        testMMLogger.warn(e, "Warn message: %s", "test");
        verifyLog(Level.WARN, "Warn message: test", e);
    }

    @Test
    public void testDebugLoggingWithException() {
        Exception e = new Exception("Test exception");
        testMMLogger.debug(e, "Debug message: %s", "test");
        verifyLog(Level.DEBUG, "Debug message: test", e);
    }

    @EnabledIfEnvironmentVariable(named = "GUITests", matches = "true")
    @Test
    public void testErrorLogging() {
        if (GraphicsEnvironment.isHeadless()) {
            // Skip this test if running in headless mode
            return;
        }
        automaticallyDismissDialog();
        testMMLogger.errorDialog("test", "Error message: {}", "test");
        verifyLog(Level.ERROR, "Error message: test");
    }

    @EnabledIfEnvironmentVariable(named = "GUITests", matches = "true")
    @Test
    public void testErrorLoggingWithStringFormat() {
        if (GraphicsEnvironment.isHeadless()) {
            // Skip this test if running in headless mode
            return;
        }
        automaticallyDismissDialog();
        testMMLogger.errorDialog("test", "Error message: %s", "test");
        verifyLog(Level.ERROR, "Error message: test");
    }

    @EnabledIfEnvironmentVariable(named = "GUITests", matches = "true")
    @Test
    public void testErrorLoggingWithException() {
        if (GraphicsEnvironment.isHeadless()) {
            // Skip this test if running in headless mode
            return;
        }
        automaticallyDismissDialog();
        Exception e = new Exception("Test exception");
        testMMLogger.errorDialog(e, "Error message: {}", "test", "test");
        verifyLog(Level.ERROR, "Error message: test", e);
    }

    @Test
    public void testFatalLogging() {
        testMMLogger.fatal("Fatal without dialog without exception");
        verifyLog(Level.FATAL, "Fatal without dialog without exception");
    }

    @EnabledIfEnvironmentVariable(named = "GUITests", matches = "true")
    @Test
    public void testFatalLoggingWithDialog() {
        if (GraphicsEnvironment.isHeadless()) {
            // Skip this test if running in headless mode
            return;
        }
        automaticallyDismissDialog();
        testMMLogger.fatalDialog("Fatal with dialog with exception", "Fatal dialog title");
        verifyLog(Level.FATAL, "Fatal with dialog with exception");
    }

    @Test
    public void testFatalLoggingWithException() {
        Exception e = new Exception("Test exception");
        testMMLogger.fatal(e, "Fatal without dialog with exception");
        verifyLog(Level.FATAL, "Fatal without dialog with exception", e);
    }

    private void verifyLog(Level level, String message) {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockLogger).logMessage(anyString(),
              eq(level),
              nullable(Marker.class),
              messageCaptor.capture(),
              nullable(Throwable.class));
        assertEquals(message, messageCaptor.getValue().getFormattedMessage());
    }

    private void verifyLog(Level level, String message, Throwable e) {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(mockLogger).logMessage(anyString(),
              eq(level),
              nullable(Marker.class),
              messageCaptor.capture(),
              throwableCaptor.capture());
        assertEquals(message, messageCaptor.getValue().getFormattedMessage());
        assertEquals(e, throwableCaptor.getValue());
    }

    // Simulate pressing the Enter key, necessary to dismiss the dialogs created by the logger on some types of logs
    private static void automaticallyDismissDialog() {
        try {
            Robot robot = new Robot();
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    robot.keyPress(KeyEvent.VK_ENTER);
                    Thread.sleep(100);
                    robot.keyRelease(KeyEvent.VK_ENTER);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Custom logger implementation for testing
    private static class CustomLogger extends AbstractLogger {

        protected CustomLogger(String name) {
            super(name, null);
        }

        @Override
        public void logMessage(String fqcn, Level level, org.apache.logging.log4j.Marker marker, String message,
                               Throwable t) {
            // Custom implementation for logging messages
        }

        @Override
        public boolean isEnabled(Level level, org.apache.logging.log4j.Marker marker, String message) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, org.apache.logging.log4j.Marker marker, String message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, org.apache.logging.log4j.Marker marker, String message,
                                 Object... params) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2,
                                 Object p3) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4, Object p5) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4, Object p5, Object p6) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4, Object p5, Object p6, Object p7) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4, Object p5, Object p6, Object p7, Object p8) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3,
                                 Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
            return true;
        }

        @Override
        public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable t) {

        }

        @Override
        public boolean isEnabled(Level level, org.apache.logging.log4j.Marker marker, Object message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, org.apache.logging.log4j.Marker marker,
                                 org.apache.logging.log4j.message.Message message, Throwable t) {
            return true;
        }

        @Override
        public boolean isEnabled(Level level, Marker marker, CharSequence message, Throwable t) {
            return true;
        }

        @Override
        public Level getLevel() {
            return Level.ALL;
        }
    }
}

