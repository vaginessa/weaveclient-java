package org.exfio.weave.account.legacy;

import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class WeaveSyncV5AccountParams extends WeaveAccountParams {
	public String syncKey;

	public WeaveSyncV5AccountParams() {
		apiVersion     = ApiVersion.v1_1;
	}
}
