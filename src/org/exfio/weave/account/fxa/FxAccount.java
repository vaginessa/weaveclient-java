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

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.json.simple.parser.ParseException;
import org.mozilla.gecko.background.fxa.FxAccountClientException;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.DSACryptoImplementation;
import org.mozilla.gecko.browserid.JSONWebTokenUtils;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerException;
import org.mozilla.gecko.tokenserver.TokenServerToken;
import org.exfio.fxa.FxAccountClient;
import org.exfio.fxa.FxAccountKeys;
import org.exfio.fxa.BlockingTokenServerClientDelegate;
import org.exfio.fxa.FxAccountSession;
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
import org.exfio.weave.util.Base64;
import org.exfio.weave.util.Hex;
import org.exfio.weave.util.Log;


//FIXME - Add support for account management
public class FxAccount extends WeaveAccount {
	
	public static final String KEY_ACCOUNT_CONFIG_TOKENSERVER   = "tokenserver";
	public static final String KEY_ACCOUNT_CONFIG_BROWSERIDCERT = "browseridcert";
	public static final String KEY_ACCOUNT_CONFIG_KB            = "kb";
	public static final String KEY_ACCOUNT_CONFIG_SYNCTOKEN     = "synctoken";
	
	private static final int KEY_PAIR_SIZE_IN_BITS_V1         = 1024;

	private URI    accountServer;
	private URI    tokenServer;
	private URI    storageURL;
	private String user;
	private String password;
	
	private String email;
	private FxAccountCertificate browserIdCertificate;
	
	private byte[] kB;
	private WeaveKeyPair keyPair;
	private FxAccountSyncToken syncToken;

	public FxAccount() {
		super();
		this.version  = ApiVersion.v1_5;
		this.accountServer = null;
		this.tokenServer   = null;
		this.storageURL    = null;
		this.user          = null;
		this.password      = null;
		this.email         = null;
		this.browserIdCertificate = null;
		this.kB            = null;
		
		this.keyPair       = null;
		this.syncToken     = null;

		/*
		final String audience = fxAccount.getAudience();
		final String authServerEndpoint = fxAccount.getAccountServerURI();
		final String tokenServerEndpoint = fxAccount.getTokenServerURI();
		final URI tokenServerEndpointURI = new URI(tokenServerEndpoint);
		*/
	}

	public void init(WeaveAccountParams params) throws WeaveException {
		FxAccountParams initParams = (FxAccountParams)params;
		
		if (
			(initParams.email != null && !initParams.email.isEmpty())
			&&
			(initParams.browserIdCertificate != null)
			&&
			(initParams.kB != null)
		) {
			
			this.init(
				initParams.accountServer,
				initParams.user,
				initParams.password,
				initParams.tokenServer,
				initParams.email,
				initParams.browserIdCertificate,
				initParams.kB,
				initParams.syncToken
			);
		} else {
			
			this.init(
				initParams.accountServer,
				initParams.user,
				initParams.password,
				initParams.tokenServer
			);
		}
	}

	public void init(String accountServer, String user, String password, String tokenServer, String email, FxAccountCertificate browserIdCertificate, byte[] kB,  FxAccountSyncToken syncToken) throws WeaveException {
		Log.getInstance().debug("FxAccount.init() with kB");
		initCore(accountServer, user, password, tokenServer);
		this.email                = email;
		this.kB                   = kB;
		
		//TODO - check for expiry of browser certificate
		this.browserIdCertificate = browserIdCertificate;
		
		if ( syncToken != null ) {
			Log.getInstance().debug(String.format("token expires: %d, current time: %d", syncToken.getExpires(), System.currentTimeMillis()));
		
			if ( syncToken.getExpires() > (System.currentTimeMillis() + 30 * 1000) ) {
				this.syncToken = syncToken;
			} else {
				Log.getInstance().info("Sync Token has expired");
				Log.getInstance().debug("Sync Token: " + syncToken.toJSONObject().toJSONString());
			}
		}
		
		getSyncAuthToken();
	}

	public void init(String accountServer, String user, String password, String tokenServer) throws WeaveException {
		Log.getInstance().debug("FxAccount.init()");
		initCore(accountServer, user, password, tokenServer);	
		getSyncAuthToken();
	}

