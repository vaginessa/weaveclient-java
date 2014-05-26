package org.exfio.weave.client;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.security.SecureRandom;

import org.exfio.weave.Log;
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

public class WeaveStorageV5 extends WeaveStorageContext {
	
	private WeaveApiClient weaveApiClient;
	private String user;
	private String syncKey;
	private WeaveKeyPair privateKey;
	private Map<String, WeaveKeyPair> bulkKeys;

	public WeaveStorageV5() {
		this.weaveApiClient = null;
		this.user           = null;
		this.syncKey        = null;
		this.privateKey     = null;
		this.bulkKeys       = null;
	}

	public void init(WeaveClientParams params) throws WeaveException {
		WeaveStorageV5Params p = (WeaveStorageV5Params)params;
		init(p.baseURL, p.user, p.password, p.syncKey);
	}

	public void init(String baseURL, String user, String password, String syncKey) throws WeaveException {
		init(baseURL, user, password, syncKey, WeaveClient.ApiVersion.v1_1);
	}

	public void init(String baseURL, String user, String password, String syncKey, WeaveClient.ApiVersion apiVersion) throws WeaveException {
		this.weaveApiClient = WeaveApiClient.getApiClient(apiVersion);
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

            WeaveBasicObject res = weaveApiClient.get("crypto/keys");

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
            
        Log.getInstance().debug( String.format("payload: %s, crypt key:  %s, crypt hmac: %s", payload, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
            
            
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
        
        if ( !local_hmac.equals(cipher_hmac) ) {
        	Log.getInstance().warn(String.format("cipher hmac: %s, local hmac: %s", cipher_hmac, local_hmac));
        	throw new WeaveException("HMAC verification failed!");
        }
            
        // 2. Decrypt ciphertext
        // Note: this is the same as this operation at the openssl command line:
        // openssl enc -d -in data -aes-256-cbc -K `cat unwrapped_symkey.16` -iv `cat iv.16`
        try {
        	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        	cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyPair.cryptKey, "AES"), new IvParameterSpec(iv));
        	
        	byte[] clearbytes = cipher.doFinal(cipherbytes);
        	cleartext = new String(clearbytes, Constants.UTF8);
        	
            Log.getInstance().debug(String.format("cleartext: %s", cleartext));

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

        Log.getInstance().info("Successfully decrypted v5 data record");
        
		return cleartext;
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
		
        Log.getInstance().debug( String.format("payload: %s, crypt key:  %s, crypt hmac: %s", plaintext, Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		
        
		// Encryption primitives
        String ciphertext  = null;
        byte[] cipherbytes = null;
        byte[] iv          = null;
        byte[] hmac        = null;
        
        // 1. Encrypt plaintext
        // Note: this is the same as this operation at the openssl command line:
        // openssl enc -d -in data -aes-256-cbc -K `cat unwrapped_symkey.16` -iv `cat iv.16`
		
        try {
            SecureRandom rnd = new SecureRandom();
            IvParameterSpec ivspec = new IvParameterSpec(rnd.generateSeed(16));
            
        	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        	cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyPair.cryptKey, "AES"), ivspec);
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

		Log.getInstance().info( "Successfully encrypted v5 data record");

        // Construct JSON encoded payload
		JSONObject encryptObject = new JSONObject();
		
		encryptObject.put("ciphertext", ciphertext);
		encryptObject.put("IV", Base64.encodeBase64String(iv));
		encryptObject.put("hmac", Hex.encodeHexString(hmac));
				
		return encryptObject.toJSONString();
	}
	
	public boolean isEncrypted(WeaveBasicObject wbo) throws ParseException {
		//Determine if WBO is encrypted or not
		JSONObject jsonPayload = wbo.getPayloadAsJSONObject();
		return ( jsonPayload.containsKey("ciphertext") && jsonPayload.containsKey("IV") && jsonPayload.containsKey("hmac") );
	}
	
	public WeaveBasicObject get(String collection, String id, boolean decrypt) throws WeaveException {
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

	public void delete(String collection, String id) throws WeaveException {
		this.weaveApiClient.delete(collection, id);
	}

}
