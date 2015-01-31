package org.exfio.weave.account.fxa;

import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class FxAccountParams extends WeaveAccountParams {
	public String tokenServer;
	
	public FxAccountParams() {
		apiVersion     = ApiVersion.v1_5;
		storageVersion = StorageVersion.v5;
	}
}
