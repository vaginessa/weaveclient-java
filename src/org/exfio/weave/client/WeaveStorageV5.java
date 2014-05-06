package org.exfio.weave.client;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.UnsupportedEncodingException;

import android.util.Log;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
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
import org.exfio.weave.client.WeaveClientParams;
import org.exfio.weave.resource.WeaveBasicObject;

public class WeaveStorageV5 extends WeaveStorageContext {
	private static final String TAG = "exfio.WeaveStorageContextV5";
	
	private WeaveHttpClient httpClient;
	private String user;
	private String syncKey;
	private WeaveKeyPair privateKey;
	private Map<String, WeaveKeyPair> bulkKeys;

	public WeaveStorageV5() {
		this.httpClient = null;
		this.user        = null;
		this.syncKey     = null;
		this.privateKey  = null;
		this.bulkKeys    = null;
	}

	public WeaveStorageV5(String baseURL, String user, String password, String syncKey) throws WeaveException {
		init(baseURL, user, password, syncKey);
	}

	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		this.httpClient  = new WeaveHttpClient(baseURL, user, password);
		this.user        = user;
		this.syncKey     = syncKey;
		this.privateKey  = null;
		this.bulkKeys    = null;
	}

	public void init(WeaveHttpClient httpClient, String user, String syncKey) throws WeaveException {
		this.httpClient  = httpClient;
		this.user        = user;
		this.syncKey     = syncKey;
		this.privateKey  = null;
		this.bulkKeys    = null;
	}

	public void init(WeaveClientParams params) throws WeaveException {
		WeaveStorageV5Params p = (WeaveStorageV5Params)params;
		init(p.baseURL, p.user, p.password, p.syncKey);
	}

	public WeaveHttpClient getHttpClient() {
		return httpClient;
	}
	
	/**
     Fetch the private key for the user and storage context
     provided to this object, and decrypt the private key
     by using my passphrase.  Store the private key in internal
     storage for later use.
	 */
	private WeaveKeyPair getPrivateKeyPair() {
		Log.i(TAG, "getPrivateKeyPair()");

		if ( this.privateKey == null ) {

			// Generate key pair using SHA-256 HMAC-based HKDF of sync key
			// See https://docs.services.mozilla.com/sync/storageformat5.html#the-sync-key

			// Remove dash chars, convert to uppercase and translate 8 and 9 to L and O
			String syncKeyB32 = this.syncKey.toUpperCase()
											.replace('8', 'L')
											.replace('9', 'O')
											.replaceAll("-", "");

			Log.d(TAG, String.format("normalised sync key: %s",  syncKeyB32));

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

			Log.d(TAG, String.format("base32 key: %s decoded to %s", this.syncKey, Hex.encodeHexString(syncKeyBin)));

			WeaveKeyPair keyPair = new WeaveKeyPair();

			try {
				byte[] message;
				Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
				hmacSHA256.init(new SecretKeySpec(syncKeyBin, "HmacSHA256"));
				message = ArrayUtils.addAll(keyInfo.getBytes(Constants.ASCII), new byte[]{1});
				keyPair.cryptKey = hmacSHA256.doFinal(message);
				message = ArrayUtils.addAll(ArrayUtils.addAll(keyPair.cryptKey, keyInfo.getBytes(Constants.ASCII)) , new byte[]{2});
				keyPair.hmacKey  = hmacSHA256.doFinal(message);

			} catch (NoSuchAlgorithmException e) {
				//TODO - Handle error
			} catch (InvalidKeyException e) {
				//TODO - Handle error				
			}
			
			this.privateKey = keyPair;
			
			Log.i(TAG, "Successfully generated sync key and hmac key");
			Log.d(TAG, String.format("sync key: %s, crypt key: %s, crypt hmac: %s", this.syncKey, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		}
		return this.privateKey;
	}
    
	/**
      Given a bulk key label, pull the key down from the network,
      and decrypt it using my private key.  Then store the key
      into self storage for later decrypt operations."""
	 */
	private WeaveKeyPair getBulkKeyPair(String collection) throws WeaveException {
		Log.i(TAG, "getBulkKeyPair()");
		
		if ( this.bulkKeys == null ) {
			Log.i(TAG, "Fetching bulk keys from server");

            WeaveBasicObject res = httpClient.get("crypto/keys");

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

    	    Iterator it = keyData.entrySet().iterator();
    	    while (it.hasNext()) {
    	        Map.Entry pairs = (Map.Entry)it.next();
            	JSONArray bulkKey = (JSONArray)pairs.getValue();
            	
            	WeaveKeyPair keyPair = new WeaveKeyPair();
            	keyPair.cryptKey = Base64.decodeBase64((String)bulkKey.get(0));
            	keyPair.hmacKey  = Base64.decodeBase64((String)bulkKey.get(1));
                this.bulkKeys.put((String)pairs.getKey(), keyPair);
            }
            
            Log.i(TAG, String.format("Successfully decrypted bulk key for %s", collection));
		}

        if ( this.bulkKeys.containsKey(collection) )  {
        	return this.bulkKeys.get(collection);
        } else if ( this.bulkKeys.containsKey("default") ) {
        	Log.i(TAG, String.format("No key found for %s, using default", collection));
        	return this.bulkKeys.get("default");        	
        } else {
        	throw new WeaveException("No default key found");
        }
	}
	
	public WeaveBasicObject decryptWeaveBasicObject(WeaveBasicObject wbo, String collection) throws WeaveException {
		String payload         = decrypt(wbo.getPayload(), collection);
		JSONObject jsonPayload = null;

		try {
			JSONParser parser = new JSONParser();
			jsonPayload = (JSONObject)parser.parse(payload);  
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		
		return new WeaveBasicObject(wbo.getId(), wbo.getModified(), wbo.getSortindex(), wbo.getTtl(), payload, jsonPayload);
	}
	
	public String decrypt(String payload, String collection) throws WeaveException {
		String cleartext         = null;
		JSONObject encryptObject = null;
		
        // Parse JSON encoded payload
		try {
			JSONParser parser = new JSONParser();			
			encryptObject = (JSONObject)parser.parse(payload);  
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
        
        // An encrypted payload has three relevant fields
        String ciphertext  = (String)encryptObject.get("ciphertext");
        byte[] cipherbytes = Base64.decodeBase64(ciphertext);
        byte[] iv          = Base64.decodeBase64((String)encryptObject.get("IV"));
        String cipher_hmac = (String)encryptObject.get("hmac");
        
        WeaveKeyPair keyPair = null;
        
        if ( collection == null ) {
        	Log.i(TAG,"Decrypting data record using sync key");
        	
        	keyPair = this.getPrivateKeyPair();
        		
        } else {
        	Log.i(TAG, String.format("Decrypting data record using bulk key %s", collection));

        	keyPair = this.getBulkKeyPair(collection);
        }
            
        Log.d(TAG, String.format("payload: %s, crypt key:  %s, crypt hmac: %s", payload, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
            
            
        // 1. Validate hmac of ciphertext
        // Note: HMAC verification is done against base64 encoded ciphertext
        String local_hmac = null;
        
        try {
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            hmacSHA256.init(new SecretKeySpec(keyPair.hmacKey, "HmacSHA256"));
            local_hmac = Hex.encodeHexString(hmacSHA256.doFinal(ciphertext.getBytes(Constants.ASCII)));
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidKeyException e) {
			throw new WeaveException(e);
		}
        
        if ( local_hmac != cipher_hmac ) {
        	throw new WeaveException("HMAC verification failed!");
        }
            
        // 2. Decrypt ciphertext
        // Note: this is the same as this operation at the openssl command line:
        // openssl enc -d -in data -aes-256-cbc -K `cat unwrapped_symkey.16` -iv `cat iv.16`
        try {
        	Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
        	cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyPair.cryptKey, "AES"), new IvParameterSpec(iv));
        	
        	byte[] clearbytes = cipher.doFinal(cipherbytes);
        	cleartext = new String(clearbytes, Constants.UTF8);
        	
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new WeaveException(e);
		} catch (IllegalBlockSizeException e) {
			throw new WeaveException(e);
		} catch (NoSuchPaddingException e) {
			throw new WeaveException(e);
		} catch (BadPaddingException e) {
			throw new WeaveException(e);
		} catch (InvalidKeyException e) {
			throw new WeaveException(e);
        }
        		
        Log.i(TAG, "Successfully decrypted v5 data record");
        
		return cleartext;
	}

	/**
	 * encrypt()
	 *
	 * Given a plaintext object, encrypt it and return the ciphertext value.
	 */
	public String encrypt(String plaintext, String collection) throws WeaveException {		
		Log.d(TAG, "encrypt()");
		Log.d(TAG, "plaintext:\n" + plaintext);
	        

		WeaveKeyPair keyPair = null;
		
		if ( collection == null ) {
			Log.i(TAG, "Encrypting data record using sync key");

			keyPair = this.getPrivateKeyPair();
	                
		} else {
			Log.i(TAG, String.format("Encrypting data record using bulk key %s", collection));

			keyPair = this.getBulkKeyPair(collection);
		}
		
        Log.d(TAG, String.format("payload: %s, crypt key:  %s, crypt hmac: %s", plaintext, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		
        
		// Encryption primitives
        String ciphertext  = null;
        byte[] cipherbytes = null;
        byte[] iv          = null;
        byte[] hmac        = null;
        
		
        // 1. Encrypt plaintext
        // Note: this is the same as this operation at the openssl command line:
        // openssl enc -d -in data -aes-256-cbc -K `cat unwrapped_symkey.16` -iv `cat iv.16`
		
        try {
        	Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
        	cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyPair.cryptKey, "AES"), new IvParameterSpec(iv));
        	cipherbytes = cipher.doFinal(plaintext.getBytes(Constants.ASCII));
        	iv          = cipher.getIV();
        	
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new WeaveException(e);
		} catch (IllegalBlockSizeException e) {
			throw new WeaveException(e);
		} catch (NoSuchPaddingException e) {
			throw new WeaveException(e);
		} catch (BadPaddingException e) {
			throw new WeaveException(e);
		} catch (InvalidKeyException e) {
			throw new WeaveException(e);
        }
        
        // 2. Create hmac of ciphertext
        // Note: HMAC is done against base64 encoded ciphertext
    	ciphertext = Base64.encodeBase64String(cipherbytes);
    	
    	try {
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            hmacSHA256.init(new SecretKeySpec(keyPair.hmacKey, "HmacSHA256"));
            hmac = hmacSHA256.doFinal(ciphertext.getBytes(Constants.ASCII));
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidKeyException e) {
			throw new WeaveException(e);
		}

		Log.i(TAG, "Successfully encrypted v5 data record");

        // Construct JSON encoded payload
		JSONObject encryptObject = new JSONObject();
		
		encryptObject.put("ciphertext", ciphertext);
		encryptObject.put("IV", Base64.encodeBase64String(iv));
		encryptObject.put("hmac", Hex.encodeHexString(hmac));
				
		return encryptObject.toJSONString();
	}
	
	public WeaveBasicObject get(String collection, String id) throws WeaveException {
		WeaveBasicObject wbo = this.httpClient.get(collection, id);
		return this.decryptWeaveBasicObject(wbo, collection);
	}
}
