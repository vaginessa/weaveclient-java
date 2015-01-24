package org.exfio.weave.storage;

import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class StorageV1_1Params extends StorageParams {
	public String user;
	public String password;
	
	public StorageV1_1Params() {
		this.apiVersion = ApiVersion.v1_1;
	}
}
