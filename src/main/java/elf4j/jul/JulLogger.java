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

import static elf4j.Level.*;

import elf4j.Level;
import elf4j.Logger;
import elf4j.util.NoopLogger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.LogRecord;
import javax.annotation.concurrent.Immutable;
import lombok.NonNull;
import lombok.ToString;

@Immutable
@ToString
class JulLogger implements Logger {
    private static final Level DEFAULT_LEVEL = INFO;
    private static final Class<JulLogger> SERVICE_INTERFACE_CLASS = JulLogger.class;
    private static final Class<Logger> SERVICE_ACCESS_CLASS = Logger.class;
    private static final EnumMap<Level, java.util.logging.Level> LEVEL_MAP = setLevelMap();
    private static final EnumMap<Level, Map<String, JulLogger>> LOGGER_CACHE = initLoggerCache();

    @NonNull private final Level level;

    @NonNull private final String name;

    @NonNull private final java.util.logging.Logger delegate;

    private JulLogger(@NonNull String name, @NonNull Level level) {
        this.name = name;
        this.level = level;
        this.delegate = java.util.logging.Logger.getLogger(name);
    }

    static JulLogger instance() {
        return getLogger(CallStack.directCallerOf(SERVICE_ACCESS_CLASS).getClassName());
    }

    private static JulLogger getLogger(@NonNull String name, @NonNull Level level) {
        return LOGGER_CACHE.get(level).computeIfAbsent(name, k -> new JulLogger(k, level));
    }

    private static JulLogger getLogger(String name) {
        return getLogger(name, DEFAULT_LEVEL);
    }

    private static @NonNull EnumMap<Level, Map<String, JulLogger>> initLoggerCache() {
        EnumMap<Level, Map<String, JulLogger>> loggerCache = new EnumMap<>(Level.class);
        EnumSet.allOf(Level.class).forEach(level -> loggerCache.put(level, new ConcurrentHashMap<>()));
        return loggerCache;
    }

    private static @NonNull String replaceWithJulPlaceholders(@NonNull String message) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = message.toCharArray();
        int placeholderIndex = 0;
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            stringBuilder.append(current);
            char next = i + 1 == chars.length ? Character.MIN_VALUE : chars[i + 1];
            if (current == '{' && next == '}') {
                stringBuilder.append(placeholderIndex++);
            }
        }
        return stringBuilder.toString();
    }

    private static @NonNull EnumMap<Level, java.util.logging.Level> setLevelMap() {
        EnumMap<Level, java.util.logging.Level> levelMap = new EnumMap<>(Level.class);
        levelMap.put(TRACE, java.util.logging.Level.FINEST);
        levelMap.put(DEBUG, java.util.logging.Level.FINE);
        levelMap.put(INFO, java.util.logging.Level.INFO);
        levelMap.put(WARN, java.util.logging.Level.WARNING);
        levelMap.put(ERROR, java.util.logging.Level.SEVERE);
        return levelMap;
    }

    private static Object @NonNull [] supply(Object[] objects) {
        return Arrays.stream(objects).map(JulLogger::supply).toArray();
    }

    private static Object supply(Object o) {
        return o instanceof Supplier<?> ? ((Supplier<?>) o).get() : o;
    }

    @Override
    public Logger atLevel(Level level) {
        if (this.level == level) {
            return this;
        }
        return level == OFF ? NoopLogger.OFF : getLogger(this.name, level);
    }

    @Override
    public @NonNull Level getLevel() {
        return this.level;
    }

    @Override
    public boolean isEnabled() {
        return this.delegate.isLoggable(LEVEL_MAP.get(this.level));
    }

    @Override
    public void log(Object message) {
        if (!this.isEnabled()) {
            return;
        }
        delegate.log(new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(supply(message))));
    }

    @Override
    public void log(String message, Object... args) {
        if (!this.isEnabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJulPlaceholders(message));
        extendedLogRecord.setParameters(supply(args));
        delegate.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t) {
        if (!this.isEnabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord = new ExtendedLogRecord(LEVEL_MAP.get(this.level), t.getMessage());
        extendedLogRecord.setThrown(t);
        delegate.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, Object message) {
        if (!this.isEnabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), Objects.toString(supply(message)));
        extendedLogRecord.setThrown(t);
        delegate.log(extendedLogRecord);
    }

    @Override
    public void log(Throwable t, String message, Object... args) {
        if (!this.isEnabled()) {
            return;
        }
        ExtendedLogRecord extendedLogRecord =
                new ExtendedLogRecord(LEVEL_MAP.get(this.level), replaceWithJulPlaceholders(message));
        extendedLogRecord.setParameters(supply(args));
        extendedLogRecord.setThrown(t);
        delegate.log(extendedLogRecord);
    }

    @NonNull String getName() {
        return name;
    }

    private static class CallStack {
        private static StackTraceElement directCallerOf(@NonNull Class<?> calleeClass) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String calleeClassName = calleeClass.getName();
            for (int i = 0; i < stackTrace.length; i++) {
                if (calleeClassName.equals(stackTrace[i].getClassName())) {
                    for (int j = i + 1; j < stackTrace.length; j++) {
                        if (!calleeClassName.equals(stackTrace[j].getClassName())) {
                            return stackTrace[j];
                        }
                    }
                    break;
                }
            }
            throw new NoSuchElementException("unable to locate caller class of " + calleeClass + " in call stack "
                    + Arrays.toString(stackTrace));
        }
    }

    private static class ExtendedLogRecord extends LogRecord {
        private String callerClassName;
        private String callerMethodName;
        private boolean retrieveCaller;

        public ExtendedLogRecord(java.util.logging.Level level, String msg) {
            super(level, msg);
            retrieveCaller = true;
        }

        @Override
        public String getSourceClassName() {
            if (retrieveCaller) {
                retrieveCaller();
            }
            return callerClassName;
        }

        @Override
        public void setSourceClassName(String sourceClassName) {
            callerClassName = sourceClassName;
            retrieveCaller = false;
        }

        @Override
        public String getSourceMethodName() {
            if (retrieveCaller) {
                retrieveCaller();
            }
            return callerMethodName;
        }

        @Override
        public void setSourceMethodName(String sourceMethodName) {
            callerMethodName = sourceMethodName;
            retrieveCaller = false;
        }

        private void retrieveCaller() {
            StackTraceElement caller = CallStack.directCallerOf(SERVICE_INTERFACE_CLASS);
            setSourceClassName(caller.getClassName());
            setSourceMethodName(caller.getMethodName());
        }
    }
}
