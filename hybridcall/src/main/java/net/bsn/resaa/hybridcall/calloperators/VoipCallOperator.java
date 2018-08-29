package net.bsn.resaa.hybridcall.calloperators;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import net.bsn.resaa.hybridcall.internet.QualityOfService;
import net.bsn.resaa.hybridcall.internet.voip.VoipCall;
import net.bsn.resaa.hybridcall.internet.voip.VoipException;
import net.bsn.resaa.hybridcall.internet.voip.VoipManager;
import net.bsn.resaa.hybridcall.internet.voip.VoipProfile;
import net.bsn.resaa.hybridcall.utilities.Func;
import net.bsn.resaa.hybridcall.utilities.TimedCache;
import net.bsn.resaa.hybridcall.utilities.logging.Logging;
import net.resaa.bsn.hybridcall.R;

import java.util.ArrayList;
import java.util.List;

public class VoipCallOperator extends CallOperator {

	private final int QUALITY_TEST_PING_COUNT;
	private final int QUALITY_TEST_PING_PACKET_SIZE;
	private final int QUALITY_TEST_PING_TIMEOUT;
	private final int QUALITY_TEST_MIN_LINK_SPEED;
	private final int QUALITY_TEST_PING_MIN_WAIT;
	private final int QUALITY_TEST_PERFECT_LOSS_THRESHOLD;
	private final int QUALITY_TEST_MEDIOCRE_LOSS_THRESHOLD;
	private final int QUALITY_TEST_PERFECT_LATENCY_THRESHOLD;
	private final int QUALITY_TEST_MEDIOCRE_LATENCY_THRESHOLD;
	private final int QUALITY_TEST_PERFECT_JITTER_THRESHOLD;
	private final int QUALITY_TEST_MEDIOCRE_JITTER_THRESHOLD;
	private final int INTERNET_PROVIDER_UPDATE_MIN_WAIT;

	private TimedCache<CallQuality> experimentalCallQuality;
	private TimedCache<InternetProvider> currentInternetProvider;

	private VoipManager voipManager;
	private VoipCall currentCall;

	private List<InternetProvider> possibleInternetProviders;

	protected Context context;

	private InnerPhoneStateListener phoneStateListener;

