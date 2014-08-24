package org.exfio.weave.client;

import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class WeaveClientV5Params extends AccountParams {
	public String syncKey;
	
	public WeaveClientV5Params() {
		version = StorageVersion.v5;
	}
}
