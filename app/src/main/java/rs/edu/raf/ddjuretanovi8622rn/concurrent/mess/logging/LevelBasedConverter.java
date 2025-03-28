package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;


/// This creates a hierarchy of colors to be used for different log levels.
/// It always appends the ANSI escape characters, which might cause errors on Windows.
/// Using Jansi might make this easier, but it doesn't seem to work.
/// If ANSI escape characters are an issue uncomment line 6 in logback.xml
public class LevelBasedConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        Level level = event.getLevel();
        return switch (level.toInt()) {
            case Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG; // Bold red
            case Level.WARN_INT -> ANSIConstants.BOLD + ANSIConstants.YELLOW_FG; // Bold yellow
            case Level.INFO_INT -> ANSIConstants.GREEN_FG; // Green
            case Level.DEBUG_INT -> ANSIConstants.CYAN_FG; // Cyan
            case Level.TRACE_INT -> ANSIConstants.BLUE_FG; // Blue
            default -> ANSIConstants.DEFAULT_FG; // Default
        };
    }
}
