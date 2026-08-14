package com.finovago.p2p.logging;

import java.util.Map;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Spring Boot's built-in %clr(%level) maps DEBUG/TRACE to the same green as INFO. This converter
 * gives DEBUG its own color so a console filled with scheduler heartbeat logs stays readable
 * against real INFO/WARN/ERROR lines.
 */
public class LevelColorConverter extends CompositeConverter<ILoggingEvent> {

    private static final Map<Integer, AnsiColor> COLORS = Map.of(
            Level.ERROR_INT, AnsiColor.RED,
            Level.WARN_INT, AnsiColor.YELLOW,
            Level.INFO_INT, AnsiColor.GREEN,
            Level.DEBUG_INT, AnsiColor.BRIGHT_MAGENTA,
            Level.TRACE_INT, AnsiColor.CYAN);

    @Override
    protected String transform(ILoggingEvent event, String in) {
        AnsiColor color = COLORS.getOrDefault(event.getLevel().toInt(), AnsiColor.DEFAULT);
        return AnsiOutput.toString(color, in);
    }
}
