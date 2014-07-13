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
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClient.ApiVersion;
import org.exfio.weave.client.WeaveClient.StorageVersion;
import org.exfio.weave.client.WeaveClientParams;
import org.exfio.weave.crypto.PayloadCipher;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Base64;

public class WeaveStorageV5 extends WeaveStorageContext {
	
	public static final String KEY_CRYPTO_PATH       = "crypto/keys";
	public static final String KEY_CRYPTO_COLLECTION = "crypto";
	public static final String KEY_CRYPTO_ID         = "keys";
	public static final String KEY_META_PATH         = "meta/global";
	public static final String KEY_META_COLLECTION   = "meta";
	public static final String KEY_META_ID           = "global";
	
	private WeaveApiClient weaveApiClient;
	private String user;
	private String syncKey;
	private WeaveKeyPair privateKey;
	private Map<String, WeaveKeyPair> bulkKeys;

	public WeaveStorageV5() {
		super();
		version        = StorageVersion.v5;
		weaveApiClient = null;
		user           = null;
		syncKey        = null;
		privateKey     = null;
		bulkKeys       = null;
	}

	public void register(WeaveClientParams params) throws WeaveException {
		WeaveRegistrationParams p = (WeaveRegistrationParams)params;
		register(p.baseURL, p.user, p.password, p.email);
	}

	public void register(String baseURL, String user, String password, String email) throws WeaveException {
		register(baseURL, user, password, email, WeaveClient.ApiVersion.v1_1);		
	}

	public void register(String baseURL, String user, String password, String email, WeaveClient.ApiVersion apiVersion) throws WeaveException {
		this.weaveApiClient = WeaveApiClient.getInstance(apiVersion);
		this.weaveApiClient.register(baseURL, user, password, email);
		this.user            = user;
		this.syncKey         = null;
		this.privateKey      = null;
		this.bulkKeys        = null;
		
		//1. Build and publish meta/global WBO
		JSONObject metaObject = new JSONObject();
		metaObject.put("syncID", generateWeaveID());
		metaObject.put("storageVersion", WeaveClient.storageVersionToString(this.getStorageVersion()));
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
		this.syncKey = syncKeyB32.toUpperCase()
								.replace('L', '8')
								.replace('O', '9')
								.replaceAll("=", "");

		Log.getInstance().debug( String.format("generated sync key: %s", this.syncKey));

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

		weaveApiClient.put(KEY_CRYPTO_PATH, wboCrypto);
	}

	public void init(WeaveClientParams params) throws WeaveException {
		WeaveStorageV5Params p = (WeaveStorageV5Params)params;
		init(p.baseURL, p.user, p.password, p.syncKey);
	}

	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		init(baseURL, user, password, syncKey, WeaveClient.ApiVersion.v1_1);
	}

	public void init(String baseURL, String user, String password, String syncKey, WeaveClient.ApiVersion apiVersion) throws WeaveException {
		this.weaveApiClient = WeaveApiClient.getInstance(apiVersion);
		this.weaveApiClient.init(baseURL, user, password);
		this.user            = user;
		this.syncKey         = syncKey;
		this.privateKey      = null;
		this.bulkKeys        = null;
	}

	public WeaveApiClient getApiClient() {
		return weaveApiClient;
	}
	
	public void setApiClient(WeaveApiClient weaveApiClient) {
		this.weaveApiClient = weaveApiClient;
	}

	public WeaveClientParams getClientParams() {
		WeaveStorageV5Params params = new WeaveStorageV5Params();
		params.baseURL  = null; //FIXME - get from API client
		params.user     = this.user;
		params.password = null;
		params.syncKey  = this.syncKey;
		
		return params;
	}

	public String generateWeaveID() {
        SecureRandom rnd = new SecureRandom();
        byte[] weaveID = rnd.generateSeed(9);
        return Base64.encodeToString(weaveID, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
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
			String syncKeyB32 = this.syncKey.toUpperCase()
											.replace('8', 'L')
											.replace('9', 'O')
											.replaceAll("-", "");

			Log.getInstance().debug( String.format("normalised sync key: %s",  syncKeyB32));

			// Pad base32 string to multiple of 8 chars (40 bits)
			if ( (syncKeyB32.length() % 8) > 0 ) {
				int paddedLength = syncKeyB32.length() + 8 - (syncKeyB32.length() % 8);
				syncKeyB32 = StringUtils.rightPad(syncKeyB32, paddedLength, '=');
			}

			Base32 b32codec = new Base32();
			byte[] syncKeyBin = b32codec.decode(syncKeyB32);

			String keyInfo = "Sync-AES_256_CBC-HMAC256" + this.user;

			// For testing only
			//syncKey = binascii.unhexlify("c71aa7cbd8b82a8ff6eda55c39479fd2")
			//keyInfo = "Sync-AES_256_CBC-HMAC256" + "johndoe@example.com"

			Log.getInstance().debug( String.format("base32 key: %s decoded to %s", this.syncKey, Hex.encodeHexString(syncKeyBin)));

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
			Log.getInstance().debug( String.format("sync key: %s, crypt key: %s, crypt hmac: %s", this.syncKey, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
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
            	res = weaveApiClient.get(KEY_CRYPTO_PATH);
            } catch (NotFoundException e) {
            	throw new WeaveException(KEY_CRYPTO_PATH + " not found " + e.getMessage());
            }

            // Recursively call decrypt to extract key data
            String payload = this.decrypt(res.getPayload(), null);
            
            // Parse JSON encoded payload
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
	@SuppressWarnings("unchecked")
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
		WeaveBasicObject wbo = this.weaveApiClient.get(collection, id);
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
		return this.weaveApiClient.getCollectionIds(collection, ids, older, newer, index_above, index_below, limit, offset, sort);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean decrypt) throws WeaveException, NotFoundException {
		WeaveBasicObject[] colWbo = this.weaveApiClient.getCollection(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format);
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

	public WeaveCollectionInfo getCollectionInfo(String collection, boolean getcount, boolean getusage) throws WeaveException {
		Map<String, WeaveCollectionInfo> wcols = this.weaveApiClient.getInfoCollections(getcount, getusage);
		if ( !wcols.containsKey(collection) ) {
			throw new WeaveException(String.format("Collection '%s' not found", collection));
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
		return this.weaveApiClient.put(collection, id, wbo);
	}

	public Double delete(String collection, String id) throws WeaveException {
		return this.weaveApiClient.delete(collection, id);
	}

	public Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		return this.weaveApiClient.deleteCollection(collection, ids, older, newer, limit, offset, sort);
	}

}