	private void initCore(String accountServer, String user, String password, String tokenServer) throws WeaveException {

		if (
			(accountServer == null || accountServer.isEmpty())
			||
			(user == null || user.isEmpty())
			||
			(password == null || password.isEmpty())
			||
			(tokenServer == null || tokenServer.isEmpty())
		) {
			throw new WeaveException("accountServer, tokenServer, user and password are required paramaters");
		}
		
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
		
		this.email                = null;
		this.browserIdCertificate = null;
		this.kB                   = null;
		this.syncToken            = null;
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
			storageClient.init(this.getStorageParams());
	
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
		params.email          = this.email;
		params.browserIdCertificate = this.browserIdCertificate;
		params.kB             = this.kB;
		params.syncToken      = this.syncToken;
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
	 * build browserid assertion then request sync auth token from token server
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
		return getSyncAuthToken(audience, false);
	}
	
	private FxAccountSyncToken getSyncAuthToken(String audience, boolean clearCache) throws WeaveException {
		Log.getInstance().debug("getSyncAuthToken()");
		
		if ( clearCache ) {
			syncToken = null;
			browserIdCertificate = null;
		}
		
		if ( syncToken != null ) {
			return syncToken;
		}
		
		//We don't have a sync token so lets get one
		
		//1. Login to auth server and get a signed certificate
		if ( browserIdCertificate == null ) {
			getCertificate();
		}
		
		//2. Build assertion
		String assertion = buildAssertion(browserIdCertificate.getKeyPair(), browserIdCertificate.getCertificate(), audience);
		
		//3. Derive client state
	    String clientState = null;
	    try {
	    	clientState = FxAccountUtils.computeClientState(kB);
	    } catch (NoSuchAlgorithmException e) {
	    	throw new WeaveException("Error generating client state - " + e.getMessage());
	    }
	    
	    //4. Initialise thread pool
	    
	    //Not sure why but if ThreadFactory not used threads do not close
		//Executor executor  = Executors.newSingleThreadExecutor();
	    
		BasicThreadFactory factory = new BasicThreadFactory.Builder()
    	.namingPattern("fxaccount-%d")
    	.daemon(true)
    	.priority(Thread.MAX_PRIORITY)
    	.build();
		Executor executor  = Executors.newSingleThreadExecutor(factory);
		
		BlockingTokenServerClientDelegate delegate = new BlockingTokenServerClientDelegate();
		
	    TokenServerClient tokenServerclient = new TokenServerClient(tokenServer, executor);
	    
		Log.getInstance().debug("before getTokenFromBrowserIDAssertion()");
		long requestTime = System.currentTimeMillis();
		
		//5. Request sync token
	    tokenServerclient.getTokenFromBrowserIDAssertion(assertion, true, clientState, delegate);
		
	    //IMPORTANT - block while async task completes
	    TokenServerToken token = null;
		Log.getInstance().debug("Wait for blocking delegate");
	    try {
	    	delegate.getLatch().await();
	    	token = delegate.getToken();
	    } catch (TokenServerException.TokenServerInvalidCredentialsException e) {
	    	//Regenerate browser certificate

	    	if ( clearCache ) {
	    		//We just regenerated certificate.. what to do
		    	throw new WeaveException("Invalid credentials - " + e.getMessage());
	    	}
	    	
	    	Log.getInstance().info("Re-generate BrowserID Certificate");
	    	return this.getSyncAuthToken(audience, true);
	    	
	    } catch (TokenServerException e) {
	    	throw new WeaveException("Token server error - " + e.getMessage());
	    } catch (Exception e) {
	    	throw new WeaveException(String.format("Error waiting for thread to complete - '%s': %s", e.getClass().getName(), e.getMessage()));
	    }
		Log.getInstance().debug("Completed BlockingDecoratorRequestDelegate");

		syncToken = new FxAccountSyncToken(token);
		
		//Set expiry time
		syncToken.setExpires(requestTime + (token.duration * 1000));
		
		return syncToken;
	}

	private void getCertificate() throws WeaveException {
		Log.getInstance().debug("getCertificate()");
		
		FxAccountClient client = new FxAccountClient();
		FxAccountKeys fxaKeys  = null;
		
		try {
			FxAccountSession fxaSession = client.login(accountServer.toString(), user, password, true);			
			fxaKeys = client.getKeys();
			email   = fxaSession.remoteEmail;
			kB      = fxaKeys.kB;
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

	    //Mozilla Android app used duration of 12 * 60 * 60 * 1000
	    //long certificateDuration = 5 * 60 * 1000; //5minutes
	    long certificateDuration = 12 * 60 * 60 * 1000; //12 hours
	    
	    String certificate = null;
	    try {
	    	certificate = client.signCertificate(keyPair, certificateDuration);
	    } catch (FxAccountClientException e) {
	    	throw new WeaveException("Error signing BrowserID certificate - " + e.getMessage());
	    }
	    
	    browserIdCertificate = new FxAccountCertificate(keyPair, certificate);
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
			FxAccountSyncToken authToken = this.getSyncAuthToken();
			
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
				keyBundle = FxAccountUtils.generateSyncKeyBundle(kB);
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
			Log.getInstance().debug( String.format("kB: %s, crypt key: %s, crypt hmac: %s", Hex.encodeHexString(kB), Hex.encodeHexString(keyPair.cryptKey), Hex.encodeHexString(keyPair.hmacKey)));
		}
		
		return keyPair;
	}
	
