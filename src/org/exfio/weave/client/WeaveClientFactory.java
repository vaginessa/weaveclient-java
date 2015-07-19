package org.exfio.weave.client;


import lombok.Getter;
import lombok.Setter;

import org.exfio.weave.InvalidStorageException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientV1_1;


public class WeaveClientFactory {
	
	public static enum ApiVersion {
		v1_1,
		v1_5;
	}
	
	public static enum StorageVersion {
		v5,
		v6;
	}

	@Getter @Setter
	private static ApiVersion defaultApiVersion = ApiVersion.v1_1;
	
	@Getter @Setter
	private static StorageVersion defaultStorageVersion = StorageVersion.v5;
	
	public static String apiVersionToString(ApiVersion version) {
		String versionString = null;
		switch (version) {
		case v1_1:
			versionString = "1.1";
			break;
		case v1_5:
			versionString = "1.5";
			break;
		default:
		}
		return versionString;
	}

	public static ApiVersion stringToApiVersion(String version) {
		ApiVersion apiVersion = null;
		if ( version.equals("1.1") ) {
			apiVersion = ApiVersion.v1_1;
		} else if ( version.equals("1.5") ) {
			apiVersion = ApiVersion.v1_5;
		}
		return apiVersion;
	}

	public static String storageVersionToString(StorageVersion version) {
		String versionString = null;
		switch (version) {
		case v5:
			versionString = "5";
			break;
		case v6:
			versionString = "6";
			break;
		default:
		}
		return versionString;
	}

	public static StorageVersion stringToStorageVersion(String version) {
		StorageVersion storageVersion = null;
		if ( version.equals("5") ) {
			storageVersion = StorageVersion.v5;
		} else if ( version.equals("6") ) {
			storageVersion = StorageVersion.v6;
		}
		return storageVersion;
	}

	public static final WeaveClient getInstance(WeaveAccount account) throws WeaveException, InvalidStorageException {
		//return WeaveClient for given parameters
		WeaveClient weaveClient = null;
		
		if ( account.getAccountParams().getApiVersion() == null ) {
			//auto-discovery
			throw new WeaveException("auto discover not implemented");
		} else {
			weaveClient = getInstance(account.getAccountParams().getApiVersion());
			weaveClient.init(account);
		}
		
		return weaveClient;
	}

	public static final WeaveClient getInstance(WeaveAccountParams params) throws WeaveException, InvalidStorageException {
		//return WeaveClient for given parameters
		WeaveClient weaveClient = null;

		if ( params.getApiVersion() == null ) {
			//auto-discovery
			throw new WeaveException("auto discover not implemented");
		} else {
			weaveClient = getInstance(params.getApiVersion());
			weaveClient.init(params);
		}
		
		return weaveClient;
	}

	public static final WeaveClient getInstance(ApiVersion apiVersion) throws WeaveException {
		//return WeaveClient for given storage context
		
		//TODO - Use reflection to detect and load additional storage versions
		
		WeaveClient wc = null;
		
		switch(apiVersion) {
		case v1_1:
			wc = new WeaveClientV1_1();
			break;
		case v1_5:
			wc = new WeaveClientV1_5();
			break;
		default:
			throw new WeaveException(String.format("Weave Sync API version '%s' not recognised", apiVersion));
		}
		
		return wc;
	}
	
}