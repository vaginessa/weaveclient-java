package org.exfio.weave.storage;

import java.net.URI;
import java.net.URISyntaxException;

import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.HawkAuthHeaderProvider;
import org.exfio.weave.WeaveException;
import org.exfio.weave.util.Log;

public class StorageV1_5 extends StorageContext {

	private String hawkid;
	private byte[] hawkkey;
	
	public StorageV1_5() throws WeaveException {
		super();
		hawkid  = null;
		hawkkey = null;
	}

	@Override
	public void init(StorageParams storageParams) throws WeaveException {
		StorageV1_5Params params = (StorageV1_5Params)storageParams;
		this.init(params.storageURL, params.hawkid, params.hawkkey);
	}

	public void init(String storageURL, String hawkid, byte[] hawkkey) throws WeaveException {
		URI uriStorageURL;
		try {
			uriStorageURL  = new URI(storageURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		this.init(uriStorageURL, hawkid, hawkkey);
	}
	
	public void init(URI storageURL, String hawkid, byte[] hawkkey) throws WeaveException {
		Log.getInstance().debug("StorageV1_5.init()");
		
		this.storageURL = storageURL;
		this.hawkid     = hawkid;
		this.hawkkey    = hawkkey;
		
		Log.getInstance().debug("Storage URL: " + this.storageURL.toString());
		
		
		//TODO - calculate skew
		AuthHeaderProvider provider = new HawkAuthHeaderProvider(this.hawkid, this.hawkkey, false, 0);
		httpClient.setAuthHeaderProvider(provider);
		
		/*
		//initialise pre-emptive authentication
		HttpHost host = new HttpHost(storageURL.getHost(), storageURL.getPort(), storageURL.getScheme());		
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        
        //TODO - create Credentials placeholder class
        //Credentials are not actually used, but need to be set
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("foo", "bar"));
        
        AuthCache authCache = new BasicAuthCache();
        HawkAuthScheme authScheme = new HawkAuthScheme(this.hawkid, this.hawkkey);
        authCache.put(host, authScheme);
                
		HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
		context.setAuthCache(authCache);
		
		httpClient.setContext(context);
		*/
	}
}
