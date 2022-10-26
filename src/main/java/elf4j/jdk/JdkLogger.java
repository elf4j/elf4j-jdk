/*
 * MIT License
 *
 * Copyright (c) 2022 Easy Logging Facade for Java (ELF4J)
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
 */

package elf4j.jdk;

import elf4j.Level;
import elf4j.Logger;
import elf4j.util.NoopLogger;
import lombok.NonNull;
import lombok.ToString;
import net.jcip.annotations.Immutable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.LogRecord;

import static elf4j.Level.*;

@Immutable
@ToString
class JdkLogger implements Logger {
    private static final char CLOSE_BRACE = '}';
    private static final Level DEFAULT_LEVEL = INFO;
    private static final String EMPTY_MESSAGE = "";
    private static final String INSTANCE = "instance";
    private static final EnumMap<Level, java.util.logging.Level> LEVEL_MAP = setLevelMap();
    private static final String LOG = "log";
    private static final EnumMap<Level, Map<String, JdkLogger>> LOGGER_CACHE = initLoggerCache();
    private static final char OPEN_BRACE = '{';
    @NonNull private final String name;
    @NonNull private final Level level;
    @NonNull private final java.util.logging.Logger nativeLogger;

    private JdkLogger(@NonNull String name, @NonNull Level level) {
        this.name = name;
        this.level = level;
        this.nativeLogger = java.util.logging.Logger.getLogger(name);
    }

    static JdkLogger instance() {
        return getLogger(CallStack.mostRecentCallerOf(Logger.class, INSTANCE).getClassName());
    }

    static JdkLogger instance(String name) {
        return getLogger(name == null ? CallStack.mostRecentCallerOf(Logger.class, INSTANCE).getClassName() : name);
    }

    static JdkLogger instance(Class<?> clazz) {
        return getLogger(
                clazz == null ? CallStack.mostRecentCallerOf(Logger.class, INSTANCE).getClassName() : clazz.getName());
    }

    private static JdkLogger getLogger(String name) {
        return getLogger(name, DEFAULT_LEVEL);
    }

    private static JdkLogger getLogger(@NonNull String name, @NonNull Level level) {
        return LOGGER_CACHE.get(level).computeIfAbsent(name, k -> new JdkLogger(k, level));
    }

    private static EnumMap<Level, Map<String, JdkLogger>> initLoggerCache() {
        EnumMap<Level, Map<String, JdkLogger>> loggerCache = new EnumMap<>(Level.class);
        EnumSet.allOf(Level.class).forEach(level -> loggerCache.put(level, new ConcurrentHashMap<>()));
        return loggerCache;
    }

    private static String replaceWithJdkPlaceholders(String message) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = message.toCharArray();
        int placeholderIndex = 0;
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            stringBuilder.append(current);
            char next = ((i + 1) == chars.length) ? Character.MIN_VALUE : chars[i + 1];
            if (current == OPEN_BRACE && next == CLOSE_BRACE) {
                stringBuilder.append(placeholderIndex++);
            }
        }
        return stringBuilder.toString();
    }

    private static EnumMap<Level, java.util.logging.Level> setLevelMap() {
        EnumMap<Level, java.util.logging.Level> levelMap = new EnumMap<>(Level.class);
        levelMap.put(TRACE, java.util.logging.Level.FINEST);
        levelMap.put(DEBUG, java.util.logging.Level.FINE);
        levelMap.put(INFO, java.util.logging.Level.INFO);
        levelMap.put(WARN, java.util.logging.Level.WARNING);
        levelMap.put(ERROR, java.util.logging.Level.SEVERE);
        return levelMap;
    }

    @Override
    public @NonNull String getName() {
        return this.name;
    }

    @Override
    public @NonNull Level getLevel() {
        return this.level;
    }

    @Override
    public Logger atTrace() {
        return atLevel(TRACE);
    }

    @Override
    public Logger atDebug() {
        return atLevel(DEBUG);
    }

    @Override
    public Logger atInfo() {
        return atLevel(INFO);
    }

    @Override
    public Logger atWarn() {
        return atLevel(WARN);
    }

    @Override
    public Logger atError() {
        return atLevel(ERROR);
    }

    @Override
    public void log(Object message) {
        if (isLevelDisabled()) {
            return;
        }
        nativeLogger.log(new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(message)));
    }

    @Override
    public void log(Supplier<?> message) {
        if (isLevelDisabled()) {
            return;
        }
        nativeLogger.log(new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(message.get())));
    }

    @Override
    public void log(String message, Object... args) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJdkPlaceholders(message));
        extendedLogRecord.setParameters(args);
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(String message, Supplier<?>... args) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJdkPlaceholders(message));
        extendedLogRecord.setParameters(Arrays.stream(args).map(Supplier::get).toArray(Object[]::new));
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord = new ExtendedLogRecord(LEVEL_MAP.get(this.level), EMPTY_MESSAGE);
        extendedLogRecord.setThrown(t);
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, Object message) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(message));
        extendedLogRecord.setThrown(t);
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, Supplier<?> message) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(message.get()));
        extendedLogRecord.setThrown(t);
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, String message, Object... args) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJdkPlaceholders(message));
        extendedLogRecord.setParameters(args);
        extendedLogRecord.setThrown(t);
        nativeLogger.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, String message, Supplier<?>... args) {
        if (isLevelDisabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJdkPlaceholders(message));
        extendedLogRecord.setParameters(Arrays.stream(args).map(Supplier::get).toArray(Object[]::new));
        extendedLogRecord.setThrown(t);
        nativeLogger.log(extendedLogRecord);
    }

    private Logger atLevel(Level level) {
        if (this.level == level) {
            return this;
        }
        return level == OFF ? NoopLogger.INSTANCE : getLogger(this.name, level);
    }

    private boolean isLevelDisabled() {
        return !nativeLogger.isLoggable(LEVEL_MAP.get(this.level));
    }

    private static class CallStack {

        static StackTraceElement mostRecentCallerOf(@NonNull Class<?> clazz, @NonNull String methodName) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < stackTrace.length; i++) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (clazz.getName().equals(stackTraceElement.getClassName())
                        && methodName.equals(stackTraceElement.getMethodName())) {
                    return stackTrace[i + 1];
                }
            }
            throw new IllegalArgumentException(
                    "unable to locate caller of " + clazz.getName() + "#" + methodName + " in calling stack "
                            + Arrays.toString(stackTrace));
        }
    }

    private static class ExtendedLogRecord extends LogRecord {
        private boolean needToInferCaller;
        private String callerClassName;
        private String callerMethodName;

        public ExtendedLogRecord(java.util.logging.Level level, String msg) {
            super(level, msg);
            needToInferCaller = true;
        }

        @Override
        public String getSourceClassName() {
            if (needToInferCaller) {
                interCaller();
            }
            return callerClassName;
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            callerClassName = sourceClassName;
            needToInferCaller = false;
        }

        @Override
        public String getSourceMethodName() {
            if (needToInferCaller) {
                interCaller();
            }
            return callerMethodName;
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            callerMethodName = sourceMethodName;
            needToInferCaller = false;
        }

        private void interCaller() {
            needToInferCaller = false;
            StackTraceElement caller = CallStack.mostRecentCallerOf(JdkLogger.class, LOG);
            setSourceClassName(caller.getClassName());
            setSourceMethodName(caller.getMethodName());
        }
    }
}