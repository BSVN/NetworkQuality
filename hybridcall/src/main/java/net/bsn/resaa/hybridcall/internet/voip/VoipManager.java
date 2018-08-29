package net.bsn.resaa.hybridcall.internet.voip;

import android.content.Context;

import net.bsn.resaa.hybridcall.utilities.Func;

import java.util.ArrayList;
import java.util.List;

public abstract class VoipManager {

	protected Context context;

	protected VoipManager(Context context) {
		this.context = context;
		callStartedListeners = new ArrayList<>();
		callEndedListeners = new ArrayList<>();
	}

	private List<Func<VoipCall, Void>> callStartedListeners;
	private List<Func<VoipCall, Void>> callEndedListeners;

	public void addOnCallStartedListener(Func<VoipCall, Void> listener) {
		callStartedListeners.add(listener);
	}

	public void addOnCallEndedListener(Func<VoipCall, Void> listener) {
		callEndedListeners.add(listener);
	}

	protected final void callStarted(VoipCall call) {
		for (Func<VoipCall, Void> listener : callStartedListeners)
			listener.run(call);
	}

	protected final void callEnded(VoipCall call) {
		for (Func<VoipCall, Void> listener : callEndedListeners)
			listener.run(call);
	}

	public abstract void registerCaller(VoipProfile caller, String callerPassword, int port,
										TransferLayerProtocol protocol, int expiresInSeconds, Integer retryInSeconds) throws VoipException;

	public abstract VoipProfile getRegisteredCaller();

	public abstract VoipCall makeCall(VoipProfile callee, boolean lowBandwidth) throws VoipException;

	public abstract VoipCall getCurrentCall();
}
