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
import java.util.HashMap;
import java.util.Iterator;
import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.client.AccountParams;
import org.exfio.weave.crypto.PayloadCipher;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Base64;

public class WeaveClientV5 extends WeaveClient {
	
	public static final String KEY_CRYPTO_PATH       = "crypto/keys";
	public static final String KEY_CRYPTO_COLLECTION = "crypto";
	public static final String KEY_CRYPTO_ID         = "keys";
	public static final String KEY_META_PATH         = "meta/global";
	public static final String KEY_META_COLLECTION   = "meta";
	public static final String KEY_META_ID           = "global";
	
	private WeaveClientV5Params account;
	private StorageApi storageClient;
	private AccountApi regClient;
	private WeaveKeyPair privateKey;
	private Map<String, WeaveKeyPair> bulkKeys;

	public WeaveClientV5() {
		super();
		version        = StorageVersion.v5;
		account        = null;
		storageClient  = null;
		regClient      = null;
		privateKey     = null;
		bulkKeys       = null;
	}

	public void register(AccountParams params) throws WeaveException {
		RegistrationParams p = (RegistrationParams)params;
		register(p.baseURL, p.user, p.password, p.email);
	}

	public void register(String baseURL, String user, String password, String email) throws WeaveException {
		this.privateKey      = null;
		this.bulkKeys        = null;

		//Store account params
		account = new WeaveClientV5Params();
		account.baseURL  = baseURL;
		account.user     = user;
		account.password = password;

		//TODO - handle captcha
		
		//Register new account
		regClient = new RegistrationApiV1_0();		
		regClient.register(baseURL, user, password, email);
				
		//Initialise storage client with account details
		storageClient = new StorageApiV1_1();
		storageClient.init(regClient.getStorageUrl(), user, password);

		//Generate new synckey and initialise server meta data
		initServer();
	}
	
	@SuppressWarnings("unchecked")
	public void initServer() throws WeaveException {
		this.privateKey      = null;
		this.bulkKeys        = null;

		//1. Build and publish meta/global WBO
		JSONObject metaObject = new JSONObject();
		metaObject.put("syncID", generateWeaveID());
		metaObject.put("storageVersion", WeaveClientFactory.storageVersionToString(this.getStorageVersion()));
		metaObject.put("engines", new JSONObject());

		WeaveBasicObject wboMeta = new WeaveBasicObject(KEY_META_ID, null, null, null, metaObject.toJSONString());
		
		//Note meta/global is NOT encrypted
		put(KEY_META_COLLECTION, KEY_META_ID, wboMeta, false);
		
		//2. Generate sync key
        SecureRandom rnd = new SecureRandom();
        byte[] syncKeyBin = rnd.generateSeed(16);
        
		Base32 b32codec = new Base32();
        String syncKeyB32 = b32codec.encodeToString(syncKeyBin);
        
		// Remove dash chars, convert to uppercase and translate L and O to 8 and 9
		account.syncKey = syncKeyB32.toUpperCase()
								.replace('L', '8')
								.replace('O', '9')
								.replaceAll("=", "");

		Log.getInstance().debug( String.format("generated sync key: %s", account.syncKey));

		//3. Generate default bulk key bundle
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

	
	public void init(AccountParams params) throws WeaveException {
		WeaveClientV5Params v5params = (WeaveClientV5Params)params;
		init(v5params.baseURL, v5params.user, v5params.password, v5params.syncKey);
	}

	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		//this.user            = user;
		//this.syncKey         = syncKey;
		this.privateKey      = null;
		this.bulkKeys        = null;
		
		//Initialise registration and storage clients with account details
		regClient = new RegistrationApiV1_0();
		regClient.init(baseURL, user, password);
		storageClient = new StorageApiV1_1();
		storageClient.init(regClient.getStorageUrl(), user, password);
		
		//Store account params
		account = new WeaveClientV5Params();
		account.baseURL  = baseURL;
		account.user     = user;
		account.password = password;
		account.syncKey  = syncKey;
	}

	public StorageApi getApiClient() {
		return storageClient;
	}
	
	public AccountParams getClientParams() {
		return account;
	}

