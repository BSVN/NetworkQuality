package net.bsn.resaa.hybridcall.utilities;

public class ProcessWaiter {
	private Process process;
	private int exitCode;
	private boolean processFinished;

	public ProcessWaiter(Process process) {
		this.process = process;
		processFinished = false;
	}

	public void waitFor(int timeoutMilliseconds) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					exitCode = process.waitFor();
					processFinished = true;
				} catch (InterruptedException ignore) {
				}
			}
		});
		thread.start();

		try {
			thread.join(timeoutMilliseconds);
		} catch (InterruptedException e) {
			thread.interrupt();
		}
	}

	public boolean isProcessFinished() {
		return processFinished;
	}

	public int getExitCode() {
		return exitCode;
	}
}