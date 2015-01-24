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
package org.exfio.weave.storage;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;

public abstract class StorageContext {
	
	protected HttpClient httpClient = null;
	protected ApiVersion version    = null;
	
	public StorageContext() throws WeaveException {
		try {
			httpClient = HttpClient.getInstance();
		} catch (IOException e) {
			throw new WeaveException(e);
		}
	}
	
	public abstract void init(WeaveAccount account) throws WeaveException;
	
	public ApiVersion getApiVersion() { return version; }
	
	public abstract String generateWeaveID();
	
	public abstract Map<String, WeaveCollectionInfo> getInfoCollections(boolean getcount, boolean getinfo) throws WeaveException;

	public Map<String, WeaveCollectionInfo> getInfoCollections() throws WeaveException { return getInfoCollections(false, false); }

	public abstract WeaveBasicObject get(String collection, String id) throws WeaveException, NotFoundException;
	
	public abstract WeaveBasicObject get(String path) throws WeaveException, NotFoundException;

	public abstract WeaveBasicObject get(URI location) throws WeaveException, NotFoundException;

	public abstract String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException;

	public abstract WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException, NotFoundException;

	public abstract WeaveBasicObject[] getCollection(URI location) throws WeaveException, NotFoundException;

	public abstract Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException;
	
	public abstract Double put(String path, WeaveBasicObject wbo) throws WeaveException;

	public abstract Double put(URI location, WeaveBasicObject wbo) throws WeaveException;

	public abstract Double delete(String collection, String id) throws WeaveException;
	
	public abstract Double delete(URI location) throws WeaveException;
	
	public abstract Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException;


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
