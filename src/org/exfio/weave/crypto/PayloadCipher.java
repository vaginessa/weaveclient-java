package org.exfio.weave.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;
import org.exfio.weave.util.Base64;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Log;

public class PayloadCipher {
	
	public String decrypt(String payload, WeaveKeyPair keyPair) throws WeaveException {
		String cleartext         = null;
		JSONObject encryptObject = null;
		
        // Parse JSONUtils encoded payload
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


	/**
	 * encrypt()
	 *
	 * Given a plaintext object, encrypt it and return the ciphertext value.
	 */
	@SuppressWarnings("unchecked")
	public String encrypt(String plaintext, WeaveKeyPair keyPair) throws WeaveException {		
		Log.getInstance().debug( "encrypt()");
		Log.getInstance().debug( "plaintext:\n" + plaintext);
	        

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

        // Construct JSONUtils encoded payload
		JSONObject encryptObject = new JSONObject();
		
		encryptObject.put("ciphertext", ciphertext);
		encryptObject.put("IV", Base64.encodeBase64String(iv));
		encryptObject.put("hmac", Hex.encodeHexString(hmac));
				
		return encryptObject.toJSONString();
	}
	
}
