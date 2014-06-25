package org.exfio.weave.client;

import org.exfio.weave.client.WeaveClient.StorageVersion;

public class WeaveBasicParams implements WeaveClientParams {
	public String baseURL;
	public String user;
	public String password;
	
	public StorageVersion getStorageVersion() {
		return null;
	}
}
