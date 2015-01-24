package org.exfio.weave.account.legacy;

import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class FirefoxSyncLegacyParams extends WeaveAccountParams {
	public String syncKey;

	public FirefoxSyncLegacyParams() {
		apiVersion     = ApiVersion.v1_1;
		storageVersion = StorageVersion.v5;
	}
}
