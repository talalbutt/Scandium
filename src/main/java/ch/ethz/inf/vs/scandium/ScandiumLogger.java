package ch.ethz.inf.vs.scandium;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * ScandiumLogger is a helper class for the logging in Scandium.
 * CaliforniumLogger makes sure that {@link #initializeLogger()} initializes the
 * loggers before use so that they print in the appropriate format.
 */
public class ScandiumLogger {
	
	private static final Logger SCANDIUM_LOGGER = Logger.getLogger(ScandiumLogger.class.getPackage().getName());
	
	/**
	 * Initializes the logger. The resulting format of logged messages is
	 * 
	 * <pre>
	 * {@code
	 * | Thread ID | Level | Message | Class | Line No | Method | Thread name |
	 * }
	 * </pre>
	 * 
	 * where Level is the {@link Level} of the message, the <code>Class</code>
	 * is the class in which the log statement is located, the
	 * <code>Line No</code> is the line number of the logging statement, the
	 * <code>Method</code> is the method name in which the statement is located
	 * and the <code>Thread name</code> is the name of the thread that executed
	 * the logging statement.
	 */
	public static void initializeLogger() {
		SCANDIUM_LOGGER.setUseParentHandlers(false);
		SCANDIUM_LOGGER.addHandler(new ScandiumHandler());
	}
	
	/**
	 * Disables logging by setting the level of all loggers that have been
	 * requested over this class to OFF.
	 */
	public static void disableLogging() {
		SCANDIUM_LOGGER.setLevel(Level.OFF);
	}

	/**
	 * Sets the logger level of all loggers that have been requests over this
	 * class to the specified level and sets this level for all loggers that are
	 * going to be requested over this class in the future.
	 * 
	 * @param level
	 *            the new logger level
	 */
	public static void setLoggerLevel(Level level) {
		SCANDIUM_LOGGER.setLevel(level);
	}

	/**
	 * Sets the logger level of the given name to the specified level and sets
	 * this level for all loggers that are going to be requested over this class
	 * in the future.
	 *
	 * @param logger
	 *            the logger to adjust
	 * @param level
	 *            the new logger level
	 */
	public static void setLoggerLevel(String logger, Level level) {
		Logger.getLogger(logger).setLevel(level);
	}

	private static class ScandiumHandler extends StreamHandler {

		public ScandiumHandler() {
			super(System.out, new ScandiumFormatter());
			this.setLevel(Level.ALL);
		}

		@Override
		public synchronized void publish(LogRecord record) {
			super.publish(record);
			super.flush();
		}
	}
}
