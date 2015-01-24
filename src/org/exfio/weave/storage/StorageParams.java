package org.exfio.weave.storage;

import lombok.Getter;

import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class StorageParams {
	@Getter protected ApiVersion apiVersion = null;
	
	public String storageURL;
}
