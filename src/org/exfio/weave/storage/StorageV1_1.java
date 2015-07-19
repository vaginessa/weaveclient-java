package org.exfio.weave.storage;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public class StorageV1_1 extends StorageContext {
	
	private String user;
	private String password;
	
	
	public StorageV1_1() throws WeaveException {
		super();
		version    = ApiVersion.v1_1;
		storageURL = null;
		user       = null;
		password   = null;
	}
	
	@Override
	public void init(StorageParams storageParams) throws WeaveException {
		StorageV1_1Params params = (StorageV1_1Params)storageParams;		
		this.init(params.storageURL, params.user, params.password);
	}
	
	public void init(String storageURL, String user, String password) throws WeaveException {
		URI uriStorageURL;
		try {
			uriStorageURL  = new URI(String.format("%s/1.1/%s/", storageURL, user));			
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		this.init(uriStorageURL, user, password);
	}
	
	public void init(URI storageURL, String user, String password) throws WeaveException {

		this.storageURL = storageURL;
		this.user       = user;
		this.password   = password;
		
		//Set HTTP Auth credentials
		HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(new BasicCredentialsProvider());				
		context.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(this.user, this.password));
		
		//initialise pre-emptive authentication
		HttpHost host = new HttpHost(storageURL.getHost(), storageURL.getPort(), storageURL.getScheme());
		AuthCache authCache = context.getAuthCache();
		if (authCache == null)
			authCache = new BasicAuthCache();
		authCache.put(host, new BasicScheme());
		context.setAuthCache(authCache);
		
		httpClient.setContext(context);
	}


}
