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
package org.exfio.weave.crypto;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.security.SecureRandom;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.crypto.PayloadCipher;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;

public class WeaveSyncV5Crypto {
	
	public static final String KEY_CRYPTO_PATH       = "crypto/keys";
	public static final String KEY_CRYPTO_COLLECTION = "crypto";
	public static final String KEY_CRYPTO_ID         = "keys";	
	public static final String KEY_META_PATH         = "meta/global";
	public static final String KEY_META_COLLECTION   = "meta";
	public static final String KEY_META_ID           = "global";
	
	private StorageVersion version = StorageVersion.v5;
	
	private StorageContext storageClient;
	private WeaveKeyPair privateKey;
	private Map<String, WeaveKeyPair> bulkKeys;

	public WeaveSyncV5Crypto() {
		storageClient  = null;
		privateKey     = null;
		bulkKeys       = null;
	}
	
	public void init(StorageContext storageClient, WeaveKeyPair keyPair) throws WeaveException {
		this.storageClient   = storageClient;
		this.privateKey      = keyPair;
		this.bulkKeys        = null;		
	}

	@SuppressWarnings("unchecked")
	public void initServer() throws WeaveException {
		this.bulkKeys = null;

		//1. Build and publish meta/global WBO
		JSONObject metaObject = new JSONObject();
		metaObject.put("syncID", generateWeaveID());
		metaObject.put("storageVersion", WeaveClientFactory.storageVersionToString(this.version));
		metaObject.put("engines", new JSONObject());

		WeaveBasicObject wboMeta = new WeaveBasicObject(KEY_META_ID, null, null, null, metaObject.toJSONString());
		
		//Note meta/global is NOT encrypted
		this.storageClient.put(KEY_META_COLLECTION, KEY_META_ID, wboMeta);
		
		//2. Generate default bulk key bundle
		WeaveKeyPair defaultKeyPair = generateWeaveKeyPair();
		
		//4. Build and publish crypto/keys WBO
		JSONObject cryptoObject = new JSONObject();
		cryptoObject.put("collection", KEY_CRYPTO_COLLECTION);
		cryptoObject.put("collections", new JSONObject());

		JSONArray dkpArray = new JSONArray();
		dkpArray.add(Base64.encodeBase64String(defaultKeyPair.cryptKey));
		dkpArray.add(Base64.encodeBase64String(defaultKeyPair.hmacKey));
		cryptoObject.put("default", dkpArray);

		WeaveBasicObject wboCrypto = new WeaveBasicObject(KEY_CRYPTO_ID, null, null, null, cryptoObject.toJSONString());

		//Encrypt payload with private keypair
		wboCrypto = encryptWeaveBasicObject(wboCrypto, null);

		storageClient.put(KEY_CRYPTO_PATH, wboCrypto);		
	}
	
	public StorageContext getApiClient() {
		return storageClient;
	}
	
	public String generateWeaveID() {
		return storageClient.generateWeaveID();
	}

	public boolean isInitialised() throws WeaveException {
		Log.getInstance().debug("isInitialised()");
		
		//Default to true as false negative could result in reset
		boolean meta = true;
		boolean keys = true;
		
		@SuppressWarnings("unused")
		WeaveBasicObject wboMeta = null;
		try {
			wboMeta = storageClient.get(KEY_META_PATH);
		} catch (NotFoundException e) {
			meta = false;
		}

		@SuppressWarnings("unused")
		WeaveBasicObject wboKeys = null;
		try {
			wboKeys = storageClient.get(KEY_CRYPTO_PATH) ;
		} catch (NotFoundException e) {
			keys = false;
		}
		
		//Only return false if both meta/global and crypto/keys objects are missing
		return (meta || keys);
	}
	
	private WeaveKeyPair generateWeaveKeyPair() {
        SecureRandom rnd = new SecureRandom();
        WeaveKeyPair keyPair = new WeaveKeyPair();
        keyPair.cryptKey = rnd.generateSeed(32);
        keyPair.hmacKey = rnd.generateSeed(32);
        return keyPair;
	}
    
