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
import java.util.Map;

import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.client.WeaveClient.ApiVersion;

public abstract class WeaveApiClient {
	
	protected HttpClient httpClient = null;
	protected ApiVersion version    = null;
	
	public WeaveApiClient() throws WeaveException {
		try {
			httpClient = HttpClient.getInstance();
		} catch (IOException e) {
			throw new WeaveException(e);
		}
	}
	
	public static final WeaveApiClient getInstance(ApiVersion apiVersion) throws WeaveException {
		//return WeaveClient for given storage context

		WeaveApiClient apiClient = null;
		
		switch(apiVersion) {
		case v1_1:
			apiClient  = new WeaveApiClientV1_1();
			break;
		default:
			throw new WeaveException(String.format("Weave API version '%s' not recognised", apiVersion));
		}
		
		return apiClient;
	}

	public ApiVersion getApiVersion() { return version; }
	
	public abstract void init(String baseURL, String user, String password) throws WeaveException;
				
	public abstract Map<String, WeaveCollectionInfo> getInfoCollections(boolean getcount, boolean getinfo) throws WeaveException;

	public Map<String, WeaveCollectionInfo> getInfoCollections() throws WeaveException { return getInfoCollections(false, false); }

	public abstract WeaveBasicObject get(String collection, String id) throws WeaveException;
	
	public abstract WeaveBasicObject get(String path) throws WeaveException;

	public abstract WeaveBasicObject get(URI location) throws WeaveException;

	public abstract String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException;

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
