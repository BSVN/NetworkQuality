package net.bsn.resaa.hybridcalltest.utilities;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import net.bsn.resaa.hybridcall.utilities.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.bsn.resaa.hybridcalltest.BuildConfig;

public class FileLogger implements Logger {

	private String folderName;
	private List<String> batch;
	private int batchMaxSize;

	public FileLogger(String folderName, int batchMaxSize) {
		this.folderName = folderName;
		this.batchMaxSize = batchMaxSize;
		batch = new ArrayList<>();
	}

	@Override
	public void info(String msg) {
		Log.d("log", msg);

		logInFile(msg);
	}

	@Override
	public void error(String msg) {
		Log.e("log", msg);

		logInFile(msg);
	}

	@Override
	public void stackTrace(Exception e) {
		error("An exception of type " + (e == null ? "null" : e.getClass().toString()) + " occurred.");
		error("Message is: " + (e == null ? "null" : e.getMessage()));
		error("Printing the stack trace.");
		if (e != null) {
			e.printStackTrace();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(os);
			e.printStackTrace(ps);
			try {
				logInFile(os.toString("UTF8"));
			} catch (UnsupportedEncodingException ignored) {
			}
		}
		error("End of stack trace.");
	}

	public synchronized void flush() {
		Log.i("log", "Flushing FileLogger.");
		for (String message : batch) {
			File dir;
			if (Build.VERSION.SDK_INT >= 19) {
				dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/" + folderName + "/");
			} else {
				dir = new File(Environment.getExternalStorageDirectory() + "/Documents/" + folderName + "/");
			}
			final File path = dir;

			if (!path.exists())
				path.mkdirs();

			final File file = new File(path, "logs.txt");

			try {
				file.createNewFile();
				FileOutputStream fOut = new FileOutputStream(file, true);
				OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
				myOutWriter.append(message).append("\n");

				myOutWriter.close();

				fOut.flush();
				fOut.close();
			} catch (IOException e) {
				Log.e("Exception", "File write failed: " + e.toString());
			}
		}
		batch.clear();
	}

	private synchronized void logInFile(String msg) {
		msg = BuildConfig.VERSION_NAME + ": " + new Date() + " @" + Thread.currentThread().getName() + "|| " + msg;
		batch.add(msg);
		if (batch.size() >= batchMaxSize)
			flush();
	}
}
