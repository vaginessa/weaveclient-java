package org.exfio.weave.account.fxa;

import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class FxAccountParams extends WeaveAccountParams {
	public byte[] kB;
	public FxAccountCertificate browserIdCertificate;
	public String tokenServer;
	public FxAccountSyncToken syncToken;
	
	public FxAccountParams() {
		apiVersion = ApiVersion.v1_5;
		kB = null;
		browserIdCertificate = null;
		tokenServer = null;
		syncToken = null;
	}
}
