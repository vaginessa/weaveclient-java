package org.exfio.weave.account.fxa;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.json.simple.parser.ParseException;

import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.background.fxa.FxAccountClientException;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.DSACryptoImplementation;
import org.mozilla.gecko.browserid.JSONWebTokenUtils;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerToken;

import org.exfio.fxa.FxAccountClient;
import org.exfio.fxa.FxAccountKeys;
import org.exfio.fxa.BlockingTokenServerClientDelegate;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.WeaveAccount;
import org.exfio.weave.account.WeaveAccountParams;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.exfio.weave.crypto.WeaveSyncV5Crypto;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.storage.StorageContext;
import org.exfio.weave.storage.StorageParams;
import org.exfio.weave.storage.StorageV1_5;
import org.exfio.weave.storage.StorageV1_5Params;
import org.exfio.weave.storage.WeaveCollectionInfo;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Log;


//FIXME - Add support for account management
public class FxAccount extends WeaveAccount {
	private static final String LOG_TAG = "exfio.fxaccount";
	
	public static final String KEY_ACCOUNT_CONFIG_TOKENSERVER      = "tokenserver";
	private static final int KEY_PAIR_SIZE_IN_BITS_V1 = 1024;

	private URI    accountServer;
	private URI    tokenServer;
	private URI    storageURL;
	private String user;
	private String password;
	private FxAccountKeys fxaKeys;
	private FxAccountSyncToken syncToken;
	private WeaveKeyPair keyPair;
	
	public FxAccount() {
		super();
		this.version  = ApiVersion.v1_5;
		this.accountServer = null;
		this.tokenServer   = null;
		this.storageURL    = null;
		this.user          = null;
		this.password      = null;
		this.fxaKeys       = null;
		this.syncToken     = null;
		this.keyPair       = null;
		
		/*
		final String audience = fxAccount.getAudience();
		final String authServerEndpoint = fxAccount.getAccountServerURI();
		final String tokenServerEndpoint = fxAccount.getTokenServerURI();
		final URI tokenServerEndpointURI = new URI(tokenServerEndpoint);
		*/
	}

	public void init(WeaveAccountParams params) throws WeaveException {
		FxAccountParams initParams = (FxAccountParams)params;
		this.init(initParams.accountServer, initParams.user, initParams.password, initParams.tokenServer);		
	}
	
