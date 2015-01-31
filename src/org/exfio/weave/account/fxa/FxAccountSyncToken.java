package org.exfio.weave.account.fxa;

import org.mozilla.gecko.tokenserver.TokenServerToken;

public class FxAccountSyncToken {
	public String id       = null;
	public String key      = null;
	public String uid      = null;
	public String endpoint = null;

	public FxAccountSyncToken(TokenServerToken token) {
		fromTokenServerToken(token);
	}

	public void fromTokenServerToken(TokenServerToken other) {
		this.id       = other.id;
		this.key      = other.key;
		this.uid      = other.uid;
		this.endpoint = other.endpoint;
	}

}
