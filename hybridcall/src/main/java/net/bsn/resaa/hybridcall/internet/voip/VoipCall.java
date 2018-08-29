package net.bsn.resaa.hybridcall.internet.voip;

public abstract class VoipCall {

	protected VoipProfile caller;
	protected VoipProfile callee;

	public VoipCall(VoipProfile caller, VoipProfile callee) {
		this.caller = caller;
		this.callee = callee;
	}

	public VoipProfile getCaller() {
		return caller;
	}

	public VoipProfile getCallee() {
		return callee;
	}

	public abstract void endCall();

	public abstract void sendDtmf(char c);
}
