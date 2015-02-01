package org.exfio.fxa;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.http.impl.pool.BasicConnFactory;
import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.background.fxa.FxAccountClient10.TwoKeys;
import org.mozilla.gecko.background.fxa.FxAccountClient20;
import org.mozilla.gecko.background.fxa.FxAccountClientException;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.background.fxa.PasswordStretcher;
import org.mozilla.gecko.background.fxa.FxAccountClient20.LoginResponse;
import org.mozilla.gecko.background.fxa.QuickPasswordStretcher;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;

/**
 * FxAccountClient
 * 
 * Multi-threaded support is not a goal of this FxA implementation hence we decorate RequestDelegates with a
 * blocking delegate so as maintain synchronous process flow
 * 
 */
public class FxAccountClient {
	private static final String LOG_TAG = "exfio.fxaclient";
	
	private Executor executor;
	
	private FxAccountClient20 fxaClient;
	private FxAccountSession fxaSession;
	
	private byte[] quickStretchedPW;
	private byte[] unwrapkB;
	private byte[] kA;
	private byte[] kB;
	
	public FxAccountClient() {
		
		BasicThreadFactory factory = new BasicThreadFactory.Builder()
	    	.namingPattern("fxaclient-%d")
	    	.daemon(true)
	    	.priority(Thread.MAX_PRIORITY)
	    	.build();
		executor  = Executors.newSingleThreadExecutor(factory);
		
		fxaClient = null;
		fxaSession = null;
		
		quickStretchedPW = null;
		unwrapkB = null;
		kA = null;
		kB = null;
	}
	
	public void login(String server, String username, String password) throws FxAccountClientException {
		login(server, username, password, true);
	}	

	public void login(String server, String username, String password, boolean fetchKeys) throws FxAccountClientException {
		Logger.debug(LOG_TAG, "login()");
		
		//Get strings as UTF8 encoded byte arrays
		byte[] usernameUTF8 = null;
		try {
			usernameUTF8 = username.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError("Couldn't encode string as UTF-8 - " + e.getMessage());
		}
		
		fxaClient = new FxAccountClient20(server, executor);

		//Prepare login params
		PasswordStretcher passwordStretcher = new QuickPasswordStretcher(password);
		Map<String, String> queryParameters = new HashMap<String, String>();
	    BlockingRequestDelegate<LoginResponse> delegate = new BlockingRequestDelegate<LoginResponse>();
	    
	    fxaClient.login(usernameUTF8, passwordStretcher, fetchKeys, queryParameters, delegate);

	    //IMPORTANT - block while async task completes
	    LoginResponse response = null;
		Logger.debug(LOG_TAG, "Wait for blocking delegate");
	    try {
	    	delegate.getLatch().await();
	    	response = delegate.getResult();
	    } catch (InterruptedException e) {
	    	throw new FxAccountClientException("Error waiting for thread to complete - " + e.getMessage());
	    }
		Logger.debug(LOG_TAG, "Completed BlockingDecoratorRequestDelegate");
		
		fxaSession = new FxAccountSession(response);
		
		try {
			quickStretchedPW = passwordStretcher.getQuickStretchedPW(fxaSession.remoteEmail.getBytes("UTF-8"));
			unwrapkB = FxAccountUtils.generateUnwrapBKey(quickStretchedPW);
		} catch (UnsupportedEncodingException e) {
			throw new FxAccountClientException("Couldn't derive client side keys - " + e.getMessage());
		} catch (GeneralSecurityException e) {
			throw new FxAccountClientException("Couldn't derive client side keys - " + e.getMessage());
		}
	}
	
	public void close() {
		fxaClient = null;
		fxaSession = null;
		executor = null;
	}
	
	public FxAccountKeys getKeys() throws FxAccountClientException {
		Logger.debug(LOG_TAG, "getKeys()");
	
		if ( fxaClient == null ) {
			throw new FxAccountClientException("FxAccountClient not initialised");
		}
		
		if ( kA == null || kB == null ) {
			
			//Use simple blocking request delegate
		    BlockingRequestDelegate<TwoKeys> delegate = new BlockingRequestDelegate<TwoKeys>();
		    
		    fxaClient.keys(fxaSession.keyFetchToken, delegate);
	
		    //IMPORTANT - block while async task completes
		    TwoKeys keys = null;
			Logger.debug(LOG_TAG, "Wait for blocking delegate");
		    try {
		    	delegate.getLatch().await();
		    	keys = delegate.getResult();
		    } catch (InterruptedException e) {
		    	throw new FxAccountClientException("Error waiting for thread to complete - " + e.getMessage());
		    }
			Logger.debug(LOG_TAG, "Completed BlockingDecoratorRequestDelegate");
	
	        kA = keys.kA;
			kB = FxAccountUtils.unwrapkB(unwrapkB, keys.wrapkB);
		}
		
		return new FxAccountKeys(kA, kB);
	}
	
	  public String signCertificate(BrowserIDKeyPair keyPair, long durationInMilliseconds) throws FxAccountClientException {
		Logger.debug(LOG_TAG, "signCertificate()");
	
		if ( fxaClient == null ) {
			throw new FxAccountClientException("FxAccountClient not initialised");
		}

		//Use simple blocking request delegate
	    BlockingRequestDelegate<String> delegate = new BlockingRequestDelegate<String>();

	    fxaClient.sign(fxaSession.sessionToken, keyPair.getPublic().toJSONObject(), durationInMilliseconds, delegate);
	    
	    //IMPORTANT - block while async task completes
	    String certificate = null;
		Logger.debug(LOG_TAG, "Wait for blocking delegate");
	    try {
	    	delegate.getLatch().await();
	    	certificate = delegate.getResult();
	    } catch (InterruptedException e) {
	    	throw new FxAccountClientException("Error waiting for thread to complete - " + e.getMessage());
	    }
		Logger.debug(LOG_TAG, "Completed BlockingDecoratorRequestDelegate");

		//ExtendedJSONObject c = JSONWebTokenUtils.parseCertificate(certificate);

		return certificate;
	}
	
}
