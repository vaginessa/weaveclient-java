package org.exfio.weave.account.exfiopeer.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

import org.exfio.weave.WeaveException;
import org.exfio.weave.crypto.JCEProvider;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.util.Base64;
import org.exfio.weave.util.OSUtils;

public class ECDH {

	public KeyPair extractECDHKeyPair(String privateKey, String publicKey) throws WeaveException {
		
		//Extract keys from ASN.1 format
		byte[] privateKeyBin = Base64.decodeBase64(privateKey);
		byte[] publicKeyBin  = Base64.decodeBase64(publicKey);
		
		PrivateKey privateKeyObj = null;
		PublicKey publicKeyObj   = null;

		try {
			KeyFactory kf          = KeyFactory.getInstance("ECDH", JCEProvider.getCryptoProvider());
			KeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBin);
			privateKeyObj          = kf.generatePrivate(privateKeySpec);
			KeySpec publicKeySpec  = new X509EncodedKeySpec(publicKeyBin);
			publicKeyObj           = kf.generatePublic(publicKeySpec);
		} catch (NoSuchProviderException e) {
			throw new WeaveException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidKeySpecException e) {
			throw new WeaveException(e);
		}
		
		return new KeyPair(publicKeyObj, privateKeyObj);
	}
	
	public KeyPair generateECDHKeyPair() throws WeaveException {
		KeyPair kp = null;
		
		try {
			//IMPORTANT - secp224k1 is not known to be compromised, but is under scrutiny post revelations of NSA backdoors by Snowden
			ECGenParameterSpec ecParamSpec = new ECGenParameterSpec("secp224k1");
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH", JCEProvider.getCryptoProvider());
			kpg.initialize(ecParamSpec);
			kp = kpg.generateKeyPair();
		} catch (NoSuchProviderException e) {
			throw new WeaveException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new WeaveException(e);
		}
		
		return kp;
	}
	
	public byte[] generateECDHSecret(String keyAPrivate, String keyBPublic) throws WeaveException {
		//Extract keys from Base64 format
		byte[] keyAPrivateBin = Base64.decodeBase64(keyAPrivate);
		byte[] keyBPublicBin = Base64.decodeBase64(keyBPublic);
		return generateECDHSecret(keyAPrivateBin, keyBPublicBin);
	}
	
	public byte[] generateECDHSecret(byte[] keyAPrivate, byte[] keyBPublic) throws WeaveException {

		//Extract keys from ASN.1 format
		PrivateKey keyAPrivateObj = null;
		PublicKey keyBPublicObj   = null;

		try {
			KeyFactory kf           = KeyFactory.getInstance("ECDH", JCEProvider.getCryptoProvider());
			KeySpec keyAPrivateSpec = new PKCS8EncodedKeySpec(keyAPrivate);
			keyAPrivateObj          = kf.generatePrivate(keyAPrivateSpec);
			KeySpec keyBPublicSpec  = new X509EncodedKeySpec(keyBPublic);
			keyBPublicObj           = kf.generatePublic(keyBPublicSpec);
		} catch (NoSuchProviderException e) {
			throw new WeaveException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidKeySpecException e) {
			throw new WeaveException(e);
		}
		
		return generateECDHSecret(keyAPrivateObj, keyBPublicObj);
	}
	
	public byte[] generateECDHSecret(PrivateKey keyAPrivate, PublicKey keyBPublic) throws WeaveException {
		
		KeyAgreement ka = null;
		try {
			ka = KeyAgreement.getInstance("ECDH", JCEProvider.getCryptoProvider());
			ka.init(keyAPrivate);
			ka.doPhase(keyBPublic, true);
		} catch (NoSuchProviderException e) {
			throw new WeaveException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new WeaveException(e);
		} catch (InvalidKeyException e) {
			throw new WeaveException(e);
		}
		
		return ka.generateSecret();
	}
	
	public WeaveKeyPair get3DHEKeyPair(String identityPrivateKey, String ephemeralPrivateKey, String otherIdentityPublicKey, String otherEphemeralPublicKey) throws WeaveException {
		return get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, true);
	}
	public WeaveKeyPair get3DHEKeyPair(String identityPrivateKey, String ephemeralPrivateKey, String otherIdentityPublicKey, String otherEphemeralPublicKey, boolean isAlice) throws WeaveException {
		byte[] identityPrivateKeyBin = Base64.decodeBase64(identityPrivateKey);
		byte[] ephemeralPrivateKeyBin = Base64.decodeBase64(ephemeralPrivateKey);
		byte[] otherIdentityPublicKeyBin = Base64.decodeBase64(otherIdentityPublicKey);
		byte[] otherEphemeralPublicKeyBin = Base64.decodeBase64(otherEphemeralPublicKey);
		return get3DHEKeyPair(identityPrivateKeyBin, ephemeralPrivateKeyBin, otherIdentityPublicKeyBin, otherEphemeralPublicKeyBin, isAlice);
	}

	public WeaveKeyPair get3DHEKeyPair(byte[] identityPrivateKey, byte[] ephemeralPrivateKey, byte[] otherIdentityPublicKey, byte[] otherEphemeralPublicKey) throws WeaveException {
		return get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, true);
	}
	
	public WeaveKeyPair get3DHEKeyPair(byte[] identityPrivateKey, byte[] ephemeralPrivateKey, byte[] otherIdentityPublicKey, byte[] otherEphemeralPublicKey, boolean isAlice) throws WeaveException {

		//Generate 3DHE shared secret
		byte[] sharedSecretBin = null;
		ByteArrayOutputStream osSharedSecret = null;
		
		if (isAlice) {
			//Perform 3DHE as Alice
			try {
				osSharedSecret = new ByteArrayOutputStream();
				osSharedSecret.write(generateECDHSecret(identityPrivateKey, otherEphemeralPublicKey));
				osSharedSecret.write(generateECDHSecret(ephemeralPrivateKey, otherIdentityPublicKey));
				osSharedSecret.write(generateECDHSecret(ephemeralPrivateKey, otherEphemeralPublicKey));
				sharedSecretBin = osSharedSecret.toByteArray();
			} catch (IOException e) {
				throw new WeaveException(e);
			}
		} else {
			//Perform 3DHE as Bob
			try {
				osSharedSecret = new ByteArrayOutputStream();
				osSharedSecret.write(generateECDHSecret(ephemeralPrivateKey, otherIdentityPublicKey));
				osSharedSecret.write(generateECDHSecret(identityPrivateKey, otherEphemeralPublicKey));
				osSharedSecret.write(generateECDHSecret(ephemeralPrivateKey, otherEphemeralPublicKey));
				sharedSecretBin = osSharedSecret.toByteArray();
			} catch (IOException e) {
				throw new WeaveException(e);
			}
		}
		
		//Derive keys from shared secret
		HKDF hkdf = new HKDF();
		DerivedSecrets sharedSecrets = hkdf.deriveSecrets(sharedSecretBin, "eXfio Weave Client".getBytes());
		
		WeaveKeyPair keyPair = new WeaveKeyPair();
		keyPair.cryptKey = sharedSecrets.getCipherKey().getEncoded();
		keyPair.hmacKey = sharedSecrets.getMacKey().getEncoded();
		return keyPair;
	}

}
