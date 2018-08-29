package net.bsn.resaa.hybridcall.internet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bsn.resaa.hybridcall.utilities.ProcessWaiter;
import net.bsn.resaa.hybridcall.utilities.logging.Logging;

public class QualityOfService {

	// need downSpeed and upSpeed?
	private float packetLoss;
	private float latency;
	private float jitter;

	public QualityOfService(float packetLoss, float latency, float jitter) {
		this.packetLoss = packetLoss;
		this.latency = latency;
		this.jitter = jitter;
	}

	public float getPacketLoss() {
		return packetLoss;
	}

	public float getLatency() {
		return latency;
	}

	public float getJitter() {
		return jitter;
	}

	public static QualityOfService getQualityOfService(int pingCount, int packetSize, String pingAddress, int maxWaitTimeSeconds) {
		return getQosByPing(pingCount, packetSize, maxWaitTimeSeconds, pingAddress);
	}

	private static QualityOfService getWorstQos() {
		return new QualityOfService(100, Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	private static QualityOfService getQosByPing(int pingCount, int packetSize, int maxWaitTime, String pingAddress) {
		try {
			String command = "ping -i 0.2 -c " + pingCount + " -s " + packetSize + " " + pingAddress;

			Logging.info("Running " + command);

			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec(command);
			ProcessWaiter processWaiter = new ProcessWaiter(process);
			processWaiter.waitFor(maxWaitTime * 1000);

			if (!processWaiter.isProcessFinished()) {
				Logging.info("Ping command timeout!");
				return getWorstQos();
			}

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String s;
			StringBuilder res = new StringBuilder();
			while ((s = stdInput.readLine()) != null)
				res.append(s).append("\n");
			process.destroy();

			Logging.info("Result: " + res);

			return getQosByPingResult(res.toString());
		} catch (Exception e) {
			Logging.error("Error occurred in pinging.");
			Logging.stackTrace(e);
			return getWorstQos();
		}
	}

	private static QualityOfService getQosByPingResult(String result) {
		Matcher matcher = Pattern.compile("(\\d+\\.*\\d*)% packet loss[\\s\\S]*" +
				"avg\\/max\\/mdev = .*\\/(\\d+\\.*\\d*)\\/.*\\/(\\d+\\.*\\d*) ms").matcher(result);

		if (matcher.find()) {
			float loss = Float.parseFloat(matcher.group(1));
			float latency = Float.parseFloat(matcher.group(2));
			float jitter = Float.parseFloat(matcher.group(3));

			Logging.info("QoS loss is " + loss + ".");
			Logging.info("QoS latency is " + latency + ".");
			Logging.info("QoS jitter is " + jitter + ".");

			return new QualityOfService(loss, latency, jitter);
		} else
			return getWorstQos();
	}

}
