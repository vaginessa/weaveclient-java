package org.exfio.weave.client;

import org.exfio.weave.client.WeaveClient.StorageVersion;

public class WeaveRegistrationParams implements WeaveClientParams {
	public String baseURL;
	public String user;
	public String password;
	public String email;
	
	public StorageVersion getStorageVersion() {
		return null;
	}
}
