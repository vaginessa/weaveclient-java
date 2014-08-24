package org.exfio.weave.client;

import java.io.IOException;
import java.net.URI;

import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public abstract class AccountApi {
	
	protected HttpClient httpClient = null;
	protected ApiVersion version    = null;
	
	public AccountApi() throws WeaveException {
		try {
			httpClient = HttpClient.getInstance();
		} catch (IOException e) {
			throw new WeaveException(e);
		}
	}

	public abstract void init(String baseURL, String user, String password) throws WeaveException;

	public ApiVersion getApiVersion() { return version; }
	
	public abstract boolean register(String baseURL, String user, String password, String email) throws WeaveException;
	
	public abstract URI getStorageUrl() throws WeaveException;
				
}
