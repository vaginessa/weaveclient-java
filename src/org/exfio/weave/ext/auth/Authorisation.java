package org.exfio.weave.ext.auth;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.InstantiationException;
import java.lang.AssertionError;
import java.lang.Math;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Collections;
import java.util.Set;
import java.util.Enumeration; 
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.security.NoSuchProviderException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Provider;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKeyFactory;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base32;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.NotFoundException;
import org.exfio.weave.client.WeaveBasicParams;
import org.exfio.weave.client.WeaveBasicObject;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientParams;
import org.exfio.weave.client.WeaveRegistrationParams;
import org.exfio.weave.client.WeaveStorageV5Params;
import org.exfio.weave.client.WeaveClient.StorageVersion;
import org.exfio.weave.crypto.PayloadCipher;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.ext.crypto.BCrypt;
import org.exfio.weave.ext.crypto.DerivedSecrets;
import org.exfio.weave.ext.crypto.HKDF;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;


public class Authorisation {

	public static final String PROTO_CLIENT_VERSION      = "1";
	public static final String PROTO_MESSAGE_VERSION     = "1";
	
	public static final int CLIENT_EPHEMERAL_KEYS_NUM    = 10;
	
	public static final String MESSAGE_TYPE_CLIENTAUTH   = "clientauth";
	
	//Client config
	public static final String KEY_CLIENT_CONFIG_CLIENTID       = "clientid";
	public static final String KEY_CLIENT_CONFIG_NAME           = "name";
	public static final String KEY_CLIENT_CONFIG_PUBLIC_KEY     = "publickey";
	public static final String KEY_CLIENT_CONFIG_PRIVATE_KEY    = "privatekey";
	public static final String KEY_CLIENT_CONFIG_EPHEMERAL_KEY  = "ephemeralkey";
	public static final String KEY_CLIENT_CONFIG_AUTHLEVEL      = "authlevel";
	
	//Key data struct
	public static final String KEY_CRYPTOKEY_KEYID       = "keyid";
	public static final String KEY_CRYPTOKEY_KEY         = "key";

	//Client data struct
	public static final String KEY_CLIENT_COLLECTION     = "exfioclient";
	public static final String KEY_CLIENT_VERSION        = "version";
	public static final String KEY_CLIENT_ID             = "clientid";
	public static final String KEY_CLIENT_NAME           = "name";
	public static final String KEY_CLIENT_IDENTITY_KEY   = "key";
	public static final String KEY_CLIENT_EPHEMERAL_KEYS = "ekeys";       //array of cryptokey data structs
	public static final String KEY_CLIENT_STATUS         = "status";
	public static final String KEY_CLIENT_AUTHLEVEL      = "authlevel";
	public static final String KEY_CLIENT_HMAC           = "hmac";
	
	//Message data struct
	public static final String KEY_MESSAGE_COLLECTION           = "exfiomessage";
	public static final String KEY_MESSAGE_VERSION              = "version";
	public static final String KEY_MESSAGE_SOURCE_CLIENTID      = "aclientid";
	public static final String KEY_MESSAGE_SOURCE_KEYID         = "akeyid";
	public static final String KEY_MESSAGE_SOURCE_KEY           = "akey";
	public static final String KEY_MESSAGE_DESTINATION_CLIENTID = "bclientid";
	public static final String KEY_MESSAGE_DESTINATION_KEYID    = "bkeyid";
	public static final String KEY_MESSAGE_SEQUENCE             = "sequence";
	public static final String KEY_MESSAGE_TYPE                 = "type";
	public static final String KEY_MESSAGE_CONTENT              = "content";

	//Authentication data struct
	public static final String KEY_AUTH_INNERSALT = "innersalt";
	public static final String KEY_AUTH_SALT      = "salt";
	public static final String KEY_AUTH_DIGEST    = "digest";

	//Client auth request message
	public static final String KEY_CLIENTAUTH_REQUEST_CLIENTID       = "clientid";
	public static final String KEY_CLIENTAUTH_REQUEST_NAME           = "name";
	public static final String KEY_CLIENTAUTH_REQUEST_AUTH           = "auth";     //authentication data struct

	//Client auth response message
	public static final String KEY_CLIENTAUTH_RESPONSE_CLIENTID      = "clientid";
	public static final String KEY_CLIENTAUTH_RESPONSE_NAME          = "name";
	public static final String KEY_CLIENTAUTH_RESPONSE_STATUS        = "status";
	public static final String KEY_CLIENTAUTH_RESPONSE_MESSAGE       = "message";
	public static final String KEY_CLIENTAUTH_RESPONSE_SYNCKEY       = "synckey";
	
	private static String cryptoProvider = null;
	
	private Properties prop;
	private File configFile;
	private WeaveClient wc;
	
	private String clientId;
	private String clientName;
	private KeyPair identityKeyPair;
	
	@lombok.Getter private String authCode;
	@lombok.Getter private String syncKey;

	public Authorisation(WeaveClient wc) {
		//Get default client from exfioweave config file
		try {
			init(wc, WeaveClient.buildAccountConfigPath());
		} catch (IOException e) {
			throw new AssertionError(e.getMessage());
		}
	}
		
