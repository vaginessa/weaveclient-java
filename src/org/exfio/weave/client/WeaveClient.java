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
import java.util.Map;

import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.crypto.WeaveSyncV5Crypto;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.storage.WeaveCollectionInfo;
import org.json.simple.parser.ParseException;

public abstract class WeaveClient {
	
	protected StorageVersion version = null;
	protected WeaveAccountParams account = null;
	protected StorageContext storageClient = null;
	protected WeaveSyncV5Crypto cryptoClient = null;
	
	public abstract void init(WeaveAccountParams params) throws WeaveException;

	public abstract void registerClient(WeaveClientRegistrationParams params) throws WeaveException;

	
	public void initServer() throws WeaveException {
		this.cryptoClient.initServer();
	}

	public StorageContext getApiClient() {
		return storageClient;
	}
	
	public WeaveAccountParams getClientParams() {
		return account;
	}

	public boolean isInitialised() throws WeaveException {
		return cryptoClient.isInitialised();		
	}

	public String generateWeaveID() {
		return storageClient.generateWeaveID();	
	}
	
	public WeaveBasicObject get(String collection, String id, boolean decrypt) throws WeaveException, NotFoundException {
		WeaveBasicObject wbo = this.storageClient.get(collection, id);
		if ( decrypt ) {
			try {
				if ( this.cryptoClient.isEncrypted(wbo) ) {
					wbo = this.cryptoClient.decryptWeaveBasicObject(wbo, collection);
				} else {
					throw new WeaveException("Weave Basic Object payload not encrypted");
				}
			} catch (ParseException e) {
				throw new WeaveException(e);
			}
		}
		return wbo;
	}

	public String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return this.storageClient.getCollectionIds(collection, ids, older, newer, index_above, index_below, limit, offset, sort);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean decrypt) throws WeaveException, NotFoundException {
		WeaveBasicObject[] colWbo = this.storageClient.getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format);
		if ( decrypt ) {
			try {
				for (int i = 0; i < colWbo.length; i++) {
					if ( this.cryptoClient.isEncrypted(colWbo[i]) ) {
						colWbo[i] = this.cryptoClient.decryptWeaveBasicObject(colWbo[i], collection);
					} else {
						throw new WeaveException("Weave Basic Object payload not encrypted");
					}
				}
			} catch (ParseException e) {
				throw new WeaveException(e);
			}
		}
		return colWbo;
	}

	public WeaveCollectionInfo getCollectionInfo(String collection) throws WeaveException, NotFoundException { return getCollectionInfo(collection, false, false); }

	public WeaveCollectionInfo getCollectionInfo(String collection, boolean getcount, boolean getusage) throws WeaveException, NotFoundException {
		Map<String, WeaveCollectionInfo> wcols = this.storageClient.getInfoCollections(getcount, getusage);
		if ( !wcols.containsKey(collection) ) {
			throw new NotFoundException(String.format("Collection '%s' not found", collection));
		}
		return wcols.get(collection);
	}

	public Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException { return put(collection, id, wbo, true); }
	
	public Double put(String collection, String id, WeaveBasicObject wbo, boolean encrypt) throws WeaveException {
		if ( encrypt ) {
			try {
				if ( !this.cryptoClient.isEncrypted(wbo) ) {
					wbo = this.cryptoClient.encryptWeaveBasicObject(wbo, collection);
				} else {
					throw new WeaveException("Weave Basic Object payload already encrypted");
				}
			} catch (ParseException e) {
				throw new WeaveException(e);
			}
		}
		return this.storageClient.put(collection, id, wbo);
	}

	public Double delete(String collection, String id) throws NotFoundException, WeaveException {
		return this.storageClient.delete(collection, id);
	}

	public Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return this.storageClient.deleteCollection(collection, ids, older, newer, limit, offset, sort);
	}
	
	public StorageVersion getStorageVersion() { return version; }

	public ApiVersion getApiVersion() { return getApiClient().getApiVersion(); }

	public WeaveBasicObject get(String collection, String id) throws WeaveException, NotFoundException { return get(collection, id, true); }

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException, NotFoundException {
		return getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format, true);
	}

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
