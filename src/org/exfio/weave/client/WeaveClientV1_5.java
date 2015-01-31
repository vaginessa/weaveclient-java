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

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.account.fxa.FxAccount;
import org.exfio.weave.account.fxa.FxAccountParams;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.crypto.WeaveSyncV5Crypto;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.StorageV1_5;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.OSUtils;

public class WeaveClientV1_5 extends WeaveClient {
		
	private WeaveAccount accountClient;
	
	public WeaveClientV1_5() {
		super();
		version        = StorageVersion.v5;
		account        = null;
		storageClient  = null;
		accountClient      = null;
	}

	public void init(String accountServer, String tokenServer, String user, String password) throws WeaveException {
		
		FxAccountParams initParams = new FxAccountParams();
		initParams.accountServer  = accountServer;
		initParams.tokenServer    = tokenServer;
		initParams.user           = user;
		initParams.password       = password;
		
		init(initParams);
	}

	@Override
	public void init(WeaveAccountParams params) throws WeaveException {
		account = (FxAccountParams)params;

		//Initialise account, storage and crypto clients
		accountClient = new FxAccount();
		accountClient.init(account);
		storageClient = new StorageV1_5();
		storageClient.init(accountClient);
		
		//FIXME - check storageVersion
		cryptoClient = new WeaveSyncV5Crypto();
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
	@SuppressWarnings("unchecked")
	private void updateClientRecord(WeaveClientRegistrationParams regParams, boolean checkCollision) throws WeaveException {
	
		//FIXME - update for v1.5 storage API
		
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

	public boolean isAuthorised() {
		throw new AssertionError("Not yet implemented");
	}
}