	public void init(String accountServer, String user, String password, String tokenServer) throws WeaveException {
		this.user        = user;
		this.password    = password;
		
		try {
			this.accountServer  = new URI(accountServer);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		
		try {
			this.tokenServer  = new URI(tokenServer);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
	}

	@Override
	public void createAccount(WeaveAccountParams params) throws WeaveException {
		Log.getInstance().debug("createAccount()");
		throw new WeaveException("createAccount() not yet implemented");
	}

	public void createAccount(String baseURL, String user, String password, String email) throws WeaveException {
		Log.getInstance().debug("createAccount()");
		throw new WeaveException("createAccount() not yet implemented");
	}

	@Override
	public String getStatus() {
		try {
			//Initialise storage client with account details
			StorageContext storageClient = new StorageV1_5();
			storageClient.init(this);
	
			//Initialise server meta data
			
			//FIXME - check crypto version first
			WeaveSyncV5Crypto cryptoClient = new WeaveSyncV5Crypto();
			cryptoClient.init(storageClient, this.getMasterKeyPair());
			if ( cryptoClient.isInitialised() ) {
				String status = "";
				String seperator = "";
				Map<String, WeaveCollectionInfo> colInfo = storageClient.getInfoCollections();
				for (String collection: colInfo.keySet()) {
					WeaveCollectionInfo info = colInfo.get(collection);
					status += String.format("%s%s - items: %s, modified: %s", seperator, info.getName(), info.getCount(), info.getModified());
					seperator = "\n";
				}
				return status;
				
			} else {
				return "Weave Sync server not initialised";
			}
			
		} catch (WeaveException e) {
			return String.format("Error querying Weave Sync status - %s", e.getMessage());
		}
	}
	
	@Override
	public WeaveAccountParams getAccountParams() {
		FxAccountParams params = new FxAccountParams();
		params.accountServer  = this.accountServer.toString();
		params.tokenServer    = this.tokenServer.toString();
		params.user           = this.user;
		params.password       = this.password;
		return params;
	}

	@Override
	public StorageParams getStorageParams() throws WeaveException {
 		StorageV1_5Params params = new StorageV1_5Params();
		params.storageURL     = this.getStorageUrl().toString();
		params.user           = this.user;
		params.hawkid         = this.syncToken.id;
		params.hawkkey        = this.syncToken.key.getBytes();
		return params;
	}

	/**
	 * build browserid assertion then then request sync auth token from token server
	 *
	 * GET /1.0/sync/1.5
	 * Host: token.services.mozilla.com
	 * Authorization: BrowserID <assertion>
	 * 
	 * The sync auth token is a JSON object with the following attributes
	 * {
	 *   "uid": 16999487, //FirefoxSync ID
	 *   "hashalg": "sha256",
	 *   "api_endpoint": "https://sync-176-us-west-2.sync.services.mozilla.com/1.5/16999487", //FirefoxSync storage endpoint
	 *   "key": "G_QwGbDXc6aYtXVrhmO5-ymQZbyZQoES8q75a-eFyik=", //Hawk auth key. NOTE: DO NOT decode
	 *   "id": "eyJub2RlIjogImh0dHBzOi8vc3luYy0xNzYtdXMtd2VzdC0yLnN5bmMuc2VydmljZXMubW96aWxsYS5jb20iLCAiZXhwaXJlcyI6IDE0MjIyNTQzNTMsICJzYWx0IjogIjdiYTQ0YyIsICJ1aWQiOiAxNjk5OTQ4N32olTf0a2mlUz9BezgYVASI_4hQ8nEl6VZVFM5RbwmQmA==" //Hawk auth id. NOTE: DO NOT decode
	 *   "duration": 3600,
	 * }
	 * 
	 */
	private FxAccountSyncToken getSyncAuthToken() throws WeaveException {
		try {
			return getSyncAuthToken(FxAccountUtils.getAudienceForURL(tokenServer.toString()));
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
	}
	
	private FxAccountSyncToken getSyncAuthToken(String audience) throws WeaveException {
				
		FxAccountClient client = new FxAccountClient();
		
		try {
			client.login(accountServer.toString(), user, password, true);			
			fxaKeys = client.getKeys();
		} catch (FxAccountClientException e) {
			throw new WeaveException(e);
		}
		
		Log.getInstance().debug(String.format("kA: %s, kB: %s", Hex.encodeHexString(fxaKeys.kA), Hex.encodeHexString(fxaKeys.kB)));
		
		//Generate BrowserID KeyPair
	    BrowserIDKeyPair keyPair;
	    try {
	      keyPair = generateKeyPair();
	    } catch (NoSuchAlgorithmException e) {
	    	throw new WeaveException("Couldn't generate BrowserID keypair - " + e.getMessage());
	    }

	    long certificateDuration = 5 * 60 * 1000; //5minutes
	    
	    String certificate = null;
	    try {
	    	certificate = client.signCertificate(keyPair, certificateDuration);
	    } catch (FxAccountClientException e) {
	    	throw new WeaveException("Error signing BrowserID certificate - " + e.getMessage());
	    }
	    
		String assertion = buildAssertion(keyPair, certificate, audience);

		//derive client state
	    String clientState = null;
	    try {
	    	clientState = FxAccountUtils.computeClientState(fxaKeys.kB);
	    } catch (NoSuchAlgorithmException e) {
	    	throw new WeaveException("Error generating client state - " + e.getMessage());
	    }
	    
		Executor executor  = Executors.newSingleThreadExecutor();
		BlockingTokenServerClientDelegate delegate = new BlockingTokenServerClientDelegate();
		
	    TokenServerClient tokenServerclient = new TokenServerClient(tokenServer, executor);
	    tokenServerclient.getTokenFromBrowserIDAssertion(assertion, true, clientState, delegate);
		
	    //IMPORTANT - block while async task completes
	    TokenServerToken token = null;
		Logger.debug(LOG_TAG, "Wait for blocking delegate");
	    try {
	    	delegate.getLatch().await();
	    	token = delegate.getToken();
	    } catch (Exception e) {
	    	throw new WeaveException("Error waiting for thread to complete - " + e.getMessage());
	    }
		Logger.debug(LOG_TAG, "Completed BlockingDecoratorRequestDelegate");

		this.syncToken = new FxAccountSyncToken(token);
		
		return syncToken;
	}

	private String buildAssertion(BrowserIDKeyPair keypair, String certificate, String audience) throws WeaveException {
		return buildAssertion(keypair, certificate, audience, JSONWebTokenUtils.DEFAULT_ASSERTION_ISSUER);
	}
	
	/**
	* Generate a new assertion for the given email address.
	*
	* This method lets you generate BrowserID assertions. Called with just
	* an email and audience it will generate an assertion from
	* login.persona.org.
	*/
	private String buildAssertion(BrowserIDKeyPair keyPair, String certificate, String audience, String issuer) throws WeaveException {
		Log.getInstance().debug("buildAssertion()");

		// We generate assertions with no iat and an exp after 2050 to avoid
		// invalid-timestamp errors from the token server.
		final long expiresAt = JSONWebTokenUtils.DEFAULT_FUTURE_EXPIRES_AT_IN_MILLISECONDS;
		String assertion;
		try {
			assertion = JSONWebTokenUtils.createAssertion(keyPair.getPrivate(), certificate, audience, issuer, null, expiresAt);
		} catch (
				NonObjectJSONException
				| IOException
				| ParseException
				| GeneralSecurityException
		e) {
			throw new WeaveException("Couldn't generate assertion - " + e.getMessage());
		}

		/*
	    try {
	      FxAccountUtils.pii(LOG_TAG, "Generated assertion: " + assertion);
	      ExtendedJSONObject a = JSONWebTokenUtils.parseAssertion(assertion);
	      if (a != null) {
	        FxAccountUtils.pii(LOG_TAG, "aHeader   : " + a.getObject("header"));
	        FxAccountUtils.pii(LOG_TAG, "aPayload  : " + a.getObject("payload"));
	        FxAccountUtils.pii(LOG_TAG, "aSignature: " + a.getString("signature"));
	        String certificate = a.getString("certificate");
	        if (certificate != null) {
	          ExtendedJSONObject c = JSONWebTokenUtils.parseCertificate(certificate);
	          FxAccountUtils.pii(LOG_TAG, "cHeader   : " + c.getObject("header"));
	          FxAccountUtils.pii(LOG_TAG, "cPayload  : " + c.getObject("payload"));
	          FxAccountUtils.pii(LOG_TAG, "cSignature: " + c.getString("signature"));
	          // Print the relevant timestamps in sorted order with labels.
	          HashMap<Long, String> map = new HashMap<Long, String>();
	          map.put(a.getObject("payload").getLong("iat"), "aiat");
	          map.put(a.getObject("payload").getLong("exp"), "aexp");
	          map.put(c.getObject("payload").getLong("iat"), "ciat");
	          map.put(c.getObject("payload").getLong("exp"), "cexp");
	          ArrayList<Long> values = new ArrayList<Long>(map.keySet());
	          Collections.sort(values);
	          for (Long value : values) {
	            FxAccountUtils.pii(LOG_TAG, map.get(value) + ": " + value);
	          }
	        } else {
	          FxAccountUtils.pii(LOG_TAG, "Could not parse certificate!");
	        }
	      } else {
	        FxAccountUtils.pii(LOG_TAG, "Could not parse assertion!");
	      }
	    } catch (Exception e) {
	      FxAccountUtils.pii(LOG_TAG, "Got exception dumping assertion debug info.");
	    }
	    */
		
		return assertion;

	}
	
	public static BrowserIDKeyPair generateKeyPair() throws NoSuchAlgorithmException {
		// New key pairs are always DSA.
		return DSACryptoImplementation.generateKeyPair(KEY_PAIR_SIZE_IN_BITS_V1);
	}

	
	@Override
	public URI getStorageUrl() throws WeaveException {
		Log.getInstance().debug("getStorageUrl()");
		
		if ( this.storageURL == null ) {
			FxAccountSyncToken authToken = this.getSyncAuthToken(this.tokenServer.toString());
			
			String storageURLString = authToken.endpoint;
			if ( !storageURLString.endsWith("/") ) {
				storageURLString += "/";
			}
			
			try {
				this.storageURL  = new URI(storageURLString);
			} catch (URISyntaxException e) {
				throw new WeaveException(e);
			}
		}		
		return storageURL;
	}
	
	@Override
	public WeaveKeyPair getMasterKeyPair() throws WeaveException {
		Log.getInstance().debug("getMasterKeyPair()");
		
		if ( keyPair == null ) {
			
			KeyBundle keyBundle = null;
			try {
				keyBundle = FxAccountUtils.generateSyncKeyBundle(fxaKeys.kB);
			} catch (
				InvalidKeyException
				| NoSuchAlgorithmException
				| UnsupportedEncodingException
			e) {
				throw new WeaveException("Couldn't generate sync key - " + e.getMessage());
			}
			keyPair = new WeaveKeyPair();
			keyPair.cryptKey = keyBundle.getEncryptionKey();
			keyPair.hmacKey  = keyBundle.getHMACKey();
			
			Log.getInstance().info( "Successfully generated sync key and hmac key");
			Log.getInstance().debug( String.format("kB: %s, crypt key: %s, crypt hmac: %s", Hex.encodeHexString(fxaKeys.kB), Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		}
		
		return keyPair;
	}
	
	@Override
	public Properties accountParamsToProperties(WeaveAccountParams params) {
		FxAccountParams fslParams = (FxAccountParams)params;
		
		Properties prop = new Properties();
		prop.setProperty(KEY_ACCOUNT_CONFIG_APIVERSION,  WeaveClientFactory.apiVersionToString(fslParams.getApiVersion()));
		prop.setProperty(KEY_ACCOUNT_CONFIG_SERVER,      fslParams.accountServer);
		prop.setProperty(KEY_ACCOUNT_CONFIG_TOKENSERVER, fslParams.tokenServer);
		prop.setProperty(KEY_ACCOUNT_CONFIG_USERNAME,    fslParams.user);
		
		return prop;
	}

	@Override
	public WeaveAccountParams propertiesToAccountParams(Properties prop) {
		FxAccountParams fslParams = new FxAccountParams();
		
		fslParams.accountServer = prop.getProperty(KEY_ACCOUNT_CONFIG_SERVER);
		fslParams.tokenServer   = prop.getProperty(KEY_ACCOUNT_CONFIG_TOKENSERVER);
		fslParams.user          = prop.getProperty(KEY_ACCOUNT_CONFIG_USERNAME);
		
		return fslParams;
	}

}