	public String generateWeaveID() {
        SecureRandom rnd = new SecureRandom();
        byte[] weaveID = rnd.generateSeed(9);
        return Base64.encodeToString(weaveID, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
	}

	public boolean isInitialised() throws WeaveException {
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

		/*
		@SuppressWarnings("unused")
		WeaveBasicObject wboKeys = null;
		try {
			wboKeys = storageClient.get(KEY_CRYPTO_PATH) ;
		} catch (NotFoundException e) {
			keys = false;
		}
		*/
		
		return (meta && keys);
	}
	
	public boolean isAuthorised() {
		return ( account.syncKey != null && account.syncKey.length() > 0 ); 
	}
	
	private WeaveKeyPair generateWeaveKeyPair() {
        SecureRandom rnd = new SecureRandom();
        WeaveKeyPair keyPair = new WeaveKeyPair();
        keyPair.cryptKey = rnd.generateSeed(32);
        keyPair.hmacKey = rnd.generateSeed(32);
        return keyPair;
	}

	/**
     Fetch the private key for the user and storage context
     provided to this object, and decrypt the private key
     by using my passphrase.  Store the private key in internal
     storage for later use.
	 */
	private WeaveKeyPair getPrivateKeyPair() throws NoSuchAlgorithmException, InvalidKeyException {
		Log.getInstance().debug("getPrivateKeyPair()");

		if ( this.privateKey == null ) {

			// Generate key pair using SHA-256 HMAC-based HKDF of sync key
			// See https://docs.services.mozilla.com/sync/storageformat5.html#the-sync-key

			// Remove dash chars, convert to uppercase and translate 8 and 9 to L and O
			String syncKeyB32 = account.syncKey.toUpperCase()
											.replace('8', 'L')
											.replace('9', 'O')
											.replaceAll("-", "");

			Log.getInstance().debug( String.format("normalised sync key: %s",  syncKeyB32));

			// Pad base32 string to multiple of 8 chars (40 bits)
			if ( (syncKeyB32.length() % 8) > 0 ) {
				int paddedLength = syncKeyB32.length() + 8 - (syncKeyB32.length() % 8);
				syncKeyB32 = StringUtils.rightPad(syncKeyB32, paddedLength, '=');
			}

			Log.getInstance().debug( String.format("padded sync key: %s",  syncKeyB32));

			Base32 b32codec = new Base32();
			byte[] syncKeyBin = b32codec.decode(syncKeyB32);

			String keyInfo = "Sync-AES_256_CBC-HMAC256" + account.user;

			// For testing only
			//syncKeyBin = Hex.decodeHexString("c71aa7cbd8b82a8ff6eda55c39479fd2");
			//keyInfo = "Sync-AES_256_CBC-HMAC256" + "johndoe@example.com";

			Log.getInstance().debug( String.format("base32 key: %s decoded to %s", account.syncKey, Hex.encodeHexString(syncKeyBin)));

			WeaveKeyPair keyPair = new WeaveKeyPair();

			byte[] message;
			Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
			hmacSHA256.init(new SecretKeySpec(syncKeyBin, "HmacSHA256"));
			
			message = ArrayUtils.addAll(keyInfo.getBytes(Constants.ASCII), new byte[]{1});
			keyPair.cryptKey = hmacSHA256.doFinal(message);
			message = ArrayUtils.addAll(ArrayUtils.addAll(keyPair.cryptKey, keyInfo.getBytes(Constants.ASCII)) , new byte[]{2});
			keyPair.hmacKey  = hmacSHA256.doFinal(message);
			
			this.privateKey = keyPair;
			
			Log.getInstance().info( "Successfully generated sync key and hmac key");
			Log.getInstance().debug( String.format("sync key: %s, crypt key: %s, crypt hmac: %s", account.syncKey, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		}
		return this.privateKey;
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
        	
        	try {
        		keyPair = this.getPrivateKeyPair();
        	} catch(NoSuchAlgorithmException e) { 
        		throw new WeaveException(e);
        	} catch(InvalidKeyException e) {
        		throw new WeaveException(e);
        	}
        		
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

        	try {
        		keyPair = this.getPrivateKeyPair();
        	} catch(NoSuchAlgorithmException e) { 
        		throw new WeaveException(e);
        	} catch(InvalidKeyException e) {
        		throw new WeaveException(e);
        	}
	                
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
	
	public WeaveBasicObject get(String collection, String id, boolean decrypt) throws WeaveException, NotFoundException {
		WeaveBasicObject wbo = this.storageClient.get(collection, id);
		if ( decrypt ) {
			try {
				if ( isEncrypted(wbo) ) {
					wbo = decryptWeaveBasicObject(wbo, collection);
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
					if ( isEncrypted(colWbo[i]) ) {
						colWbo[i] = decryptWeaveBasicObject(colWbo[i], collection);
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
				if ( !isEncrypted(wbo) ) {
					wbo = encryptWeaveBasicObject(wbo, collection);
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