	@Override
	public Properties accountParamsToProperties(WeaveAccountParams params, boolean includePassword) {
		FxAccountParams fslParams = (FxAccountParams)params;
		
		Properties prop = new Properties();
		
		//Core account params
		prop.setProperty(KEY_ACCOUNT_CONFIG_APIVERSION,  WeaveClientFactory.apiVersionToString(fslParams.getApiVersion()));
		prop.setProperty(KEY_ACCOUNT_CONFIG_TOKENSERVER,   fslParams.tokenServer);
		prop.setProperty(KEY_ACCOUNT_CONFIG_SERVER,        fslParams.accountServer);
		prop.setProperty(KEY_ACCOUNT_CONFIG_USERNAME,      fslParams.user);

		if (includePassword) {
			prop.setProperty(KEY_ACCOUNT_CONFIG_PASSWORD,      fslParams.password);
		}
		
		//Derived account params - can be generated from core params + password
		if ( fslParams.email != null && !fslParams.email.isEmpty() ) {
			prop.setProperty(KEY_ACCOUNT_CONFIG_EMAIL,         fslParams.email);
		}
		if ( fslParams.browserIdCertificate != null ) {
			prop.setProperty(KEY_ACCOUNT_CONFIG_BROWSERIDCERT, fslParams.browserIdCertificate.toJSONObject().toJSONString());
		}
		if ( fslParams.kB != null && fslParams.kB.length != 0 ) {
			prop.setProperty(KEY_ACCOUNT_CONFIG_KB, Base64.encodeBase64String(fslParams.kB));
		}
		if ( fslParams.syncToken != null ) {
			prop.setProperty(KEY_ACCOUNT_CONFIG_SYNCTOKEN, fslParams.syncToken.toJSONObject().toJSONString());
		}
		
		return prop;
	}

	@Override
	public WeaveAccountParams propertiesToAccountParams(Properties prop) throws WeaveException {
		FxAccountParams fslParams = new FxAccountParams();

		//Core account params
		fslParams.accountServer        = prop.getProperty(KEY_ACCOUNT_CONFIG_SERVER);
		fslParams.tokenServer          = prop.getProperty(KEY_ACCOUNT_CONFIG_TOKENSERVER);
		fslParams.user                 = prop.getProperty(KEY_ACCOUNT_CONFIG_USERNAME);
		fslParams.password             = prop.getProperty(KEY_ACCOUNT_CONFIG_PASSWORD, null);
		
		//Derived acount params - can be generated from core params + password
		if ( prop.containsKey(KEY_ACCOUNT_CONFIG_EMAIL) ) {
			fslParams.email = prop.getProperty(KEY_ACCOUNT_CONFIG_EMAIL);
		}
		if ( prop.containsKey(KEY_ACCOUNT_CONFIG_BROWSERIDCERT) ) {
			try {
				ExtendedJSONObject jsonObject = ExtendedJSONObject.parseJSONObject(prop.getProperty(KEY_ACCOUNT_CONFIG_BROWSERIDCERT));
				fslParams.browserIdCertificate = FxAccountCertificate.fromJSONObject(jsonObject);
			} catch (NonObjectJSONException | IOException | ParseException e) {
				throw new WeaveException("Couldn't parse BrowserID certificate and key pair - " + e.getMessage());
			}
		}
		if ( prop.containsKey(KEY_ACCOUNT_CONFIG_KB) ) {
			fslParams.kB = Base64.decodeBase64(prop.getProperty(KEY_ACCOUNT_CONFIG_KB));
		}
		if ( prop.containsKey(KEY_ACCOUNT_CONFIG_SYNCTOKEN) ) {
			try {
				ExtendedJSONObject jsonObject = ExtendedJSONObject.parseJSONObject(prop.getProperty(KEY_ACCOUNT_CONFIG_SYNCTOKEN));
				fslParams.syncToken = FxAccountSyncToken.fromJSONObject(jsonObject);
			} catch (NonObjectJSONException | IOException | ParseException e) {
				throw new WeaveException("Couldn't parse Sync Token - " + e.getMessage());
			}
		}
		
		return fslParams;
	}

}
