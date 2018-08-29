package net.bsn.resaa.hybridcalltest;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import net.bsn.resaa.hybridcall.internet.voip.TransferLayerProtocol;
import net.bsn.resaa.hybridcall.internet.voip.VoipCall;
import net.bsn.resaa.hybridcall.internet.voip.VoipException;
import net.bsn.resaa.hybridcall.internet.voip.VoipManager;
import net.bsn.resaa.hybridcall.internet.voip.VoipProfile;
import net.bsn.resaa.hybridcall.utilities.TimedCache;
import net.bsn.resaa.hybridcall.utilities.logging.Logging;
import net.resaa.bsn.hybridcall.R;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class LinphoneVoipManager extends VoipManager implements LinphoneCoreListener {

	private static class LinphoneVoipCall extends VoipCall {

		private LinphoneVoipCall(VoipProfile caller, VoipProfile callee) {
			super(caller, callee);
		}

		@Override
		public void endCall() {
			LinphoneVoipManager.getCore().terminateCall(LinphoneVoipManager.getCore().getCurrentCall());
		}

		@Override
		public void sendDtmf(char c) {
			LinphoneVoipManager.getCore().sendDtmf(c);
		}
	}

	private HashMap<LinphoneCall, LinphoneVoipCall> callMap;
	private LinphoneVoipCall toBeEstablishedCall;

	private static LinphoneVoipManager instance;
	private LinphoneCore core;
	private String basePath;
	private static boolean sExited;
	private VoipProfile registeredCaller;
	private VoipProfile toBeRegisteredCaller;
	private AudioManager audioManager;
	private Integer retryInSeconds;

	protected LinphoneVoipManager(Context context) {
		super(context);

		sExited = false;
		basePath = context.getFilesDir().getAbsolutePath();
		linphoneRootCaFile = basePath + "/rootca.pem";
		ringbackSoundFile = basePath + "/ringback.wav";
		userCertificatePath = basePath;
		callMap = new HashMap<>();

		registeredCaller = null;
		toBeRegisteredCaller = null;

		retryInSeconds = null;

		audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
	}

	private final String linphoneRootCaFile;
	private final String userCertificatePath;
	private final String ringbackSoundFile;
	private Timer timer;

	public synchronized static LinphoneVoipManager createAndStart(Context context) {
		if (instance == null || sExited) {
			instance = new LinphoneVoipManager(context.getApplicationContext());
			instance.startLibLinphone();
		}
		return instance;
	}

	public static synchronized LinphoneVoipManager getInstance() {
		if (instance != null && !sExited)
			return instance;
		else
			return null;
	}

	public static synchronized LinphoneCore getCore() {
		return getInstance().core;
	}

	@Override
	public void registerCaller(VoipProfile caller, String callerPassword, int port,
							   TransferLayerProtocol protocol, int expiresInSeconds, Integer retryInSeconds) throws VoipException {
		try {
			getCore().clearAuthInfos();
			getCore().clearProxyConfigs();

			toBeRegisteredCaller = caller;

			LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(
					"sip:" + caller.getDomain() + ":" + port);
			LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(caller.toString());

			proxyAddr.setTransport(protocol == TransferLayerProtocol.UDP ?
					LinphoneAddress.TransportType.LinphoneTransportUdp : LinphoneAddress.TransportType.LinphoneTransportTcp);

			LinphoneProxyConfig prxCfg = LinphoneVoipManager.getCore()
					.createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), null, true);

			prxCfg.enableAvpf(false);
			prxCfg.setAvpfRRInterval(0);
			prxCfg.enableQualityReporting(false);
			prxCfg.setQualityReportingCollector(null);
			prxCfg.setQualityReportingInterval(0);
			prxCfg.setExpires(expiresInSeconds);

			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance()
					.createAuthInfo(caller.getUsername(), "", callerPassword, null, null, caller.getDomain());
			this.retryInSeconds = retryInSeconds;

			LinphoneVoipManager.getCore().addProxyConfig(prxCfg);
			LinphoneVoipManager.getCore().addAuthInfo(authInfo);
			LinphoneVoipManager.getCore().setDefaultProxyConfig(prxCfg);

		} catch (LinphoneCoreException ex) {
			throw new VoipException(ex.getMessage());
		}
	}

	@Override
	public VoipProfile getRegisteredCaller() {
		return registeredCaller;
	}

	@Override
	public VoipCall makeCall(VoipProfile callee, boolean lowBandwidth) throws VoipException {
		try {
			Logging.info("Starting making linphone voip call to " + callee.getUsername() + ".");

			LinphoneAddress lAddress;

			lAddress = LinphoneVoipManager.getCore().interpretUrl(callee.toString());

			lAddress.setDisplayName(callee.getUsername());

			toBeEstablishedCall = new LinphoneVoipCall(registeredCaller, callee);

			LinphoneVoipManager.getInstance().inviteAddress(lAddress, lowBandwidth);

			return toBeEstablishedCall;
		} catch (LinphoneCoreException e) {
			Logging.error("Error occurred Starting making linphone voip call.");
			Logging.stackTrace(e);
			throw new VoipException(e.getMessage());
		}
	}

	@Override
	public VoipCall getCurrentCall() {
		return callMap.get(getCore().getCurrentCall());
	}

	private void inviteAddress(LinphoneAddress lAddress, boolean lowBandwidth) throws LinphoneCoreException {
		LinphoneCore lc = LinphoneVoipManager.getCore();

		LinphoneCallParams params = lc.createCallParams(null);

		params.setVideoEnabled(false);

		if (lowBandwidth) {
			params.enableLowBandwidth(true);
			Logging.info("Low bandwidth enabled in call params");
		}

		lc.inviteAddressWithParams(lAddress, params);
	}

	private void manageTunnelServer() {
		if (core == null)
			return;
		if (!core.isTunnelAvailable())
			return;
		core.tunnelSetMode(LinphoneCore.TunnelMode.auto);
	}

	private synchronized void startLibLinphone() {
		try {
			Logging.info("Starting LibLinphone.");
			copyAssetsFromPackage();
			core = LinphoneCoreFactory.instance().createLinphoneCore(this, context);
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
							if (core != null) {
								core.iterate();
							}
						}
					});
				}
			};
			timer = new Timer("Linphone scheduler");
			timer.schedule(lTask, 0, 20);
		} catch (Exception e) {
			Logging.error("Error occurred starting LibLinphone.");
			Logging.stackTrace(e);
		}
	}

	private synchronized void initLiblinphone(LinphoneCore lc) {
		Logging.info("Starting linphone init.");

		core = lc;

		core.setRingback(ringbackSoundFile);

		core.setZrtpSecretsCache(basePath + "/zrtp_secrets");

		core.setRootCA(linphoneRootCaFile);
		core.setUserCertificatesPath(userCertificatePath);

		int availableCores = Runtime.getRuntime().availableProcessors();
		Logging.info("MediaStreamer : " + availableCores + " cores detected and configured.");
		core.setCpuCount(availableCores);

		core.migrateCallLogs();

		updateNetworkReachability();
		Logging.info("Linphone init completed.");
	}

	private void updateNetworkReachability() {
		manageTunnelServer();
		core.setNetworkReachable(false);
		core.setNetworkReachable(true);
	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.ringback, ringbackSoundFile);
	}

	private void copyIfNotExist(int ressourceId, String target) throws IOException {
		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {
			copyFromPackage(ressourceId, lFileToCopy.getName());
		}
	}

	private void copyFromPackage(int ressourceId, String target) throws IOException {
		FileOutputStream lOutputStream = context.openFileOutput(target, 0);
		InputStream lInputStream = context.getResources().openRawResource(ressourceId);
		int readByte;
		byte[] buff = new byte[8048];
		while ((readByte = lInputStream.read(buff)) != -1) {
			lOutputStream.write(buff, 0, readByte);
		}
		lOutputStream.flush();
		lOutputStream.close();
		lInputStream.close();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void doDestroy() {
		try {
			timer.cancel();
			core.destroy();
		} catch (RuntimeException e) {
			Logging.error("Error occurred destroying Honeycomb version.");
			Logging.stackTrace(e);
		} finally {
			core = null;
			instance = null;
		}
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		sExited = true;
		instance.doDestroy();
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
	}

	@Override
	public void show(LinphoneCore lc) {
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {

	}

	@Override
	public void messageReceivedUnableToDecrypted(LinphoneCore lc, LinphoneChatRoom cr,
												 LinphoneChatMessage message) {
	}

	@Override
	public void displayStatus(final LinphoneCore lc, final String message) {
	}

	@Override
	public void globalState(final LinphoneCore lc, final LinphoneCore.GlobalState state, final String message) {
		Logging.info("New global state [" + state + "]");
		if (state == LinphoneCore.GlobalState.GlobalOn) {
			try {
				Logging.info("LinphoneManager globalState ON");
				initLiblinphone(lc);

			} catch (IllegalArgumentException iae) {
				Logging.error("Error occurred initializing Liblinphone.");
				Logging.stackTrace(iae);
			}
		}
	}

	@Override
	public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig proxy,
								  final LinphoneCore.RegistrationState state, final String message) {
		Logging.info("Linphone registration state is " + state.toString());
		if (state == LinphoneCore.RegistrationState.RegistrationOk)
			registeredCaller = toBeRegisteredCaller;
		else
			registeredCaller = null;
		//todo it needs "if(state is cleared)" or not? (what happens if it is already registered)
		// todo (in linphone app, it always calls the function (no matter what the state is)
		if (state == LinphoneCore.RegistrationState.RegistrationFailed && retryInSeconds != null) {
			if (Looper.myLooper() == null)
				Looper.prepare();
			new Handler(Looper.myLooper()).postDelayed(new Runnable() {
				@Override
				public void run() {
					if (registeredCaller == null && toBeRegisteredCaller != null)
						Logging.info("Linphone retrying registration.");
						lc.refreshRegisters();
				}
			}, retryInSeconds * 1000);
		}
	}

	@Override
	@SuppressLint("Wakelock")
	public void callState(final LinphoneCore lc, final LinphoneCall call, final LinphoneCall.State state, final String message) {
		Logging.info("Linphone voip call state is " + state.toString());
		if (state == LinphoneCall.State.OutgoingInit) {
			audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
			getCore().muteMic(false);
			callMap.put(call, toBeEstablishedCall);
			callStarted(toBeEstablishedCall);
			toBeEstablishedCall = null;
		}
		if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.CallReleased) {
			audioManager.setMode(AudioManager.MODE_NORMAL);
			getCore().muteMic(true);
			LinphoneVoipCall voipCall = callMap.get(call);
			callEnded(voipCall);
			callMap.remove(call);
		}
	}

	@Override
	public void callStatsUpdated(final LinphoneCore lc, final LinphoneCall call, final LinphoneCallStats stats) {
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
									  boolean encrypted, String authenticationToken) {
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
							   LinphoneAddress from, byte[] event) {
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
							  LinphoneCall.State new_call_state) {
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info) {
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
										 SubscriptionState state) {
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
							   String eventName, LinphoneContent content) {
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
									PublishState state) {
	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
	}

	@Override
	public void configuringStatus(LinphoneCore lc,
								  LinphoneCore.RemoteProvisioningState state, String message) {
	}

	@Override
	public void fileTransferProgressIndication(LinphoneCore lc,
											   LinphoneChatMessage message, LinphoneContent content, int progress) {
	}

	@Override
	public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message,
								 LinphoneContent content, byte[] buffer, int size) {
	}

	@Override
	public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message,
								LinphoneContent content, ByteBuffer buffer, int size) {
		return 0;
	}

	@Override
	public void uploadProgressIndication(LinphoneCore linphoneCore, int offset, int total) {
	}

	@Override
	public void uploadStateChanged(LinphoneCore linphoneCore, LinphoneCore.LogCollectionUploadState state, String info) {
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, LinphoneCore.EcCalibratorStatus status,
									int delay_ms, Object data) {
	}

	@Override
	public void friendListCreated(LinphoneCore lc, LinphoneFriendList list) {
	}

	@Override
	public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list) {
	}

	@Override
	public void networkReachableChanged(LinphoneCore lc, boolean enable) {
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm,
								  String username, String domain) {
	}

	@Override
	public void authenticationRequested(LinphoneCore lc,
										LinphoneAuthInfo authInfo, LinphoneCore.AuthMethod method) {
	}

}