	/**
      Given a bulk key label, pull the key down from the network,
      and decrypt it using my private key.  Then store the key
      into self storage for later decrypt operations."""
	 */
	private WeaveKeyPair getBulkKeyPair(String collection) throws WeaveException {
		Log.getInstance().debug("getBulkKeyPair()");
		
		if ( this.bulkKeys == null ) {
			Log.getInstance().info( "Fetching bulk keys from server");

            WeaveBasicObject res = null;
            try {
            	res = storageClient.get(KEY_CRYPTO_PATH);
            } catch (NotFoundException e) {
            	throw new WeaveException(KEY_CRYPTO_PATH + " not found " + e.getMessage());
            }

            // Recursively call decrypt to extract key data
            String payload = this.decrypt(res.getPayload(), null);
            
            // Parse JSONUtils encoded payload
            JSONParser parser = new JSONParser();
            JSONObject keyData = null; 
            		
            try {
            	keyData = (JSONObject)parser.parse(payload);  
            } catch (ParseException e) {
            	throw new WeaveException(e);
            }

    		this.bulkKeys   = new HashMap<String, WeaveKeyPair>();

    		//Get default key pair
    		JSONArray defaultKey = (JSONArray)keyData.get("default");
    		
        	WeaveKeyPair keyPair = new WeaveKeyPair();
        	keyPair.cryptKey = Base64.decodeBase64((String)defaultKey.get(0));
        	keyPair.hmacKey  = Base64.decodeBase64((String)defaultKey.get(1));
            this.bulkKeys.put("default", keyPair);
    		
            //Get collection key pairs
            JSONObject colKeys = (JSONObject)keyData.get("collections");
            
    	    Iterator<?> it = colKeys.entrySet().iterator();
    	    while (it.hasNext()) {
    	        Map.Entry<?, ?> pairs = (Map.Entry<?, ?>)it.next();
            	JSONArray bulkKey = (JSONArray)pairs.getValue();
            	
            	WeaveKeyPair bulkKeyPair = new WeaveKeyPair();
            	bulkKeyPair.cryptKey = Base64.decodeBase64((String)bulkKey.get(0));
            	bulkKeyPair.hmacKey  = Base64.decodeBase64((String)bulkKey.get(1));
                this.bulkKeys.put((String)pairs.getKey(), bulkKeyPair);
            }
            
            Log.getInstance().info( String.format("Successfully decrypted bulk key for %s", collection));
		}

        if ( this.bulkKeys.containsKey(collection) )  {
        	return this.bulkKeys.get(collection);
        } else if ( this.bulkKeys.containsKey("default") ) {
        	Log.getInstance().info( String.format("No key found for %s, using default", collection));
        	return this.bulkKeys.get("default");        	
        } else {
        	throw new WeaveException("No default key found");
        }
	}
	
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String collection) throws WeaveException {
		try {
			if ( !isEncrypted(wbo) ) {
				throw new WeaveException("Weave Basic Object already decrypted");
			}
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		String payload = decrypt(wbo.getPayload(), collection);
		return new WeaveBasicObject(wbo.getId(), wbo.getModified(), wbo.getSortindex(), wbo.getTtl(), payload);
	}
	
	public String decrypt(String payload, String collection) throws WeaveException {
		
        WeaveKeyPair keyPair = null;
        
        if ( collection == null ) {
        	Log.getInstance().info("Decrypting data record using sync key");
        	
        	keyPair = this.privateKey;
        		
        } else {
        	Log.getInstance().info(String.format("Decrypting data record using bulk key %s", collection));

        	keyPair = this.getBulkKeyPair(collection);
        }

        PayloadCipher cipher = new PayloadCipher();
        
        return cipher.decrypt(payload, keyPair);
	}

	public WeaveBasicObject encryptWeaveBasicObject(WeaveBasicObject wbo, String collection) throws WeaveException {
		try {
			if ( isEncrypted(wbo) ) {
				throw new WeaveException("Weave Basic Object already encrypted");
			}
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		String payload = encrypt(wbo.getPayload(), collection);
		return new WeaveBasicObject(wbo.getId(), wbo.getModified(), wbo.getSortindex(), wbo.getTtl(), payload);
	}

	/**
	 * encrypt()
	 *
	 * Given a plaintext object, encrypt it and return the ciphertext value.
	 */
	public String encrypt(String plaintext, String collection) throws WeaveException {		
		Log.getInstance().debug( "encrypt()");
		Log.getInstance().debug( "plaintext:\n" + plaintext);
	        

		WeaveKeyPair keyPair = null;
		
		if ( collection == null ) {
			Log.getInstance().info( "Encrypting data record using sync key");

        	keyPair = this.privateKey;
	                
		} else {
			Log.getInstance().info( String.format("Encrypting data record using bulk key %s", collection));

			keyPair = this.getBulkKeyPair(collection);
		}
		
		PayloadCipher cipher = new PayloadCipher();
		
		return cipher.encrypt(plaintext, keyPair);
	}
	
	public boolean isEncrypted(WeaveBasicObject wbo) throws ParseException {
		//Determine if WBO is encrypted or not
		JSONObject jsonPayload = wbo.getPayloadAsJSONObject();
		return ( jsonPayload.containsKey("ciphertext") && jsonPayload.containsKey("IV") && jsonPayload.containsKey("hmac") );
	}
}
