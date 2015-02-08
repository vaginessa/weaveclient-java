package org.exfio.weave.account.fxa;

import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class FxAccountParams extends WeaveAccountParams {
	public byte[] wrapkB;
	public FxAccountCertificate browserIdCertificate;
	public String tokenServer;
	
	public FxAccountParams() {
		apiVersion = ApiVersion.v1_5;
		wrapkB = null;
		browserIdCertificate = null;
		tokenServer = null;
	}
}
