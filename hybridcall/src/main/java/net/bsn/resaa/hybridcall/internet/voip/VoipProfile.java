package net.bsn.resaa.hybridcall.internet.voip;

import java.net.URL;

public class VoipProfile {

	private String username;
	private String domain;

	public VoipProfile(String username, String domain) {
		this.username = username;
		this.domain = domain;
	}

	public String getUsername() {
		return username;
	}

	public String getDomain() {
		return domain;
	}

	@Override
	public String toString() {
		return "sip:" + username + "@" + domain;
	}
}
