/*******************************************************************************
 * Copyright (c) 2014 Gerry Healy <nickel_chrome@mac.com>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Gerry Healy <nickel_chrome@mac.com> - Initial implementation
 ******************************************************************************/
package org.exfio.weave.client;

import java.io.IOException;

import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.storage.WeaveCollectionInfo;

public abstract class WeaveClient {
	
	protected StorageVersion version = null;
	
	public abstract void init(WeaveAccountParams params) throws WeaveException;

	public abstract void registerClient(WeaveClientRegistrationParams params) throws WeaveException;

	public abstract void initServer() throws WeaveException;

	public StorageVersion getStorageVersion() { return version; }

	public ApiVersion getApiVersion() { return getApiClient().getApiVersion(); }

	public abstract StorageContext getApiClient();
	
	public abstract WeaveAccountParams getClientParams();

	public abstract boolean isInitialised() throws WeaveException;

	public abstract boolean isAuthorised();

	public abstract String generateWeaveID();
	
	public abstract WeaveBasicObject get(String collection, String id, boolean decrypt) throws WeaveException, NotFoundException;
	
	public WeaveBasicObject get(String collection, String id) throws WeaveException, NotFoundException { return get(collection, id, true); }

	public abstract String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException;

	public abstract WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean decrypt) throws WeaveException, NotFoundException;

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException, NotFoundException {
		return getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format, true);
	}

	public abstract WeaveCollectionInfo getCollectionInfo(String collection, boolean getcount, boolean getusage) throws WeaveException, NotFoundException;

	public WeaveCollectionInfo getCollectionInfo(String collection) throws WeaveException, NotFoundException { return getCollectionInfo(collection, false, false); }

	public abstract Double put(String collection, String id, WeaveBasicObject wbo, boolean encrypt) throws WeaveException;

	public Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException { return put(collection, id, wbo, true); }

	public abstract Double delete(String collection, String id) throws WeaveException, NotFoundException;

	public abstract Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException;
	
	public void lock() {
		getApiClient().lock();
	}
	
	public void unlock() {
		getApiClient().unlock();
	}
	
	public void close() throws IOException {
		getApiClient().close();
	}
}
