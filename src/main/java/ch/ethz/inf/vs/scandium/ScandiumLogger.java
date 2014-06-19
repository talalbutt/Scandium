/*******************************************************************************
 * Copyright (c) 2014, Institute for Pervasive Computing, ETH Zurich.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * This file is part of the Scandium (Sc) Security for Californium.
 ******************************************************************************/
package ch.ethz.inf.vs.scandium;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * ScandiumLogger is a helper class for the logging in Scandium.
 * CaliforniumLogger makes sure that {@link #initialize()} initializes the
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
	public static void initialize() {
		SCANDIUM_LOGGER.setUseParentHandlers(false);
		SCANDIUM_LOGGER.addHandler(new ScandiumHandler());
	}
	
	/**
	 * Disables logging by setting the level of all loggers that have been
	 * requested over this class to OFF.
	 */
	public static void disable() {
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
	public static void setLevel(Level level) {
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
