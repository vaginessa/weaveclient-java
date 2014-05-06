package org.exfio.weave.client;

import org.exfio.weave.WeaveException;
import org.exfio.weave.resource.WeaveBasicObject;

public abstract class WeaveStorageContext {
	
	public abstract void init(WeaveClientParams params) throws WeaveException;

	public abstract WeaveHttpClient getHttpClient();

	public abstract WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String collection) throws WeaveException;

	public abstract String decrypt(String payload, String collection) throws WeaveException;
	
	public abstract String encrypt(String plaintext, String collection) throws WeaveException;
	
	public abstract WeaveBasicObject get(String collection, String id) throws WeaveException;
	
}
