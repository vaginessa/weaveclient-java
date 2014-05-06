package org.exfio.weave.client;

import java.io.IOException;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveStorageContext;
import org.exfio.weave.client.WeaveStorageV5;
import org.exfio.weave.resource.WeaveBasicObject;

public class WeaveClient {

	private WeaveStorageContext ws;
	
	public enum Version {
		v5;
	}
	
	protected WeaveClient(WeaveStorageContext ws) {
		this.ws = ws;
	}
	
	public static final WeaveClient getInstance(Version version) throws WeaveException {
		//return WeaveClient for given storage context
		
		WeaveStorageContext context = null;
		
		switch(version) {
		case v5:
			context = new WeaveStorageV5();
			break;
		default:
			throw new WeaveException(String.format("Weave storage context '%s' not recognised", version));
		}
		
		return new WeaveClient(context);
	}

	public void init(WeaveClientParams params) throws WeaveException {
		ws.init(params);
	}
	
	public void lock() {
		ws.getHttpClient().lock(); 
	}
	
	public void unlock() {
		ws.getHttpClient().unlock();
	}
	
	public void close() throws IOException {
		ws.getHttpClient().close();
	}

	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo) throws WeaveException {
		return decryptWeaveBasicObject(wbo, null);
	}
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String keyLabel) throws WeaveException {
		return ws.decryptWeaveBasicObject(wbo, keyLabel);
	}
	public WeaveBasicObject get(String collection, String id) throws WeaveException {
		return ws.get(collection, id);
	}
}
