package org.exfio.weave.client;


import lombok.Getter;
import lombok.Setter;

import org.json.simple.JSONObject;
import org.exfio.weave.InvalidStorageException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientV5;

public class WeaveClientFactory {
	
	public static enum StorageVersion {
		v5;
	}

	public static enum ApiVersion {
		v1_1;
	}

	
	@Getter @Setter
	private static StorageVersion defaultStorageVersion = StorageVersion.v5;
	
	public static String storageVersionToString(StorageVersion version) {
		String versionString = null;
		switch (version) {
		case v5:
			versionString = "5";
			break;
		default:
		}
		return versionString;
	}

	public static StorageVersion stringToStorageVersion(String version) {
		StorageVersion storageVersion = null;
		if ( version.equals("5") ) {
			storageVersion = StorageVersion.v5;
		}
		return storageVersion;
	}

	public static final StorageVersion autoDiscoverStorageVersion(AccountParams adParams) throws WeaveException, InvalidStorageException {
		StorageVersion storageVersion = null;
		
		//Initialise registration and storage clients with account details
		AccountApi regClient = new RegistrationApiV1_0();
		regClient.init(adParams.baseURL, adParams.user, adParams.password);
		StorageApi storageClient = new StorageApiV1_1();
		storageClient.init(regClient.getStorageUrl(), adParams.user, adParams.password);
		
		WeaveBasicObject wbo = null;
		try {
			wbo = storageClient.get(WeaveClientV5.KEY_META_PATH);
		} catch (NotFoundException e) {
			//Storage may be an uninitialised
			throw new InvalidStorageException(String.format("%s not found - %s", WeaveClientV5.KEY_META_PATH, e.getMessage()));
		}
		JSONObject jsonPayload = null;
		
		try {
			jsonPayload = wbo.getPayloadAsJSONObject();
		} catch (org.json.simple.parser.ParseException e) {
			throw new WeaveException(e);
		}
		
		if ( !jsonPayload.containsKey("storageVersion") ) {
			throw new WeaveException(String.format("Storage version not found in %s record", WeaveClientV5.KEY_META_PATH));
		}

		Long version = null;
		if (jsonPayload.get("storageVersion").getClass().equals(Long.class)) {
			version = (Long)jsonPayload.get("storageVersion");
		} else {
			version = Long.parseLong((String)jsonPayload.get("storageVersion"));
		}
		
		if ( version == 5 ) {
			storageVersion = StorageVersion.v5;
		} else {
			throw new WeaveException(String.format("Storage version '%s' not supported", version));
		}
		
		return storageVersion;
	}

	public static final WeaveClient getInstance(AccountParams params) throws WeaveException, InvalidStorageException {
		//return WeaveClient for given parameters
		WeaveClient weaveClient = null;
		
		if ( params.getStorageVersion() == null ) {
			//auto-discovery
			StorageVersion storageVersion = autoDiscoverStorageVersion(params);
			weaveClient = getInstance(storageVersion);
		} else {
			weaveClient = getInstance(params.getStorageVersion());
			weaveClient.init(params);
		}
		
		return weaveClient;
	}

	public static final WeaveClient getInstance(StorageVersion storageVersion) throws WeaveException {
		//return WeaveClient for given storage context
		
		//TODO - Use reflection to detect and load additional storage versions
		
		WeaveClient wc = null;
		
		switch(storageVersion) {
		case v5:
			wc = new WeaveClientV5();
			break;
		default:
			throw new WeaveException(String.format("Weave storage version '%s' not recognised", storageVersion));
		}
		
		return wc;
	}
	
}