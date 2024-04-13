/*
 * MIT License
 *
 * Copyright (c) 2022 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package elf4j.jul;

import static org.junit.jupiter.api.Assertions.*;

import elf4j.Level;
import elf4j.Logger;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JulLoggerTest {

    @Nested
    class isEnabled {

        @Test
        void optToSupplyDefaultLevelAsInfo() {
            assertTrue(Logger.instance().isEnabled());
            assertFalse(Logger.instance().atDebug().isEnabled());
            assertTrue(Logger.instance().atWarn().isEnabled());
        }
    }

    @Nested
    class name {
        @Test
        void loggerNameForNullOrNoargInstanceCaller() {
            String thisClassName = this.getClass().getName();
            assertEquals(thisClassName, ((JulLogger) Logger.instance()).getName());
        }
    }

    @Nested
    class readmeSamples {
        private final Logger logger = Logger.instance();

        @Test
        void messagesArgsAndGuards() {
            logger.atWarn().log("message with arguments - arg1 {}, arg2 {}, arg3 {}", "a11111", "a22222", "a33333");
            logger.atInfo().log("info message");
            Logger debug = logger.atDebug();
            assertNotSame(logger, debug);
            assertEquals(((JulLogger) logger).getName(), ((JulLogger) debug).getName());
            assertEquals(Level.DEBUG, debug.getLevel());
            if (debug.isEnabled()) {
                debug.log(
                        "a {} guarded by a {}, so {} is created {} DEBUG level is {}",
                        "long message",
                        "level check",
                        "no message object",
                        "unless",
                        "enabled by the configuration of the logging provider");
            }
            debug.log(
                    () ->
                            "alternative to the level guard, using a supplier function should achieve the same goal, pending quality of the logging provider");
        }

        @Test
        void throwableAndMessageAndArgs() {
            logger.atInfo().log("let's see immutability in action...");
            Logger error = logger.atError();
            error.log("this is an immutable logger instance whose level is Level.ERROR");
            Throwable ex = new Exception("ex message");
            error.log(ex, "level set omitted but we know the level is Level.ERROR");
            error.atWarn()
                    .log(
                            ex,
                            "the log level switched to WARN on the fly. that is, {} returns a {} and {} Logger {}",
                            "atWarn()",
                            "different",
                            "immutable",
                            "instance");
            error.atError()
                    .log(
                            ex,
                            "here the {} call is {} because the {} instance is {}, and the instance's log level has and will always be Level.ERROR",
                            "atError()",
                            "unnecessary",
                            "error logger",
                            "immutable");
            error.log(
                    ex,
                    "now at Level.ERROR, together with the exception stack trace, logging some items expensive to compute: item1 {}, item2 {}, item3 {}, item4 {}, ...",
                    () -> "i11111",
                    () -> "i22222",
                    () -> "i33333",
                    () -> Arrays.stream(new Object[] {"i44444"}).collect(Collectors.toList()));
        }
    }
}
