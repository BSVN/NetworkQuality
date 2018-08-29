package net.bsn.resaa.hybridcall.calloperators;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import net.bsn.resaa.hybridcall.utilities.logging.Logging;
import net.resaa.bsn.hybridcall.R;

import java.lang.reflect.Method;

public class GsmCallOperator extends CallOperator {

	private static final int PHONE_CALL_PENDING_INTENT_REQUEST_CODE = 1;
	private static final int DEFAULT_PHONE_STATE_LISTENER_EVENTS = PhoneStateListener.LISTEN_SERVICE_STATE |
			PhoneStateListener.LISTEN_SIGNAL_STRENGTHS;
	private static final int IN_CALL_PHONE_STATE_LISTENER_EVENTS = PhoneStateListener.LISTEN_SERVICE_STATE |
			PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_CALL_STATE;

	private final int QUALITY_TEST_PERFECT_STRENGTH_THRESHOLD;
	private final int QUALITY_TEST_MEDIOCRE_STRENGTH_THRESHOLD;

	@SuppressLint("StaticFieldLeak")
	private static GsmCallOperator instance;

	public static GsmCallOperator getInstance(Context context) {
		if (instance == null)
			instance = new GsmCallOperator(context.getApplicationContext());
		return instance;
	}

	private Context context;
	private boolean isAvailable;
	private CallQuality callQuality;
	private InnerPhoneStateListener phoneStateListener;
	private String calledPhoneNumber;

	private GsmCallOperator(Context context) {
		this.context = context;

		Resources resources = context.getResources();
		QUALITY_TEST_PERFECT_STRENGTH_THRESHOLD = resources.getInteger(R.integer.gsm_call_quality_test_perfect_strength_threshold);
		QUALITY_TEST_MEDIOCRE_STRENGTH_THRESHOLD = resources.getInteger(R.integer.gsm_call_quality_test_mediocre_strength_threshold);

		calledPhoneNumber = "";

		phoneStateListener = new InnerPhoneStateListener();

		TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager != null)
			telephonyManager.listen(phoneStateListener, DEFAULT_PHONE_STATE_LISTENER_EVENTS);
		else {
			isAvailable = false;
			callQuality = CallQuality.POOR;
		}
	}

	@Override
	public boolean isAvailable() {
		Logging.info("GSM call availability is " + isAvailable + ".");
		return isAvailable;
	}

	@Override
	public CallQuality getCurrentQuality() {
		Logging.info("GSM call quality is " + callQuality.name() + ".");
		return callQuality;
	}

	@Override
	public CallPrice getCurrentPrice() {
		return CallPrice.EXPENSIVE;
	}

	@SuppressLint("MissingPermission")
	@Override
	public void call(String phoneNumber) {
		try {
			Logging.info("Starting GSM call to " + phoneNumber + ".");

			Intent intent = new Intent(Intent.ACTION_CALL);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				intent.setPackage("com.android.server.telecom");
			else
				intent.setPackage("com.android.phone");

			intent.setData(Uri.parse("tel:" + phoneNumber));
			intent.putExtra("requestCode", PHONE_CALL_PENDING_INTENT_REQUEST_CODE);
			PendingIntent pendingIntent = PendingIntent.getActivity(context, PHONE_CALL_PENDING_INTENT_REQUEST_CODE, intent, 0);

			TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(phoneStateListener, IN_CALL_PHONE_STATE_LISTENER_EVENTS);

			calledPhoneNumber = phoneNumber;
			pendingIntent.send(Activity.RESULT_OK);
		} catch (Exception e) {
			Logging.error("Error occurred starting GSM call.");
			Logging.stackTrace(e);
		}
	}

	public void endCall() {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		try {
			Logging.info("Ending GSM call to " + calledPhoneNumber + ".");
			Class c = Class.forName(tm.getClass().getName());
			Method m = c.getDeclaredMethod("getITelephony");
			m.setAccessible(true);
			Object telephonyService = m.invoke(tm); // Get the internal ITelephony object
			c = Class.forName(telephonyService.getClass().getName()); // Get its class
			m = c.getDeclaredMethod("endCall"); // Get the "endCall()" method
			m.setAccessible(true); // Make it accessible
			m.invoke(telephonyService); // invoke endCall()
		} catch (Exception e) {
			Logging.error("Error occurred ending GSM call.");
			Logging.stackTrace(e);
		}
	}

	@Override
	public void sendDtmf(char c) {
		Logging.info("Command to send DTMF character " + c + " in GSM Call to " + calledPhoneNumber + ".");
		throw new IllegalStateException();
	}

	@Override
	public void destroy() {
		Logging.info("Destroying GSM call operator.");
		TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager != null)
			telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		phoneStateListener = null;
		GsmCallOperator.instance = null;
	}

	private class InnerPhoneStateListener extends PhoneStateListener {
		private int prevState;

		@Override
		public void onServiceStateChanged(ServiceState serviceState) {
			super.onServiceStateChanged(serviceState);

			Logging.info("Phone service state changed to " + serviceState.getState() + ".");

			GsmCallOperator.this.isAvailable = serviceState.getState() == ServiceState.STATE_IN_SERVICE;
		}

		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);

			int gsmStrength = signalStrength.getGsmSignalStrength();

			if (gsmStrength == 99) {
				try {
					Logging.info("Finding LTE signal strength.");
					Class c = Class.forName(signalStrength.getClass().getName());
					Method m = c.getDeclaredMethod("getLteSignalStrength");
					m.setAccessible(true);
					gsmStrength = (int) m.invoke(signalStrength);
				} catch (Exception e) {
					Logging.error("Error occurred in checking LT signal strength");
					Logging.stackTrace(e);
				}
			}

			Logging.info("Phone signal strength changed to " + gsmStrength + ".");

			if (gsmStrength == 99)
				gsmStrength = -200;

			if (gsmStrength >= QUALITY_TEST_PERFECT_STRENGTH_THRESHOLD)
				callQuality = CallQuality.PERFECT;
			else if (gsmStrength >= QUALITY_TEST_MEDIOCRE_STRENGTH_THRESHOLD)
				callQuality = CallQuality.MEDIOCRE;
			else
				callQuality = CallQuality.POOR;
		}

		@Override
		public void onCallStateChanged(int state, String ignored) {
			switch (state) {
				case TelephonyManager.CALL_STATE_OFFHOOK:
					Logging.info("GSM call started to " + calledPhoneNumber + ".");
					callStarted(calledPhoneNumber);
					prevState = state;
					break;
				case TelephonyManager.CALL_STATE_IDLE:
					Logging.info("GSM call Idle to " + calledPhoneNumber + ".");
					if ((prevState == TelephonyManager.CALL_STATE_OFFHOOK)) {
						prevState = state;
						Logging.info("GSM call Ended to " + calledPhoneNumber + ".");
						String tmpPhoneNumber = calledPhoneNumber;
						calledPhoneNumber = "";
						callEnded(tmpPhoneNumber);
						TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
						telephonyManager.listen(phoneStateListener, DEFAULT_PHONE_STATE_LISTENER_EVENTS);
					}
					break;
			}
		}
	}
}