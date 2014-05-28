/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package org.exfio.weave.client;

import java.io.IOException;
import java.net.URI;

import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;

public abstract class WeaveApiClient {
	
	private HttpClient httpClient = HttpClient.getInstance();
	
	public static final WeaveApiClient getApiClient(WeaveClient.ApiVersion apiVersion) throws WeaveException {
		//return WeaveClient for given storage context
		
		WeaveApiClient context = null;
		
		switch(apiVersion) {
		case v1_1:
			context = new WeaveApiClientV1_1();
			break;
		default:
			throw new WeaveException(String.format("Weave API version '%s' not recognised", apiVersion));
		}
		
		return context;
	}

	public abstract void init(String baseURL, String user, String password) throws WeaveException;
				
	public abstract WeaveBasicObject get(String collection, String id) throws WeaveException;
	
	public abstract WeaveBasicObject get(String path) throws WeaveException;

	public abstract WeaveBasicObject get(URI location) throws WeaveException;

	public abstract WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException;

	public abstract WeaveBasicObject[] getCollection(URI location) throws WeaveException;

	public abstract Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException;

	public abstract Double put(URI location, WeaveBasicObject wbo) throws WeaveException;

	public abstract void delete(String collection, String id) throws WeaveException;
	
	public abstract void delete(URI location) throws WeaveException;

	public void lock() {
		httpClient.lock();
	}
	
	public void unlock() {
		httpClient.unlock();
	}
	
	public void close() throws IOException {
		httpClient.close();
	}
}
