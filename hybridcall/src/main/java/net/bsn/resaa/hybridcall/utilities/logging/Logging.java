package net.bsn.resaa.hybridcall.utilities.logging;

import android.util.Log;

public class Logging {
	static {
		logger = new Logger() {
			@Override
			public void info(String msg) {
				Log.i(Logging.class.getName(), msg);
			}

			@Override
			public void error(String msg) {
				Log.e(Logging.class.getName(), msg);
			}

			@Override
			public void stackTrace(Exception e) {
				e.printStackTrace();
			}
		};
	}

	private static Logger logger;

	public static Logger getLogger() {
		return logger;
	}

	public static void setLogger(Logger logger) {
		Logging.logger = logger;
	}

	public static void info(String msg) {
		logger.info(msg);
	}

	public static void error(String msg) {
		logger.error(msg);
	}

	public static void stackTrace(Exception e) {
		logger.stackTrace(e);
	}
}
