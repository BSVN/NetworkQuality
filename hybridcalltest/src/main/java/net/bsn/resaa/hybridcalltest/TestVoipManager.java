package net.bsn.resaa.hybridcalltest;

import android.content.Context;

import net.bsn.resaa.hybridcall.internet.voip.TransferLayerProtocol;
import net.bsn.resaa.hybridcall.internet.voip.VoipCall;
import net.bsn.resaa.hybridcall.internet.voip.VoipException;
import net.bsn.resaa.hybridcall.internet.voip.VoipManager;
import net.bsn.resaa.hybridcall.internet.voip.VoipProfile;
import net.bsn.resaa.hybridcall.utilities.logging.Logging;

public class TestVoipManager extends VoipManager {

	private VoipProfile caller;

	private VoipCall call;

	protected TestVoipManager(Context context) {
		super(context);
	}

	@Override
	public void registerCaller(VoipProfile caller, String password, int port, TransferLayerProtocol protocol, int expiresInSeconds, Integer retryInSeconds) throws VoipException {
		Logging.info("test voip register");
		this.caller = caller;
	}

	@Override
	public VoipProfile getRegisteredCaller() {
		return caller;
	}

	@Override
	public VoipCall makeCall(VoipProfile callee, boolean lowBandwidth) throws VoipException {
		Logging.info("test voip make call");
		call = new VoipCall(caller, callee) {
			@Override
			public void endCall() {
				call = null;
				Logging.info("test voip end call");
			}

			@Override
			public void sendDtmf(char c) {
				Logging.info("test voip send dtmf");
			}
		};
		return call;
	}

	@Override
	public VoipCall getCurrentCall() {
		return call;
	}
}
