package org.exfio.weave.client;

import org.exfio.weave.client.WeaveClientFactory.StorageVersion;

public class AccountParams {
	protected StorageVersion version = null;
	
	public String baseURL;
	public String user;
	public String password;
	
	public StorageVersion getStorageVersion() {
		return version;
	}
}
