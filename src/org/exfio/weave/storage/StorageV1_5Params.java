package org.exfio.weave.storage;

import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class StorageV1_5Params extends StorageParams {
	public String user;
	public String hawkid;
	public byte[] hawkkey;
	
	public StorageV1_5Params() {
		this.apiVersion = ApiVersion.v1_1;
	}
}