	public Authorisation(WeaveClient wc, String account) {
		try {
			init(wc, WeaveClient.buildAccountConfigPath(account));
		} catch (IOException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public Authorisation(WeaveClient wc, File configFile) {
		try {
			init(wc, configFile);
		} catch (IOException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	private void init(WeaveClient wc, File configFile) throws IOException {
		this.wc         = wc;
		this.configFile = configFile;
		this.clientId   = null;
		this.prop       = new Properties();

		try {
			prop.load(new FileInputStream(configFile));
		} catch (IOException e) {
			throw new IOException(String.format("Couldn't load client config file '%s'", configFile.getAbsolutePath()));
		}

		java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}
	
	private void loadConfig() throws IOException {
		prop = new Properties();
		prop.load(new FileInputStream(configFile));
	}

	private void writeConfig() throws IOException {
		//Build path to config file
		configFile.getParentFile().mkdirs();
		prop.store(new FileOutputStream(configFile), "");
	}


	private boolean validateClientConfig(Properties clientProp) {
		
		//Validate client config fields
		if (!(
			clientProp.containsKey(KEY_CLIENT_CONFIG_CLIENTID) 
			&&
			clientProp.containsKey(KEY_CLIENT_CONFIG_NAME)
			&&
			clientProp.containsKey(KEY_CLIENT_CONFIG_PUBLIC_KEY)
			&&
			clientProp.containsKey(KEY_CLIENT_CONFIG_PRIVATE_KEY)
			//&&
			//clientProp.containsKey(KEY_CLIENT_AUTHLEVEL)
		)) {
			return false;
		}
		
		Pattern reEphemeral = Pattern.compile(String.format("^%s\\..+\\.%s$", KEY_CLIENT_CONFIG_EPHEMERAL_KEY, KEY_CLIENT_CONFIG_PUBLIC_KEY));
		
		int ephemeralKeyNum = 0;
		
		Iterator<String> iter = clientProp.stringPropertyNames().iterator();
		while ( iter.hasNext() ) {
			String propKey = iter.next();
			if ( reEphemeral.matcher(propKey).find() ) {
				ephemeralKeyNum++;
			}
		}
		
		if ( ephemeralKeyNum < CLIENT_EPHEMERAL_KEYS_NUM ) {
			return false;
		}
		
		return true;		
	}
	
	private boolean validateKeyJson(JSONObject payload) {
		if (!(
				payload.containsKey(KEY_CRYPTOKEY_KEYID) && payload.get(KEY_CRYPTOKEY_KEYID) instanceof String
				&&
				payload.containsKey(KEY_CRYPTOKEY_KEY) && payload.get(KEY_CRYPTOKEY_KEY) instanceof String
			)) {
				return false;
			}
		return true;
	}
	
	private boolean validateClientJson(JSONObject payload) {
		//Validate client record fields
		if (!(
			payload.containsKey(KEY_CLIENT_ID) && payload.get(KEY_CLIENT_ID) instanceof String 
			&&
			payload.containsKey(KEY_CLIENT_NAME) && payload.get(KEY_CLIENT_NAME) instanceof String
			&&
			payload.containsKey(KEY_CLIENT_IDENTITY_KEY) && payload.get(KEY_CLIENT_IDENTITY_KEY) instanceof String
			&&
			payload.containsKey(KEY_CLIENT_EPHEMERAL_KEYS) && payload.get(KEY_CLIENT_EPHEMERAL_KEYS) instanceof JSONArray
			&&
			payload.containsKey(KEY_CLIENT_STATUS) && payload.get(KEY_CLIENT_STATUS) instanceof String
			&&
			payload.containsKey(KEY_CLIENT_AUTHLEVEL) && payload.get(KEY_CLIENT_AUTHLEVEL) instanceof String
			&&
			payload.containsKey(KEY_CLIENT_HMAC) && payload.get(KEY_CLIENT_HMAC) instanceof String
		)) {
			return false;
		}
		
		JSONArray ekeys = (JSONArray)payload.get(KEY_CLIENT_EPHEMERAL_KEYS);
		Iterator<JSONObject> iter = ekeys.iterator();
		while (iter.hasNext()) {
			JSONObject keyObject = iter.next();
			if (!validateKeyJson(keyObject)) {
				return false;
			}
		}
		
		return true;
	}

	private boolean validateMessageJson(JSONObject payload) {
		//Validate message record fields
		if (!(
			payload.containsKey(KEY_MESSAGE_SOURCE_CLIENTID) && payload.get(KEY_MESSAGE_SOURCE_CLIENTID) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_SOURCE_KEYID) && payload.get(KEY_MESSAGE_SOURCE_KEYID) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_SOURCE_KEY) && payload.get(KEY_MESSAGE_SOURCE_KEY) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_DESTINATION_CLIENTID) && payload.get(KEY_MESSAGE_DESTINATION_CLIENTID) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_DESTINATION_KEYID) && payload.get(KEY_MESSAGE_DESTINATION_KEYID) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_SEQUENCE) && payload.get(KEY_MESSAGE_SEQUENCE) instanceof Long
			&&
			payload.containsKey(KEY_MESSAGE_TYPE) && payload.get(KEY_MESSAGE_TYPE) instanceof String
			&&
			payload.containsKey(KEY_MESSAGE_CONTENT) && payload.get(KEY_MESSAGE_CONTENT) instanceof String
		)) {
			return false;
		}