	public VoipCallOperator(Context context, VoipManager voipManager) {
		this.context = context;

		Resources resources = context.getResources();
		QUALITY_TEST_PING_COUNT = resources.getInteger(R.integer.voip_call_quality_test_ping_count);
		QUALITY_TEST_PING_PACKET_SIZE = resources.getInteger(R.integer.voip_call_quality_test_ping_packet_size);
		QUALITY_TEST_PING_TIMEOUT = resources.getInteger(R.integer.voip_call_quality_test_ping_timeout);
		QUALITY_TEST_MIN_LINK_SPEED = resources.getInteger(R.integer.voip_call_quality_test_min_link_speed);
		QUALITY_TEST_PING_MIN_WAIT = resources.getInteger(R.integer.voip_call_quality_test_ping_min_wait);
		QUALITY_TEST_PERFECT_LOSS_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_perfect_loss_threshold);
		QUALITY_TEST_MEDIOCRE_LOSS_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_mediocre_loss_threshold);
		QUALITY_TEST_PERFECT_LATENCY_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_perfect_latency_threshold);
		QUALITY_TEST_MEDIOCRE_LATENCY_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_mediocre_latency_threshold);
		QUALITY_TEST_PERFECT_JITTER_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_perfect_jitter_threshold);
		QUALITY_TEST_MEDIOCRE_JITTER_THRESHOLD = resources.getInteger(R.integer.voip_call_quality_test_mediocre_jitter_threshold);
		INTERNET_PROVIDER_UPDATE_MIN_WAIT = resources.getInteger(R.integer.voip_call_internet_provider_update_min_wait);

		experimentalCallQuality = new TimedCache<CallQuality>(QUALITY_TEST_PING_MIN_WAIT * 1000) {
			@Override
			public CallQuality getNewValue() {
				return getQualityExperimentally();
			}
		};

		currentInternetProvider = new TimedCache<InternetProvider>(INTERNET_PROVIDER_UPDATE_MIN_WAIT * 1000) {
			@Override
			public InternetProvider getNewValue() {
				return getCurrentInternetProvider();
			}
		};

		possibleInternetProviders = new ArrayList<>();
		possibleInternetProviders.add(WifiInternetProvider.getInstane(context));
		possibleInternetProviders.add(MobileInternetProvider.getInstane(context));

		this.voipManager = voipManager;

		voipManager.addOnCallStartedListener(new Func<VoipCall, Void>() {
			@Override
			public Void run(VoipCall voipCall) {
				Logging.info("Voip call started to " + voipCall.getCallee().getUsername() + ".");
				currentCall = voipCall;
				callStarted(voipCall.getCallee().getUsername());
				return null;
			}
		});
		voipManager.addOnCallEndedListener(new Func<VoipCall, Void>() {
			@Override
			public Void run(VoipCall voipCall) {
				Logging.info("Voip call ended to " + voipCall.getCallee().getUsername() + ".");
				callEnded(voipCall.getCallee().getUsername());
				return null;
			}
		});

		phoneStateListener = new InnerPhoneStateListener();
		TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager != null)
			telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
	}

	@Override
	public final boolean isAvailable() {
		boolean result = voipManager.getRegisteredCaller() != null;
		Logging.info("Voip call availability is " + result + ".");
		return result;
	}

	@Override
	public void endCall() {
		Logging.info("Ending voip call to " + currentCall.getCallee().getUsername() + ".");
		currentCall.endCall();
	}

	@Override
	public void sendDtmf(final char c) {
		Logging.info("Command to send DTMF character " + c + " in voip call to " +
				currentCall.getCallee().getUsername() + ".");
		currentCall.sendDtmf(c);
	}

	@Override
	public CallQuality getCurrentQuality() {
		CallQuality result;
		if (currentInternetProvider.getValue().getLinkSpeed() >= QUALITY_TEST_MIN_LINK_SPEED)
			result = experimentalCallQuality.getValue();
		else
			result = CallQuality.POOR;
		Logging.info("Voip call quality is " + result.name() + ".");
		return result;
	}

	@Override
	public CallPrice getCurrentPrice() {
		return currentInternetProvider.getValue().getCurrentPrice();
	}

	@Override
	public void destroy() {
		Logging.info("Destroying voip call operator.");

		for (InternetProvider provider : possibleInternetProviders)
			provider.destroy();

		TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager != null)
			telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		phoneStateListener = null;
		voipManager = null;
	}

	@Override
	public void call(final String phoneNumber) {
		Logging.info("Starting voip call to " + phoneNumber + ".");
		try {
			voipManager.makeCall(
					new VoipProfile(phoneNumber, voipManager.getRegisteredCaller().getDomain()),
					getCurrentQuality() != CallQuality.PERFECT);
		} catch (VoipException e) {
			Logging.error("Error occurred starting voip call.");
			Logging.stackTrace(e);
		}
	}

	private CallQuality getQualityExperimentally() {
		QualityOfService qos = QualityOfService.getQualityOfService(QUALITY_TEST_PING_COUNT,
				QUALITY_TEST_PING_PACKET_SIZE, voipManager.getRegisteredCaller().getDomain(),
				QUALITY_TEST_PING_TIMEOUT);
		return getQualityByQos(qos);
	}

	private CallQuality getQualityByQos(QualityOfService qos) {
		if (qos.getPacketLoss() * 10 < QUALITY_TEST_PERFECT_LOSS_THRESHOLD &&
				qos.getLatency() * 1000 < QUALITY_TEST_PERFECT_LATENCY_THRESHOLD &&
				qos.getJitter() * 1000 < QUALITY_TEST_PERFECT_JITTER_THRESHOLD)
			return CallQuality.PERFECT;

		if (qos.getPacketLoss() * 10 < QUALITY_TEST_MEDIOCRE_LOSS_THRESHOLD &&
				qos.getLatency() * 1000 < QUALITY_TEST_MEDIOCRE_LATENCY_THRESHOLD &&
				qos.getJitter() * 1000 < QUALITY_TEST_MEDIOCRE_JITTER_THRESHOLD)
			return CallQuality.MEDIOCRE;

		return CallQuality.POOR;
	}

	private InternetProvider getCurrentInternetProvider() {
		for (InternetProvider provider : possibleInternetProviders) {
			if (provider.canProvide()) {
				Logging.info("Current voip internet provider is " + provider.getInternetTypeName() + ".");
				return provider;
			}
		}
		return null;
	}

	private class InnerPhoneStateListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String phoneNumber) {
			switch (state) {
				case TelephonyManager.CALL_STATE_RINGING:
					if (voipManager.getCurrentCall() != null) {
						Logging.info("Incomming GSM call in the middle of a voip call.");
						GsmCallOperator.getInstance(context).endCall();
					}
			}
		}
	}

	private interface InternetProvider {
		CallPrice getCurrentPrice();

		int getLinkSpeed();

		void destroy();

		String getInternetTypeName();

		boolean canProvide();
	}

	private static class WifiInternetProvider implements InternetProvider {

		@SuppressLint("StaticFieldLeak")
		private static WifiInternetProvider instance;

		private Context context;

		public static WifiInternetProvider getInstane(Context context) {
			if (instance == null)
				instance = new WifiInternetProvider(context.getApplicationContext());
			return instance;
		}

		private WifiInternetProvider(Context context) {
			this.context = context;
		}

		@Override
		public CallPrice getCurrentPrice() {
			return CallPrice.CHEAP;
		}

		@Override
		public int getLinkSpeed() {
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			int speed = wifiInfo.getLinkSpeed() * 1000;
			Logging.info("Wifi link speed is " + speed + ".");
			return speed;
		}

		@Override
		public void destroy() {
			MobileInternetProvider.instance = null;
		}

		@Override
		public String getInternetTypeName() {
			return "wifi";
		}

		@Override
		public boolean canProvide() {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo info = cm.getActiveNetworkInfo();
			return info != null && "WIFI".equalsIgnoreCase(info.getTypeName());
		}
	}

	private static class MobileInternetProvider implements InternetProvider {

		@SuppressLint("StaticFieldLeak")
		private static MobileInternetProvider instance;

		private Context context;
		private int rxqual;
		private InnerPhoneStateListener phoneStateListener;

		public static MobileInternetProvider getInstane(Context context) {
			if (instance == null)
				instance = new MobileInternetProvider(context.getApplicationContext());
			return instance;
		}

		private MobileInternetProvider(Context context) {
			this.context = context;

			phoneStateListener = new InnerPhoneStateListener();
			TelephonyManager telephonyManager = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
			if (telephonyManager != null)
				telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
			else
				rxqual = 100;
		}

		@Override
		public CallPrice getCurrentPrice() {
			return CallPrice.MEDIOCRE;
		}

		@Override
		public int getLinkSpeed() {
			int speed = (int) (getMobileDataSpeed() * (100 - getBer(rxqual)) / 100.0);
			Logging.info("Mobile link speed is " + speed + ".");
			return speed;
		}

		@Override
		public void destroy() {
			Logging.info("Destroying mobile internet provider.");
			TelephonyManager telephonyManager = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
			if (telephonyManager != null)
				telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
			phoneStateListener = null;
			MobileInternetProvider.instance = null;
		}

		@Override
		public String getInternetTypeName() {
			return "mobile";
		}

		@Override
		public boolean canProvide() {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo info = cm.getActiveNetworkInfo();
			return info != null && "MOBILE".equalsIgnoreCase(info.getTypeName());
		}

		private int getMobileDataSpeed() {
			// https://stackoverflow.com/questions/31518992/android-network-type-detection-and-assessing-the-connecting-speed
			TelephonyManager mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			int networkType = mTelephonyManager.getNetworkType();
			Logging.info("Mobile data type is " + networkType + ".");
			switch (networkType) {
				case TelephonyManager.NETWORK_TYPE_GPRS:
					return 100;
				case TelephonyManager.NETWORK_TYPE_EDGE:
					return 75;
				case TelephonyManager.NETWORK_TYPE_CDMA:
					return 39;
				case TelephonyManager.NETWORK_TYPE_1xRTT:
					return 75;
				case TelephonyManager.NETWORK_TYPE_IDEN:
					return 25;
				case TelephonyManager.NETWORK_TYPE_UMTS:
					return 3700;
				case TelephonyManager.NETWORK_TYPE_EVDO_0:
					return 700;
				case TelephonyManager.NETWORK_TYPE_EVDO_A:
					return 1000;
				case TelephonyManager.NETWORK_TYPE_HSDPA:
					return 8000;
				case TelephonyManager.NETWORK_TYPE_HSUPA:
					return 12000;
				case TelephonyManager.NETWORK_TYPE_HSPA:
					return 1200;
				case TelephonyManager.NETWORK_TYPE_EVDO_B:
					return 5000;
				case TelephonyManager.NETWORK_TYPE_EHRPD:
					return 1500;
				case TelephonyManager.NETWORK_TYPE_HSPAP:
					return 15000;
				case TelephonyManager.NETWORK_TYPE_LTE:
					return 20000;
				default:
					return 0;
			}
		}

		private double getBer(int rxqual) {
			//http://www.rfwireless-world.com/Terminology/GPRS-RxQUAL-vs-BER.html
			switch (rxqual) {
				case 0:
					return 0.05;
				case 1:
					return 0.28;
				case 2:
					return 0.575;
				case 3:
					return 1.15;
				case 4:
					return 2.3;
				case 5:
					return 4.6;
				case 6:
					return 9.3;
				case 7:
					return 20;
				default:
					return 100;
			}
		}

		private class InnerPhoneStateListener extends PhoneStateListener {

			@Override
			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				super.onSignalStrengthsChanged(signalStrength);

				rxqual = signalStrength.getGsmBitErrorRate();

				Logging.info("Phone rxqual changed to " + rxqual + ".");
			}
		}
	}
}