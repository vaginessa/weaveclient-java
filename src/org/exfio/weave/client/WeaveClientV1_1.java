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

import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.account.legacy.FirefoxSyncLegacy;
import org.exfio.weave.account.legacy.FirefoxSyncLegacyParams;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.crypto.WeaveCryptoV5;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.StorageV1_1;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.storage.WeaveCollectionInfo;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.OSUtils;

public class WeaveClientV1_1 extends WeaveClient {
	
	/*
	public static final String KEY_CRYPTO_PATH       = "crypto/keys";
	public static final String KEY_CRYPTO_COLLECTION = "crypto";
	public static final String KEY_CRYPTO_ID         = "keys";
	public static final String KEY_META_PATH         = "meta/global";
	public static final String KEY_META_COLLECTION   = "meta";
	public static final String KEY_META_ID           = "global";
	*/
	
	private FirefoxSyncLegacyParams account;
	private WeaveAccount accountClient;
	private StorageContext storageClient;
	private WeaveCryptoV5 cryptoClient;
	
	public WeaveClientV1_1() {
		super();
		version        = StorageVersion.v5;
		account        = null;
		storageClient  = null;
		accountClient      = null;
	}

	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		
		//Store account params
		FirefoxSyncLegacyParams initParams = new FirefoxSyncLegacyParams();
		initParams.accountServer  = baseURL;
		initParams.user           = user;
		initParams.password       = password;
		initParams.syncKey        = syncKey;
		
		init(initParams);
	}

	@Override
	public void init(WeaveAccountParams params) throws WeaveException {
		account = (FirefoxSyncLegacyParams)params;

		//Initialise account, storage and crypto clients
		accountClient = new FirefoxSyncLegacy();
		accountClient.init(account);
		storageClient = new StorageV1_1();
		storageClient.init(accountClient);
		cryptoClient = new WeaveCryptoV5();
		cryptoClient.init(storageClient, accountClient.getMasterKeyPair());
		
		//TODO - is this check sufficiently robust? We really don't want to do this unintentionally!
		if ( !cryptoClient.isInitialised() ) {
			cryptoClient.initServer();
		}
	}

	@Override
	public void registerClient(WeaveClientRegistrationParams regParams) throws WeaveException {
		
		if ( regParams.clientId == null || regParams.clientId.isEmpty() ) {
			regParams.clientId = storageClient.generateWeaveID();
		}
			
		this.updateClientRecord(regParams, true);
	}

	/**
	 * updateClientRecord()
	 * 
	 * Update item in clients collection
	 *
	 */
	private void updateClientRecord(WeaveClientRegistrationParams regParams, boolean checkCollision) throws WeaveException {
	
		if (
			( regParams.clientId == null || regParams.clientId.isEmpty() )
			||	
			( regParams.clientName == null || regParams.clientName.isEmpty() )
		) {
			throw new WeaveException("clientId and clientName are required parameters");
		}
	
		if ( checkCollision ) {
			//Check to see if there is a collision with id
			WeaveBasicObject wboClient = null;
			try {
				wboClient = this.get("clients", regParams.clientId);
			} catch (NotFoundException e) {
				//Do nothing as we expect no collision
			}
			
			if ( wboClient != null ) {
				try {
					JSONObject jsonClient = wboClient.getPayloadAsJSONObject();
					throw new WeaveException(String.format("Weave client '%s' with id '%s' already exists", jsonClient.get("name"), wboClient.getId()));
				} catch (ParseException e) {
					Log.getInstance().warn(String.format("Error parsing client payload for id %s", wboClient.getId()));
					throw new WeaveException(String.format("Weave client with id '%s' already exists", wboClient.getId()));				
				}
			}
		}
		
		if ( regParams.clientType == null || regParams.clientType.isEmpty() ) {
			regParams.clientType = OSUtils.getType();
		}
		
		if ( regParams.clientOS == null || regParams.clientOS.isEmpty() ) {
			if (OSUtils.isAndroid()) {
				regParams.clientOS = "Android";
			} else if (OSUtils.isWindows()) {
				regParams.clientOS = "WINNT";
			} else if (OSUtils.isOSX()) {
				regParams.clientOS = "Darwin";
			} else if (OSUtils.isLinux()) {
				regParams.clientOS = "Linux";
			} else if (OSUtils.isUnix()) {
				regParams.clientOS = "Unix";
			}
		}
	
		JSONObject jsonClient = new JSONObject();
		jsonClient.put("name", regParams.clientName);
		jsonClient.put("type", regParams.clientType);
		
		jsonClient.put("os", regParams.clientOS);
		jsonClient.put("appPackage", Constants.APP_PACKAGE);
		jsonClient.put("application", Constants.APP_NAME);		
	
		//TODO - determine device and form factor
		if ( regParams.clientDevice != null && !regParams.clientDevice.isEmpty() ) {
			jsonClient.put("device", regParams.clientDevice);
		}
		if ( regParams.clientFormFactor != null && !regParams.clientFormFactor.isEmpty() ) {
			jsonClient.put("formFactor", regParams.clientFormFactor);
		}
		
		WeaveBasicObject wboClient = new WeaveBasicObject(regParams.clientId);
		wboClient.setPayload(jsonClient.toJSONString());
		
		this.put("clients", wboClient.getId(), wboClient);
	}

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
	
	public boolean isAuthorised() {
		return ( account.syncKey != null && account.syncKey.length() > 0 ); 
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

	public WeaveCollectionInfo getCollectionInfo(String collection, boolean getcount, boolean getusage) throws WeaveException, NotFoundException {
		Map<String, WeaveCollectionInfo> wcols = this.storageClient.getInfoCollections(getcount, getusage);
		if ( !wcols.containsKey(collection) ) {
			throw new NotFoundException(String.format("Collection '%s' not found", collection));
		}
		return wcols.get(collection);
	}

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

	public Double delete(String collection, String id) throws WeaveException {
		return this.storageClient.delete(collection, id);
	}

	public Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return this.storageClient.deleteCollection(collection, ids, older, newer, limit, offset, sort);
	}

}
