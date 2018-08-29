package net.bsn.resaa.hybridcall.calloperators;

import net.bsn.resaa.hybridcall.utilities.Func;

import java.util.ArrayList;
import java.util.List;

// terminology: call=outgoingCall
public abstract class CallOperator {

	public enum CallQuality {
		PERFECT,
		MEDIOCRE,
		POOR
	}

	public enum CallPrice {
		EXPENSIVE,
		MEDIOCRE,
		CHEAP
	}

	private List<Func<String, Void>> callStartedListeners;

	private List<Func<String, Void>> callEndedListeners;

	protected CallOperator() {
		callStartedListeners = new ArrayList<>();
		callEndedListeners = new ArrayList<>();
	}

	public void addOnCallStartedListener(Func<String, Void> listener) {
		callStartedListeners.add(listener);
	}

	public void addOnCallEndedListener(Func<String, Void> listener) {
		callEndedListeners.add(listener);
	}

	public void removeOnCallStartedListener(Func<String, Void> listener) {
		callStartedListeners.remove(listener);
	}

	public void removeOnCallEndedListener(Func<String, Void> listener) {
		callEndedListeners.remove(listener);
	}

	protected final void callStarted(String phoneNumber) {
		for (Func<String, Void> listener : callStartedListeners)
			listener.run(phoneNumber);
	}

	protected final void callEnded(String phoneNumber) {
		for (Func<String, Void> listener : callEndedListeners)
			listener.run(phoneNumber);
	}

	@Override
	protected void finalize() throws Throwable {
		destroy();
		super.finalize();
	}

	public abstract boolean isAvailable();

	public abstract CallQuality getCurrentQuality();

	public abstract CallPrice getCurrentPrice();

	public abstract void call(String phoneNumber);

	public abstract void endCall();

	public abstract void sendDtmf(char c);

	public abstract void destroy();
}
