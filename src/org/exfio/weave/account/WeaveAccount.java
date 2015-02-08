package org.exfio.weave.account;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.storage.StorageParams;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.crypto.WeaveKeyPair;

public abstract class WeaveAccount {
	
	public static final String KEY_ACCOUNT_CONFIG_APIVERSION   = "apiversion";
	public static final String KEY_ACCOUNT_CONFIG_SERVER       = "server";
	public static final String KEY_ACCOUNT_CONFIG_USERNAME     = "username";
	public static final String KEY_ACCOUNT_CONFIG_EMAIL        = "email";

	protected HttpClient httpClient = null;
	protected ApiVersion version    = null;
	
	public WeaveAccount() {
		try {
			httpClient = HttpClient.getInstance();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public abstract void init(WeaveAccountParams params) throws WeaveException;

	public void init(Properties clientProp, String password) throws WeaveException {
		WeaveAccountParams params = this.propertiesToAccountParams(clientProp);
		params.password = password;
		this.init(params);
	}
	
	public abstract String getStatus();

	public ApiVersion getApiVersion() { return version; }
	
	public abstract void createAccount(WeaveAccountParams params) throws WeaveException;
	
	public abstract WeaveAccountParams getAccountParams();

	public abstract StorageParams getStorageParams() throws WeaveException;

	public abstract URI getStorageUrl() throws WeaveException;

	public abstract WeaveKeyPair getMasterKeyPair() throws WeaveException;

	public abstract Properties accountParamsToProperties(WeaveAccountParams params);

	public Properties accountParamsToProperties() {
		return this.accountParamsToProperties(this.getAccountParams());
	}

	public abstract WeaveAccountParams propertiesToAccountParams(Properties prop) throws WeaveException;
}
