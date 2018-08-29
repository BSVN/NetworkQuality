package net.bsn.resaa.hybridcall.utilities.logging;

public interface Logger {
	void info(String msg);

	void error(String msg);

	void stackTrace(Exception e);
}