		return true;
	}

	private boolean validateClientAuthRequestJson(JSONObject payload) {
		//Validate client auth request message
		if (!(
			payload.containsKey(KEY_CLIENTAUTH_REQUEST_CLIENTID) && payload.get(KEY_CLIENTAUTH_REQUEST_CLIENTID) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_REQUEST_NAME) && payload.get(KEY_CLIENTAUTH_REQUEST_NAME) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_REQUEST_AUTH) && payload.get(KEY_CLIENTAUTH_REQUEST_AUTH) instanceof JSONObject
		)) {
			return false;
		}

		JSONObject authObject = (JSONObject)payload.get(KEY_CLIENTAUTH_REQUEST_AUTH);
		if (!(
			authObject.containsKey(KEY_AUTH_INNERSALT) && authObject.get(KEY_AUTH_INNERSALT) instanceof String
			&&
			authObject.containsKey(KEY_AUTH_SALT) && authObject.get(KEY_AUTH_SALT) instanceof String
			&&
			authObject.containsKey(KEY_AUTH_DIGEST) && authObject.get(KEY_AUTH_DIGEST) instanceof String
		)) {
			return false;
		}

		return true;
	}

	private boolean validateClientAuthResponseJson(JSONObject payload) {
		//Validate client auth response message
		if (!(
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_CLIENTID) && payload.get(KEY_CLIENTAUTH_RESPONSE_CLIENTID) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_NAME) && payload.get(KEY_CLIENTAUTH_RESPONSE_NAME) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_STATUS) && payload.get(KEY_CLIENTAUTH_RESPONSE_STATUS) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_MESSAGE) && payload.get(KEY_CLIENTAUTH_RESPONSE_MESSAGE) instanceof String
			//synckey only required on okay response
			//&&
			//payload.containsKey(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) && payload.get(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) instanceof String
		)) {
			return false;
		}

		return true;
	}

	//FIXME - Password is discarded as soon as it is used hence it is currently not possible to retrieve it from WeaveClient 
	private String getWeavePassword() throws WeaveException {
		String password = null;
		if ( wc.getStorageVersion() == WeaveClient.StorageVersion.v5 ) {
			WeaveStorageV5Params params = (WeaveStorageV5Params)wc.getClientParams();
			password = params.password;
		} else {
			throw new WeaveException(String.format("Storage version '%s' not supported", WeaveClient.storageVersionToString(wc.getStorageVersion())));
		}
		return password;
	}

	private byte[] generatePasswordSalt() {
		//Generate 128 bit (16 byte) salt
		return generatePBKDF2Salt(16);
	}

	private byte[] generateAuthSalt() {
		//Generate 128 bit (16 byte) salt
		return generatePBKDF2Salt(16);
	}

	private byte[] generatePBKDF2Salt(int size) {
		SecureRandom rnd = new SecureRandom();
        return rnd.generateSeed(size);
	}

	private String generatePasswordHash(String password, byte[] salt) {
		//Generate 128 bit (16 byte) digest
		return generatePBKDF2Digest(password, salt, 80000, 128);
	}

	private String generateAuthDigest(String cleartext, byte[] salt) {
		//Generate 128 bit (16 byte) digest
		return generatePBKDF2Digest(cleartext, salt, 80000, 128);
	}
	
	private String generatePBKDF2Digest(String cleartext, byte[] salt, int iterations, int length) {
		
		byte[] digestBin = null;
		
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			KeySpec spec = new PBEKeySpec(cleartext.toCharArray(), salt, iterations, length);
			digestBin = factory.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		} catch (InvalidKeySpecException e) {
			throw new AssertionError(e);
		}
		
		return Base64.encodeBase64String(digestBin);

		//PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
		//generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password), salt, iterations);
		//KeyParameter key = (KeyParameter)generator.generateDerivedMacParameters(keySizeInBits);
	}

	private String generateAuthCode() {
		//Default to 6 chars (30 bits of entropy)
		return generateAuthCode(6);
	}
	
	private String generateAuthCode(int chars) {
		SecureRandom rnd = new SecureRandom();
		Base32 b32codec = new Base32();
		int bytes = (int)Math.ceil((double)chars * 5 / 8);
        String authCode = b32codec.encodeToString(rnd.generateSeed(bytes));

        // Convert to uppercase, translate L and O to 8 and 9
		authCode = authCode.toUpperCase()
					.replace('L', '8')
					.replace('O', '9')
					.replaceAll("=", "");
		
		//Return the specified number of chars only
		return authCode.substring(0, chars - 1);
	}

	private boolean verifyClientAuthRequestAuthCode(String ephemeralKeyId, String authCode) {
		//FIXME - Verify out-of-band authcode
		boolean verified = true;
		
		String otherClientName         = prop.getProperty(String.format("messagekey.%s.clientauth.name", ephemeralKeyId));
		String innerSalt               = prop.getProperty(String.format("messagekey.%s.clientauth.innersalt", ephemeralKeyId));
		String salt                    = prop.getProperty(String.format("messagekey.%s.clientauth.salt", ephemeralKeyId));
	 	String digest                  = prop.getProperty(String.format("messagekey.%s.clientauth.digest", ephemeralKeyId));
	 	
	 	return verified;
	}
	
	private String getCryptoProvider() {
		if ( cryptoProvider == null ) {
			String cryptoProviderClass = null;
			String os = System.getProperty("os.name");
			if ( os.matches("(?i)android") ) {
				//On android we need to use Spongy Castle, i.e. SC
				cryptoProvider      = "SC";
				cryptoProviderClass = "org.spongycastle.jce.provider.BouncyCastleProvider";
			} else {
				cryptoProvider      = "BC";
				cryptoProviderClass = "org.bouncycastle.jce.provider.BouncyCastleProvider";
			}
			try {
				Class<?> provider = Class.forName(cryptoProviderClass);
				Security.addProvider((Provider)provider.newInstance());
			} catch (ClassNotFoundException e) {
				new AssertionError(e);
			} catch (IllegalAccessException e) {
				new AssertionError(e);
			} catch (InstantiationException  e) {
				new AssertionError(e);				
			}
		}
		return cryptoProvider;
	}
	
	private KeyPair generateECDHKeyPair() throws WeaveException {
		KeyPair kp = null;
		
		try {
			//IMPORTANT - secp224k1 is not known to be compromised, but is under scrutiny post revelations of NSA backdoors by Snowden
			ECGenParameterSpec ecParamSpec = new ECGenParameterSpec("secp224k1");
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH", getCryptoProvider());
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
	
	private byte[] generateECDHSecret(String keyAPrivate, String keyBPublic) throws WeaveException {
		//Extract keys from Base64 format
		byte[] keyAPrivateBin = Base64.decodeBase64(keyAPrivate);
		byte[] keyBPublicBin = Base64.decodeBase64(keyBPublic);
		return generateECDHSecret(keyAPrivateBin, keyBPublicBin);
	}
	
	private byte[] generateECDHSecret(byte[] keyAPrivate, byte[] keyBPublic) throws WeaveException {

		//Extract keys from ASN.1 format
		PrivateKey keyAPrivateObj = null;
		PublicKey keyBPublicObj   = null;

		try {
			KeyFactory kf           = KeyFactory.getInstance("ECDH", getCryptoProvider());
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
	
	private byte[] generateECDHSecret(PrivateKey keyAPrivate, PublicKey keyBPublic) throws WeaveException {
		
		KeyAgreement ka = null;
		try {
			ka = KeyAgreement.getInstance("ECDH", getCryptoProvider());
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
	private WeaveKeyPair get3DHEKeyPair(String identityPrivateKey, String ephemeralPrivateKey, String otherIdentityPublicKey, String otherEphemeralPublicKey) throws WeaveException {
		return get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, true);
	}
	private WeaveKeyPair get3DHEKeyPair(String identityPrivateKey, String ephemeralPrivateKey, String otherIdentityPublicKey, String otherEphemeralPublicKey, boolean isAlice) throws WeaveException {
		byte[] identityPrivateKeyBin = Base64.decodeBase64(identityPrivateKey);
		byte[] ephemeralPrivateKeyBin = Base64.decodeBase64(ephemeralPrivateKey);
		byte[] otherIdentityPublicKeyBin = Base64.decodeBase64(otherIdentityPublicKey);
		byte[] otherEphemeralPublicKeyBin = Base64.decodeBase64(otherEphemeralPublicKey);
		return get3DHEKeyPair(identityPrivateKeyBin, ephemeralPrivateKeyBin, otherIdentityPublicKeyBin, otherEphemeralPublicKeyBin, isAlice);
	}

	private WeaveKeyPair get3DHEKeyPair(byte[] identityPrivateKey, byte[] ephemeralPrivateKey, byte[] otherIdentityPublicKey, byte[] otherEphemeralPublicKey) throws WeaveException {
		return get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, true);
	}
	
	private WeaveKeyPair get3DHEKeyPair(byte[] identityPrivateKey, byte[] ephemeralPrivateKey, byte[] otherIdentityPublicKey, byte[] otherEphemeralPublicKey, boolean isAlice) throws WeaveException {

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

	private boolean isAuthorised() {
		if ( wc.getStorageVersion() == StorageVersion.v5 ) {
			return ( ((WeaveStorageV5Params)wc.getClientParams()).syncKey != null );
		} else {
			return false;
		}
	}
	
	private void setMessageState(String ephemeralKeyId, String state, float timestamp) throws WeaveException {
		prop.setProperty(String.format("messagekey.%s.state", ephemeralKeyId), state);
		prop.setProperty(String.format("messagekey.%s.timestamp", ephemeralKeyId), String.format("%.2f", timestamp));
	}

	private void cleanClientConfig() throws WeaveException {
		cleanClientConfig(true, true, true);
	}

	private void cleanClientConfig(boolean cleanEphemeral, boolean cleanMessages, boolean cleanClientAuth) throws WeaveException {
		Log.getInstance().debug("cleanClientConfig()");

		Pattern reEphemeral  = Pattern.compile("^ephemeralkey\\..*$");
		Pattern reMessages   = Pattern.compile("^messagekey\\..*$");
		Pattern reClientAuth = Pattern.compile("^clientauth\\..*$");
		
		Iterator<String> iter = prop.stringPropertyNames().iterator();
		while ( iter.hasNext() ) {
			String propKey = iter.next();
				
			if ( cleanEphemeral && reEphemeral.matcher(propKey).find() ) {
				prop.remove(propKey);	
			}

			if ( cleanMessages && reMessages.matcher(propKey).find() ) {
				prop.remove(propKey);				
			}
						
			if ( cleanClientAuth && reClientAuth.matcher(propKey).find() ) {
				prop.remove(propKey);
			}
		}
		
	}

	public void initServer(String version) throws WeaveException {
		Log.getInstance().debug("initSever()");
		
		//Delete exfio collections
		try {
			wc.deleteCollection(KEY_CLIENT_COLLECTION, null, null, null, null, null, null);
		} catch (NotFoundException e) {
			//Nothing to do - fail quietly
		}

		try {
			wc.deleteCollection(KEY_MESSAGE_COLLECTION, null, null, null, null, null, null);
		} catch (NotFoundException e) {
			//Nothing to do - fail quietly
		}

		//FIXME - create meta record with exfio version etc., i.e. meta/exfio
		//FIXME - create bulk keys for exfioclient and exfiomessage collections

	}
	
	public void initClient(String name, boolean isAuthorised) throws WeaveException {
		Log.getInstance().debug("initClient()");
		
		//Clean up config
		cleanClientConfig();
		
		//Generate and store client id
		clientId   = wc.generateWeaveID();
		clientName = name;
		prop.setProperty(KEY_CLIENT_CONFIG_CLIENTID, clientId);
		prop.setProperty(KEY_CLIENT_CONFIG_NAME, clientName);

		//Generate and store ECDH keypair
		identityKeyPair = generateECDHKeyPair();
		
		String identityPublicKey  = Base64.encodeBase64String(identityKeyPair.getPublic().getEncoded());
        String identityPrivateKey = Base64.encodeBase64String(identityKeyPair.getPrivate().getEncoded());
		prop.setProperty("publickey",  identityPublicKey);
		prop.setProperty("privatekey", identityPrivateKey);

		
		JSONObject payloadClient = new JSONObject();
		payloadClient.put(KEY_CLIENT_VERSION, PROTO_CLIENT_VERSION);
		payloadClient.put(KEY_CLIENT_ID, clientId);
		payloadClient.put(KEY_CLIENT_NAME, clientName);
		payloadClient.put(KEY_CLIENT_IDENTITY_KEY, identityPublicKey);
		payloadClient.put(KEY_CLIENT_AUTHLEVEL, "all");
		
		//Generate ephemeral keys
		JSONArray ekeys = new JSONArray();
		while ( ekeys.size() < CLIENT_EPHEMERAL_KEYS_NUM ) {
			
			String ephemeralKeyId = wc.generateWeaveID();
			KeyPair ephemeralKeyPair = generateECDHKeyPair();
			String ephemeralPublicKey  = Base64.encodeBase64String(ephemeralKeyPair.getPublic().getEncoded());
	        String ephemeralPrivateKey = Base64.encodeBase64String(ephemeralKeyPair.getPrivate().getEncoded());
			prop.setProperty(String.format("ephemeralkey.%s.publickey", ephemeralKeyId), ephemeralPublicKey);
			prop.setProperty(String.format("ephemeralkey.%s.privatekey", ephemeralKeyId), ephemeralPrivateKey);
			prop.setProperty(String.format("ephemeralkey.%s.status", ephemeralKeyId), "published");
			
			JSONObject keyObject = new JSONObject();
			keyObject.put(KEY_CRYPTOKEY_KEYID, ephemeralKeyId);
			keyObject.put(KEY_CRYPTOKEY_KEY, ephemeralPublicKey);
			
			ekeys.add(keyObject);
		}
		
		payloadClient.put(KEY_CLIENT_EPHEMERAL_KEYS, ekeys);

		if ( isAuthorised ) {
			payloadClient.put(KEY_CLIENT_STATUS, "authorised");
			
			//FIXME - Calculate HMAC
			payloadClient.put(KEY_CLIENT_HMAC, "TODO");
		} else {
			payloadClient.put(KEY_CLIENT_STATUS, "pending");
			payloadClient.put(KEY_CLIENT_HMAC, "");	
		}
		
		WeaveBasicObject wboClient = new WeaveBasicObject(clientId, null, null, null, payloadClient.toJSONString());
		
		wc.putItem(KEY_CLIENT_COLLECTION, clientId, wboClient, false);
		
		//Write config file
		try {
			writeConfig();
		} catch (IOException e) {
			throw new WeaveException("Couldn't write config file - " + e.getMessage());
		}
	}

	public void updateClient() throws WeaveException {
		Log.getInstance().debug("updateClient()");
		
		if ( !validateClientConfig(prop) ) {
			throw new WeaveException("Client config invalid!");
		}
		
		//Get client id
		clientId = prop.getProperty(KEY_CLIENT_CONFIG_CLIENTID);
		
		//Get and validate client record
		WeaveBasicObject wboClient = null;
		try {
			wboClient = wc.getItem(KEY_CLIENT_COLLECTION, clientId, false);
		} catch (NotFoundException e) {
			throw new WeaveException(String.format("Client '%s' not found - " + e.getMessage(), clientId));
		}
		
		JSONObject payloadClient = null;
		try {
			payloadClient = wboClient.getPayloadAsJSONObject();			
		} catch (org.json.simple.parser.ParseException e) {
			throw new WeaveException(e);
		}
		
		if ( !validateClientJson(payloadClient) ) {
			throw new WeaveException(String.format("Invalid client record for client '%s'", clientId));
		}
				
		//Remove used ephemeral keys from client
		JSONArray ekeys = (JSONArray)payloadClient.get(KEY_CLIENT_EPHEMERAL_KEYS);
		ListIterator<JSONObject> iterEkey = ekeys.listIterator();
		while (iterEkey.hasNext()) {
			JSONObject keyObject = iterEkey.next();
			String keyId = (String)keyObject.get(KEY_CRYPTOKEY_KEYID);
			
			String status = prop.getProperty(String.format("ephemeralkey.%s.status", keyId));
			
			if ( status.matches("(?i)provisioned") ) {
				//Generate new ephemeral key
				String newEphemeralKeyId = wc.generateWeaveID();
				KeyPair newEphemeralKeyPair = generateECDHKeyPair();
				String newEphemeralPublicKey  = Base64.encodeBase64String(newEphemeralKeyPair.getPublic().getEncoded());
		        String newEphemeralPrivateKey = Base64.encodeBase64String(newEphemeralKeyPair.getPrivate().getEncoded());
				prop.setProperty(String.format("ephemeralkey.%s.publickey", newEphemeralKeyId), newEphemeralPublicKey);
				prop.setProperty(String.format("ephemeralkey.%s.privatekey", newEphemeralKeyId), newEphemeralPrivateKey);
				prop.setProperty(String.format("ephemeralkey.%s.status", newEphemeralKeyId), "published");
				
				JSONObject newKeyObject = new JSONObject();
				newKeyObject.put(KEY_CRYPTOKEY_KEYID, newEphemeralKeyId);
				newKeyObject.put(KEY_CRYPTOKEY_KEY, newEphemeralPublicKey);
				
				//Replace used ephemeral key with a new one				
				iterEkey.set(newKeyObject);
			}			
		}
		payloadClient.put(KEY_CLIENT_EPHEMERAL_KEYS, ekeys);
		
		WeaveBasicObject wboClientUpdate = new WeaveBasicObject(clientId, null, null, null, payloadClient.toJSONString());
		
		wc.putItem(KEY_CLIENT_COLLECTION, clientId, wboClientUpdate, false);
		
		//Write config file
		try {
			writeConfig();
		} catch (IOException e) {
			throw new WeaveException("Couldn't write config file - " + e.getMessage());
		}
	}
	
	
	//@SuppressWarnings("unchecked")
	public void authoriseClient(String clientName, String password) throws WeaveException {
		Log.getInstance().debug("authoriseClient()");

		//FIXME - Warn user if client has already been initialised
		
		//Create client record
		initClient(clientName, isAuthorised());
		
        //Get private key
		String identityPrivateKey  = prop.getProperty("privatekey");

		//Generate and store auth code
		authCode = generateAuthCode();
		prop.setProperty("clientauth.authcode", authCode);
				
		//Get existing clients and create registration for each
		WeaveBasicObject[] wbos = null;
		try {
			wbos = wc.getCollection(KEY_CLIENT_COLLECTION, null, null, null, null, null, null, null, null, null, false);
		} catch (NotFoundException e) {
			throw new WeaveException(e);
		}
		
		for (int i = 0; i < wbos.length; i++) {
							
			//Extract and validate payload
			JSONObject payload = null;
			try {
				payload = wbos[i].getPayloadAsJSONObject();
			} catch (org.json.simple.parser.ParseException e) {
				throw new WeaveException(e);
			}
			
			if ( !validateClientJson(payload) ) {
				Log.getInstance().error("Invalid client payload");
				continue;
			}
			
			String otherClientId     = (String)payload.get(KEY_CLIENT_ID);
			String otherClientName   = (String)payload.get(KEY_CLIENT_NAME);
			String otherClientStatus = (String)payload.get(KEY_CLIENT_STATUS);

			if ( otherClientId.equals(clientId) ) {
				//Ignore our own client record
				Log.getInstance().info(String.format("Client '%s' (%s) is self. Skipping...", otherClientName, otherClientId));
				continue;
			}

			if ( !otherClientStatus.equalsIgnoreCase("authorised") ) {
				//Ignore unauthorised clients
				Log.getInstance().info(String.format("Client '%s' (%s) is not authorised. Skipping...", otherClientName, otherClientId));
				continue;
			}
		
			//Store clientauth state
			String ephemeralKeyId = wc.generateWeaveID();
			prop.setProperty(String.format("clientauth.%s.status", ephemeralKeyId), "pending");

			//Store message state
			prop.setProperty(String.format("messagekey.%s.type", ephemeralKeyId), "clientauth");
			prop.setProperty(String.format("messagekey.%s.role", ephemeralKeyId), "requester");
			prop.setProperty(String.format("messagekey.%s.state", ephemeralKeyId), "requestpending");

			//Generate and store ephemeral ECDH keypair
			KeyPair ephemeralKeyPair   = generateECDHKeyPair();
			String ephemeralPublicKey  = Base64.encodeBase64String(ephemeralKeyPair.getPublic().getEncoded());
	        String ephemeralPrivateKey = Base64.encodeBase64String(ephemeralKeyPair.getPrivate().getEncoded());
			prop.setProperty(String.format("messagekey.%s.publickey", ephemeralKeyId), ephemeralPublicKey);
			prop.setProperty(String.format("messagekey.%s.privatekey", ephemeralKeyId), ephemeralPrivateKey);

			//Get and store identity and ephemeral keys
			String otherIdentityPublicKey = (String)payload.get(KEY_CLIENT_IDENTITY_KEY);

			//Randomly select ephemeral key and store details
			JSONArray ekeys = (JSONArray)payload.get(KEY_CLIENT_EPHEMERAL_KEYS);
			int keyIndex = (int)(Math.random() * ekeys.size());
			JSONObject key = (JSONObject)ekeys.get(keyIndex); 
			String otherEphemeralKeyId     = (String)key.get(KEY_CRYPTOKEY_KEYID);
			String otherEphemeralPublicKey = (String)key.get(KEY_CRYPTOKEY_KEY);
			
			prop.setProperty(String.format("messagekey.%s.otherclientid", ephemeralKeyId), otherClientId);
			prop.setProperty(String.format("messagekey.%s.otheridentitykey", ephemeralKeyId), otherIdentityPublicKey);
			prop.setProperty(String.format("messagekey.%s.otherkeyid", ephemeralKeyId), otherEphemeralKeyId);
			prop.setProperty(String.format("messagekey.%s.otherkey", ephemeralKeyId), otherEphemeralPublicKey);

			//TODO - refactor to avoid encoding/decoding to Base64
			WeaveKeyPair keyPair = get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey);
			
			//Build message WBO
			JSONObject msgObject = new JSONObject();
			msgObject.put(KEY_MESSAGE_VERSION, PROTO_MESSAGE_VERSION);
			msgObject.put(KEY_MESSAGE_SOURCE_CLIENTID, clientId);
			msgObject.put(KEY_MESSAGE_SOURCE_KEYID, ephemeralKeyId);
			msgObject.put(KEY_MESSAGE_SOURCE_KEY, ephemeralPublicKey);
			msgObject.put(KEY_MESSAGE_DESTINATION_CLIENTID, otherClientId);
			msgObject.put(KEY_MESSAGE_DESTINATION_KEYID, otherEphemeralKeyId);
			msgObject.put(KEY_MESSAGE_TYPE, MESSAGE_TYPE_CLIENTAUTH);
			msgObject.put(KEY_MESSAGE_SEQUENCE, 1);

			//Build client auth request
			JSONObject caObject = new JSONObject();
			caObject.put(KEY_CLIENTAUTH_REQUEST_CLIENTID, clientId);
			caObject.put(KEY_CLIENTAUTH_REQUEST_NAME, clientName);
			
			//Build auth data struct
			//To increase difficulty of MiTM attacks concatenate authcode and password hash and use PBKDF2 to make brute forcing expensive
			//IMPORTANT: If password is known by attacker it would be trivial to brute force authcode
			JSONObject authObject = new JSONObject();

			//No additional security provided by including known value
			//Date tsDate = new Date();
			//String timestamp = String.format("%.2f", (double)tsDate.getTime() / 1000);
			
			byte[] passwordSaltBin = generatePasswordSalt();
			String passwordSalt = Base64.encodeBase64String(passwordSaltBin);			
			String passwordHash = generatePasswordHash(password, passwordSaltBin);
			
			byte[] authSaltBin = generateAuthSalt();
			String authSalt = Base64.encodeBase64String(authSaltBin);
			String authDigest = generateAuthDigest(authCode + passwordHash, authSaltBin);
			
			authObject.put(KEY_AUTH_INNERSALT, passwordSalt);
			authObject.put(KEY_AUTH_SALT, authSalt);
			authObject.put(KEY_AUTH_DIGEST, authDigest);
			caObject.put(KEY_CLIENTAUTH_REQUEST_AUTH, authObject);
			
			//Ecrypt message content
			String msgContent = caObject.toJSONString();
			PayloadCipher cipher = new PayloadCipher();
			msgObject.put(KEY_MESSAGE_CONTENT, cipher.encrypt(msgContent, keyPair));
			
			WeaveBasicObject wboReg = new WeaveBasicObject(otherEphemeralKeyId, null, null, null, msgObject.toJSONString());
			Double modified = wc.putItem(KEY_MESSAGE_COLLECTION, otherEphemeralKeyId, wboReg, false);

			prop.setProperty(String.format("messagekey.%s.state", ephemeralKeyId), "requestsent");
			prop.setProperty(String.format("messagekey.%s.timestamp", ephemeralKeyId), String.format("%.2f", modified));

			//Write config file
			try {
				writeConfig();
			} catch (IOException e) {
				throw new WeaveException("Couldn't write config file - " + e.getMessage());
			}
		}		
	}

	public void pollForClientAuthResponse() throws WeaveException {
		Log.getInstance().debug("pollClientAuthResponse()");

		if ( !validateClientConfig(prop) ) {
			throw new WeaveException("Client config invalid!");
		}

		//Get client id
		clientId = prop.getProperty(KEY_CLIENT_CONFIG_CLIENTID);

		//Get private key
		String identityPrivateKey = prop.getProperty("privatekey");

		//Check for update to registration request WBO
		Pattern reReg = Pattern.compile("^clientauth\\.(.*)\\.status$");
		Iterator<String> iter = prop.stringPropertyNames().iterator();

		while ( syncKey == null && iter.hasNext() ) {
			String propKey = iter.next();
			Matcher match = reReg.matcher(propKey);
			if ( match.find() ) {
				String ephemeralKeyId   =  match.group(1);
				String clientAuthStatus = prop.getProperty(String.format("clientauth.%s.status", ephemeralKeyId));
				String messageState     = prop.getProperty(String.format("messagekey.%s.state", ephemeralKeyId));
				
				if ( !clientAuthStatus.equalsIgnoreCase("pending") ) {
					Log.getInstance().info(String.format("Client auth request '%s' status '%s'. Nothing to do", ephemeralKeyId, clientAuthStatus));
					continue;
				}
				if ( !messageState.equalsIgnoreCase("requestsent") ) {
					Log.getInstance().info(String.format("Message '%s' state '%s'. Nothing to do", ephemeralKeyId, messageState));
					continue;
				}
				
				String ephemeralPrivateKey     = prop.getProperty(String.format("messagekey.%s.privatekey", ephemeralKeyId));
				String otherClientId           = prop.getProperty(String.format("messagekey.%s.otherclientid", ephemeralKeyId));
				String otherIdentityPublicKey  = prop.getProperty(String.format("messagekey.%s.otheridentitykey", ephemeralKeyId));
				String otherEphemeralKeyId     = prop.getProperty(String.format("messagekey.%s.otherkeyid", ephemeralKeyId));
				String otherEphemeralPublicKey = prop.getProperty(String.format("messagekey.%s.otherkey", ephemeralKeyId));
				Double msgTimestamp            = Double.parseDouble(prop.getProperty(String.format("messagekey.%s.timestamp", ephemeralKeyId)));


				WeaveBasicObject wbo = null;
				try {
					wbo = wc.getItem(KEY_MESSAGE_COLLECTION, ephemeralKeyId, false);
				} catch (NotFoundException e) {
					Log.getInstance().info(String.format("No messages for ephemeral key '%s'", ephemeralKeyId));
					continue;
				} catch (WeaveException e) {
					Log.getInstance().error(e.getMessage());
					continue;					
				}
				
				//Extract and validate payload
				JSONObject payload = null;
				try {
					payload = wbo.getPayloadAsJSONObject();
				} catch (org.json.simple.parser.ParseException e) {
					throw new WeaveException(e);
				}

				if ( !validateMessageJson(payload) ) {
					Log.getInstance().error("Invalid message");
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}
				
				if (!(
					((String)payload.get(KEY_MESSAGE_SOURCE_CLIENTID)).equals(otherClientId)
					&&
					((String)payload.get(KEY_MESSAGE_SOURCE_KEYID)).equals(otherEphemeralKeyId)
					&&
					((String)payload.get(KEY_MESSAGE_DESTINATION_CLIENTID)).equals(clientId)
					&&
					((String)payload.get(KEY_MESSAGE_DESTINATION_KEYID)).equals(ephemeralKeyId)
				)) {
					Log.getInstance().error(String.format("Invalid message - Id mismatch - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}
				
				//Extract and validate response message
				WeaveKeyPair keyPair = get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey);
				PayloadCipher cipher = new PayloadCipher();
				String content = cipher.decrypt((String)payload.get(KEY_MESSAGE_CONTENT), keyPair);
				JSONObject responseObject = null;
				try {
					JSONParser parser = new JSONParser();
					responseObject = (JSONObject)parser.parse(content);
				} catch (org.json.simple.parser.ParseException e) {
					throw new WeaveException(e);
				}
				
				if ( !validateClientAuthResponseJson(responseObject) ) {
					Log.getInstance().error(String.format("Invalid client auth response - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));					
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				if ( !((String)responseObject.get(KEY_CLIENTAUTH_RESPONSE_CLIENTID)).equals(otherClientId) ) {
					Log.getInstance().error(String.format("Invalid client auth response - Id mismatch - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				String otherClientName = (String)responseObject.get(KEY_CLIENTAUTH_RESPONSE_NAME);
				String status = (String)responseObject.get(KEY_CLIENTAUTH_RESPONSE_STATUS);
				String message = (String)responseObject.get(KEY_CLIENTAUTH_RESPONSE_MESSAGE);

				if ( !status.matches("(?i)okay|fail") ) {
					Log.getInstance().error(String.format("Client auth response status from client '%s' (%s) is unknown - %s: %s", otherClientName, otherClientId, status, message));					
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				//Update status
				prop.setProperty(String.format("clientauth.%s.status", ephemeralKeyId), status);
				prop.setProperty(String.format("clientauth.%s.message", ephemeralKeyId), message);

				Log.getInstance().info(String.format("Client auth response from client '%s' (%s) - %s: %s", otherClientName, otherClientId, status, message));

				if ( status.equalsIgnoreCase("okay") ) {
					if ( !(responseObject.containsKey(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) && responseObject.get(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) instanceof String) ) {
						Log.getInstance().error(String.format("Invalid client auth response - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
						setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
						continue;
					}
					
					//Success!
					
					//Get synckey and clean up client auth config
					syncKey = (String)responseObject.get(KEY_CLIENTAUTH_RESPONSE_SYNCKEY);
					
					prop.setProperty("synckey", syncKey);
					prop.setProperty("authby", otherClientId);
					
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					
					updateClient();

					this.cleanClientConfig(false, false, true);
					
				} else {
					//Do nothing
				}

				//Write config file
				try {
					writeConfig();
				} catch (IOException e) {
					throw new WeaveException("Couldn't write config file - " + e.getMessage());
				}
			}
		}		
	}

	//FIXME - return list of new (and existing) client auth requests
	public void pollForClientAuthRequest() throws WeaveException {
		Log.getInstance().debug("pollClientAuthRequet()");

		if ( !validateClientConfig(prop) ) {
			throw new WeaveException("Client config invalid!");
		}

		//Get client id
		clientId = prop.getProperty(KEY_CLIENT_CONFIG_CLIENTID);

		//Get private key
		String identityPrivateKey = prop.getProperty("privatekey");

		//Check for update to registration request WBO
		Pattern reEKey = Pattern.compile("^ephemeralkey\\.(.*)\\.publickey$");
		Iterator<String> iter = prop.stringPropertyNames().iterator();
		
		while ( iter.hasNext() ) {
			String propKey = iter.next();
			Matcher match = reEKey.matcher(propKey);
			if ( match.find() ) {
				String ephemeralKeyId      =  match.group(1);
				String ephemeralPrivateKey = prop.getProperty(String.format("ephemeralkey.%s.privatekey", ephemeralKeyId));

				WeaveBasicObject wbo = null;
				try {
					wbo = wc.getItem(KEY_MESSAGE_COLLECTION, ephemeralKeyId, false);
				} catch (NotFoundException e) {
					Log.getInstance().info(String.format("No messages for ephemeral key '%s'", ephemeralKeyId));
					continue;
				} catch (WeaveException e) {
					Log.getInstance().error(e.getMessage());
					continue;					
				}
				
				//Set ephemeral key status to provisioned
				prop.setProperty(String.format("ephemeralkey.%s.status", ephemeralKeyId), "provisioned");

				//Extract and validate payload
				JSONObject payload = null;
				try {
					payload = wbo.getPayloadAsJSONObject();
				} catch (org.json.simple.parser.ParseException e) {
					Log.getInstance().error(String.format("Invalid payload for message '%s'", ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				if ( !validateMessageJson(payload) ) {
					Log.getInstance().error(String.format("Invalid record for message '%s'", ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				String otherClientId           = (String)payload.get(KEY_MESSAGE_SOURCE_CLIENTID);
				String otherEphemeralKeyId     = (String)payload.get(KEY_MESSAGE_SOURCE_KEYID);
				String otherEphemeralPublicKey = (String)payload.get(KEY_MESSAGE_SOURCE_KEY);

				if (!(
					((String)payload.get(KEY_MESSAGE_DESTINATION_CLIENTID)).equals(clientId)
					&&
					((String)payload.get(KEY_MESSAGE_DESTINATION_KEYID)).equals(ephemeralKeyId)
				)) {
					Log.getInstance().error(String.format("Invalid message - Id mismatch - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				//TODO - should identity and ephemeral keys be included in first message?
				
				//Get identity and ephemeral keys for other client
				
				//Get and validate client record
				WeaveBasicObject wboClient = null;
				try {
					wboClient = wc.getItem(KEY_CLIENT_COLLECTION, otherClientId, false);
				} catch (NotFoundException e) {
					Log.getInstance().error(String.format("Client '%s' not found", otherClientId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				} catch (WeaveException e) {
					Log.getInstance().error(e.getMessage());
					continue;					
				}
				
				JSONObject payloadClient = null;
				try {
					payloadClient = wboClient.getPayloadAsJSONObject();			
				} catch (org.json.simple.parser.ParseException e) {
					Log.getInstance().error(String.format("Invalid payload for client '%s'", otherClientId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				if ( !validateClientJson(payloadClient) ) {
					Log.getInstance().error(String.format("Invalid record for client '%s'", otherClientId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}
				
				String otherClientName = (String)payloadClient.get(KEY_CLIENT_NAME);
				
				//Nothing to do if status no longer pending
				String otherClientStatus = (String)payloadClient.get(KEY_CLIENT_STATUS);
				if ( !otherClientStatus.equals("authorised") ) {
					Log.getInstance().warn(String.format("Client '%s' (%s) not authorised", otherClientName, otherClientId, otherClientStatus));
				}
				
				String otherIdentityPublicKey = (String)payloadClient.get(KEY_CLIENT_IDENTITY_KEY);

				prop.setProperty(String.format("messagekey.%s.otherclientid", ephemeralKeyId), otherClientId);
				prop.setProperty(String.format("messagekey.%s.otheridentitykey", ephemeralKeyId), otherIdentityPublicKey);
				prop.setProperty(String.format("messagekey.%s.otherkeyid", ephemeralKeyId), otherEphemeralKeyId);
				prop.setProperty(String.format("messagekey.%s.otherkey", ephemeralKeyId), otherEphemeralPublicKey);

				//Generate shared secret and keypair
				WeaveKeyPair keyPair = get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, false);
				
				//Extract and validate client auth request message
				PayloadCipher cipher = new PayloadCipher();
				String content = cipher.decrypt((String)payload.get(KEY_MESSAGE_CONTENT), keyPair);
				JSONObject requestObject = null;
				try {
					JSONParser parser = new JSONParser();
					requestObject = (JSONObject)parser.parse(content);
				} catch (org.json.simple.parser.ParseException e) {
					Log.getInstance().error(e.getMessage());
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				if ( !validateClientAuthRequestJson(requestObject) ) {
					Log.getInstance().error(String.format("Invalid client auth request - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}
				if ( !(otherClientId.equals(requestObject.get(KEY_CLIENTAUTH_REQUEST_CLIENTID)) && otherClientName.equals(requestObject.get(KEY_CLIENTAUTH_REQUEST_NAME))) ) {
					Log.getInstance().error(String.format("Invalid client auth request - Id mismatch - clientA: '%s' (%s), clientB: '%s' (%s)", otherClientId, otherEphemeralKeyId, clientId, ephemeralKeyId));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				prop.setProperty(String.format("messagekey.%s.type", ephemeralKeyId), "clientauth");
				prop.setProperty(String.format("messagekey.%s.role", ephemeralKeyId), "responder");
				prop.setProperty(String.format("messagekey.%s.state", ephemeralKeyId), "responsepending");
				
				//Nothing to do if status not pending
				if ( !otherClientStatus.equals("pending") ) {
					Log.getInstance().error(String.format("Client '%s' (%s) status '%s'. Nothing to do", otherClientName, otherClientId, otherClientStatus));
					setMessageState(ephemeralKeyId, "closed", (float)wbo.getModified().doubleValue());
					continue;
				}

				JSONObject authObject = (JSONObject)requestObject.get(KEY_CLIENTAUTH_REQUEST_AUTH);
				String innerSalt = (String)authObject.get(KEY_AUTH_INNERSALT);
				String salt = (String)authObject.get(KEY_AUTH_SALT);
				String digest = (String)authObject.get(KEY_AUTH_DIGEST);
				prop.setProperty(String.format("messagekey.%s.clientauth.name", ephemeralKeyId), otherClientName);
				prop.setProperty(String.format("messagekey.%s.clientauth.innersalt", ephemeralKeyId), innerSalt);
				prop.setProperty(String.format("messagekey.%s.clientauth.salt", ephemeralKeyId), salt);
				prop.setProperty(String.format("messagekey.%s.clientauth.digest", ephemeralKeyId), digest);
				
				//FIXME Prompt user to authorise client
				
				updateClient();
				
				//Write config file
				try {
					writeConfig();
				} catch (IOException e) {
					throw new WeaveException("Couldn't write config file - " + e.getMessage());
				}
			}
		}
	}


	public void sendClientAuthResponse(String ephemeralKeyId, boolean authorised, String authCode) throws WeaveException {
		Log.getInstance().debug("sendClientAuthResponse()");

		if ( !validateClientConfig(prop) ) {
			throw new WeaveException("Client config invalid!");
		}
		
		//Get account and client details
		clientId   = prop.getProperty(KEY_CLIENT_CONFIG_CLIENTID);
		clientName = prop.getProperty(KEY_CLIENT_CONFIG_NAME);
		
		if ( wc.getStorageVersion() == StorageVersion.v5 ) {
			syncKey = ((WeaveStorageV5Params)wc.getClientParams()).syncKey;
		} else {
			throw new WeaveException(String.format("Storage version %s not supported", WeaveClient.storageVersionToString(wc.getStorageVersion())));
		}

		//FIXME - Support configurable authLevel of all, verified, none
		//String authLevel    = prop.getProperty("authlevel");
		String authLevel    = "all";
		
		String messageType  = prop.getProperty(String.format("messagekey.%s.type", ephemeralKeyId));
		String messageRole  = prop.getProperty(String.format("messagekey.%s.role", ephemeralKeyId));
		String messageState = prop.getProperty(String.format("messagekey.%s.state", ephemeralKeyId));

		if ( !(
			messageType.equalsIgnoreCase("clientauth")
			&&
			messageRole.equalsIgnoreCase("responder")
			&&
			messageState.equalsIgnoreCase("responsepending")
		)) {
			throw new WeaveException(String.format("Invalid state for client auth message '%s'", ephemeralKeyId));
		}
		
		//Get private and ephemeral keys
		String identityPrivateKey  = prop.getProperty("privatekey");
		String ephemeralPrivateKey = prop.getProperty(String.format("ephemeralkey.%s.privatekey", ephemeralKeyId));
		String ephemeralPublicKey  = prop.getProperty(String.format("ephemeralkey.%s.publickey", ephemeralKeyId));

		String otherClientName         = prop.getProperty(String.format("messagekey.%s.otherclientname", ephemeralKeyId));
		String otherClientId           = prop.getProperty(String.format("messagekey.%s.otherclientid", ephemeralKeyId));
		String otherIdentityPublicKey  = prop.getProperty(String.format("messagekey.%s.otheridentitykey", ephemeralKeyId));
		String otherEphemeralKeyId     = prop.getProperty(String.format("messagekey.%s.otherkeyid", ephemeralKeyId));
		String otherEphemeralPublicKey = prop.getProperty(String.format("messagekey.%s.otherkey", ephemeralKeyId));

		boolean verified = false;
		
		if (authorised) {
			verified = verifyClientAuthRequestAuthCode(ephemeralKeyId, authCode);
			if (!verified) {
				Log.getInstance().warn(String.format("Auth code verfication failed for client '%s' (%s)", otherClientName, otherClientId));
			}
		}

		//Build message WBO
		JSONObject msgObject = new JSONObject();
		msgObject.put(KEY_MESSAGE_VERSION, PROTO_MESSAGE_VERSION);
		msgObject.put(KEY_MESSAGE_SOURCE_CLIENTID, clientId);
		msgObject.put(KEY_MESSAGE_SOURCE_KEYID, ephemeralKeyId);
		msgObject.put(KEY_MESSAGE_SOURCE_KEY, ephemeralPublicKey);
		msgObject.put(KEY_MESSAGE_DESTINATION_CLIENTID, otherClientId);
		msgObject.put(KEY_MESSAGE_DESTINATION_KEYID, otherEphemeralKeyId);
		msgObject.put(KEY_MESSAGE_TYPE, MESSAGE_TYPE_CLIENTAUTH);
		msgObject.put(KEY_MESSAGE_SEQUENCE, 2);

		//Build client auth response
		JSONObject caResponseObject = new JSONObject();
		caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_CLIENTID, clientId);
		caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_NAME, clientName);
		
		if ( authorised && verified ) {
			caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_STATUS, "okay");
			caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_MESSAGE, "Client authentication request approved");			
			caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_SYNCKEY, syncKey);
		} else {
			caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_STATUS, "fail");
			caResponseObject.put(KEY_CLIENTAUTH_RESPONSE_MESSAGE, "Client authentication request declined");			
		}
		
		WeaveKeyPair keyPair = this.get3DHEKeyPair(identityPrivateKey, ephemeralPrivateKey, otherIdentityPublicKey, otherEphemeralPublicKey, false);
		PayloadCipher cipher = new PayloadCipher();
		msgObject.put(KEY_MESSAGE_CONTENT, cipher.encrypt(caResponseObject.toJSONString(), keyPair));
		
		//Build exfiomessage WBO
		WeaveBasicObject wboClientAuthResponse = new WeaveBasicObject(otherEphemeralKeyId, null, null, null, msgObject.toJSONString());
		
		wc.putItem(KEY_MESSAGE_COLLECTION, otherEphemeralKeyId, wboClientAuthResponse, false);
	}
	
	public void checkMessages() throws WeaveException {
		
		//FIXME - Generalise behaviour to handle arbitrary messages
		
		if ( isAuthorised() ) {
			pollForClientAuthRequest();
		} else {
			pollForClientAuthResponse();
		}
		
		updateClient();
	}


	//**********************************
	// CLI interface and helper methods
	//**********************************

	public static void printUsage(Options options) {
		System.out.println();
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "weaveclient", options );
	}
	
	public static void main( String[] args ) {
		
		String baseURL    = null;
		String username   = null;
		String password   = null;
		String synckey    = null;
		String loglevel   = null;
		
		WeaveClient.StorageVersion storageVersion = null;
		
		// Parse commandline arguments
		Options options = new Options();
		
		options.addOption("h", "help", false, "print this message");
		options.addOption("f", "config-file", true, "load config from file");
		options.addOption("a", "account", true, "load config for account");
		options.addOption("p", "password", true, "password");
		options.addOption("i", "auth-init", true, "reset client authorisation. WARNING all clients will need to re-authenticate");	
		options.addOption("j", "auth-client", true, "request client authorisation");	
		options.addOption("x", "auth-reject", true, "reject client authorisation request");	
		options.addOption("o", "auth-approve", true, "approve client authorisation request");	
		options.addOption("c", "auth-code", true, "verification code for client authorisation");	
		options.addOption("m", "messages", false, "check for new messages");	
		options.addOption("l", "log-level", true, "set log level (trace|debug|info|warn|error)");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args );
		} catch( ParseException exp ) {
			System.err.println( "Parsing failed: " + exp.getMessage() );
			printUsage(options);
			System.exit(1);;
		}
		
		if ( cmd.hasOption('h') ) {
			// help
			printUsage(options);
			System.exit(0);
		}

		//Need to set log level BEFORE instansiating Logger
		loglevel = "warn";
		if ( cmd.hasOption('l') ) {
			loglevel = cmd.getOptionValue('l').toLowerCase();
			if ( !loglevel.matches("trace|debug|info|warn|error") ) {
				System.err.println("log level must be one of (trace|debug|info|warn|error)");
				System.exit(1);		
			}
		}
		Log.init(loglevel);

		//DEBUG only
		//Log.getInstance().warn("Log warn message");
		//Log.getInstance().info("Log info message");
		//Log.getInstance().debug("Log debug message");

		if ( !cmd.hasOption('p') ) {
			System.err.println("password is a required parameter");
			System.exit(1);
		}

		//Load client config
		Properties clientProp = new Properties();
		File clientConfig = null;
		
		try {
			if ( cmd.hasOption('f') ) {
				clientConfig = new File(cmd.getOptionValue('f'));
			} else if ( cmd.hasOption('a') ) {
				clientConfig = WeaveClient.buildAccountConfigPath(cmd.getOptionValue('a'));
			} else {
				//Use default config
				clientConfig = WeaveClient.buildAccountConfigPath();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		try {
			clientProp.load(new FileInputStream(clientConfig));
		} catch (IOException e) {
			System.err.println(String.format("Error opening client config file '%s'", clientConfig.getAbsolutePath()));
			System.exit(1);
		}

		//Set host and credential details
		baseURL  = clientProp.getProperty("server", null);
		username = clientProp.getProperty("username", null);
		password = cmd.getOptionValue('p', null);
		
		if (
			(baseURL == null || baseURL.isEmpty())
			||
			(username == null || username.isEmpty())
			||
			(password == null || password.isEmpty())
		) {
			System.err.println("server, username and password are required parameters");
			System.exit(1);
		}

		//Validate URI syntax
		try {
			URI.create(baseURL);
		} catch (IllegalArgumentException e) {
			System.err.printf("'%s' is not a valid URI, i.e. should be http(s)://example.com\n", baseURL);
			System.exit(1);
		}			

		//Auto-discover storage version
		try {
			WeaveBasicParams  adParams = new WeaveBasicParams();
			adParams.baseURL = baseURL;
			adParams.user    = username;
			adParams.password = password;				
			storageVersion = WeaveClient.autoDiscoverStorageVersion(adParams);
		} catch (WeaveException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}		

		WeaveClient weaveClient = null;
		
		if ( storageVersion == WeaveClient.StorageVersion.v5 ){
			//Only v5 is currently supported
			
			//Get synckey, default to null
			synckey = clientProp.getProperty("synckey", null);
			
			WeaveStorageV5Params weaveParams = new WeaveStorageV5Params();
			weaveParams.baseURL = baseURL;
			weaveParams.user = username;
			weaveParams.password = password;
			weaveParams.syncKey = synckey;
			
			try {
				weaveClient = WeaveClient.getInstance(storageVersion);
				weaveClient.init(weaveParams);	
			} catch (WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}			
		} else {
			System.err.println("Storage version not recognised");
			System.exit(1);
		}		

		if ( cmd.hasOption('j') ) {
			//Request client auth
			String clientName = cmd.getOptionValue('j');

			Log.getInstance().info(String.format("Requesting client auth for client '%s'", clientName));

			try {			
				Authorisation auth = new Authorisation(weaveClient, clientConfig);
				auth.authoriseClient(clientName, password);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Sent client auth request for client '%s'", clientName));
			System.exit(0);
		}

		if ( cmd.hasOption('o') ) {
			
			//Approve client auth request
			String messageKey = cmd.getOptionValue('o', null);
			String authCode   = cmd.getOptionValue('c', null);

			if ( authCode == null || authCode.isEmpty() ) {
				System.err.println("auth-code is a required argument for auth-approve");
				System.exit(1);
			}

			Log.getInstance().info(String.format("Approving client auth request '%s'", messageKey));

			try {			
				Authorisation auth = new Authorisation(weaveClient, clientConfig);
				auth.sendClientAuthResponse(messageKey, true, authCode);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			System.out.println(String.format("Approved client auth request '%s'", messageKey));
			System.exit(0);
			
		} else if ( cmd.hasOption('x') ) {
			
			//Reject client auth request
			String messageKey = cmd.getOptionValue('x');
			
			Log.getInstance().info(String.format("Rejecting client auth request '%s'", messageKey));

			try {			
				Authorisation auth = new Authorisation(weaveClient, clientConfig);
				auth.sendClientAuthResponse(messageKey, false, null);
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.out.println(String.format("Rejected client auth request '%s'", messageKey));
			System.exit(0);

		} else if ( cmd.hasOption('m') ) {

			Log.getInstance().info(String.format("Checking messages"));

			try {			
				Authorisation auth = new Authorisation(weaveClient, clientConfig);
				auth.checkMessages();
			} catch(WeaveException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
			
			System.exit(0);
		}

	}
}
