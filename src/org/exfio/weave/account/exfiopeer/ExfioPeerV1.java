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
package org.exfio.weave.account.exfiopeer;

import java.lang.AssertionError;
import java.lang.Math;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.codec.binary.Base32;
import org.exfio.weave.WeaveException;
import org.exfio.weave.account.exfiopeer.ClientAuthRequestMessage.ClientAuthVerifier;
import org.exfio.weave.account.exfiopeer.comm.Client;
import org.exfio.weave.account.exfiopeer.comm.Comms;
import org.exfio.weave.account.exfiopeer.comm.Message;
import org.exfio.weave.account.exfiopeer.comm.NoPublishedKeysException;
import org.exfio.weave.account.exfiopeer.comm.StorageNotFoundException;
import org.exfio.weave.account.exfiopeer.comm.Message.MessageSession;
import org.exfio.weave.account.exfiopeer.crypto.PBKDF2;
import org.exfio.weave.account.legacy.WeaveSyncV5AccountParams;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;


public class ExfioPeerV1 {

	public static final String MESSAGE_TYPE_CLIENTAUTHREQUEST  = "clientauthrequest";
	public static final String MESSAGE_TYPE_CLIENTAUTHRESPONSE = "clientauthresponse";

	//ExfioPeerV1 config
	public static final String KEY_CLIENT_CONFIG_AUTHSTATUS  = "clientauth.status";
	public static final String KEY_CLIENT_CONFIG_AUTHCODE    = "clientauth.authcode";
	public static final String KEY_CLIENT_CONFIG_AUTHBY      = "clientauth.authby";
	public static final String KEY_CLIENT_CONFIG_AUTHSYNCKEY = "clientauth.synckey";
	
	//PBKDF2
	//Ideally more iterations should be used, i.e. 8000, however as of 2014-10-09 processing time is prohibitive
	public static final int PBKDF2_DEFAULT_ITERATIONS = 2000;
	public static final int PBKDF2_DEFAULT_LENGTH     = 128;
	
	private WeaveClient wc;
	private Comms comms;

	@lombok.Getter @lombok.Setter private int pbkdf2Iterations = PBKDF2_DEFAULT_ITERATIONS;
	@lombok.Getter @lombok.Setter private int pbkdf2Length     = PBKDF2_DEFAULT_LENGTH;

	@lombok.Getter private String authCode;
	@lombok.Getter private String syncKey;
	@lombok.Getter private String authStatus;
	@lombok.Getter private String authBy;
		
	public ExfioPeerV1(WeaveClient wc) {
		this.wc       = wc;
		this.comms    = new Comms(wc);
		
		this.authStatus = null;
		this.authCode   = null;
		this.authBy     = null;
		this.syncKey    = null;
	}

	public ExfioPeerV1(WeaveClient wc, String database) {	
		try {
			Connection jdbcDb = DriverManager.getConnection("jdbc:sqlite:" + database);
			init(wc, jdbcDb);
		} catch (SQLException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public ExfioPeerV1(WeaveClient wc, Connection db) {
		init(wc, db);
	}

	private void init(WeaveClient wc, Connection db) {
		this.wc    = wc;
		this.comms = new Comms(wc, db);
		
		try {
			authStatus = comms.getProperty(KEY_CLIENT_CONFIG_AUTHSTATUS, null);
			authCode   = comms.getProperty(KEY_CLIENT_CONFIG_AUTHCODE, null);
			authBy     = comms.getProperty(KEY_CLIENT_CONFIG_AUTHBY, null);
			syncKey    = comms.getProperty(KEY_CLIENT_CONFIG_AUTHSYNCKEY, null);		
		} catch (WeaveException e){
			throw new AssertionError(String.format("Error loading client auth properties - %s", e.getMessage()));
		}

		java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}
	
	@SuppressWarnings("unused")
	private String getWeavePassword() throws WeaveException {
		String password = null;
		if ( wc.getStorageVersion() == WeaveClientFactory.StorageVersion.v5 ) {
			WeaveSyncV5AccountParams params = (WeaveSyncV5AccountParams)wc.getClientParams();
			password = params.password;
		} else {
			throw new WeaveException(String.format("Storage version '%s' not supported", WeaveClientFactory.storageVersionToString(wc.getStorageVersion())));
		}
		return password;
	}

	private byte[] generatePasswordSalt() {
		//Generate 128 bit (16 byte) salt
		PBKDF2 pbkdf = new PBKDF2();
		return pbkdf.generatePBKDF2Salt(16);
	}

	private byte[] generateAuthSalt() {
		//Generate 128 bit (16 byte) salt
		PBKDF2 pbkdf = new PBKDF2();
		return pbkdf.generatePBKDF2Salt(16);
	}

	private String generatePasswordHash(String password, byte[] salt) {
		//Generate 128 bit (16 byte) digest
		PBKDF2 pbkdf = new PBKDF2();
		return pbkdf.generatePBKDF2Digest(password, salt, pbkdf2Iterations, pbkdf2Length);
	}

	private String generateAuthDigest(String cleartext, byte[] salt) {
		//Generate 128 bit (16 byte) digest
		PBKDF2 pbkdf = new PBKDF2();
		return pbkdf.generatePBKDF2Digest(cleartext, salt, pbkdf2Iterations, pbkdf2Length);
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

	/**
	 * buildClientAuthVerifier
	 * @param authCode
	 * @return ClientAuthVerifier
	 * 
	 * To increase difficulty of MiTM attacks concatenate authcode and password hash and use PBKDF2 to make brute forcing expensive
	 * IMPORTANT: If password is known by attacker it would be trivial to brute force authcode
	 * 
	 */
	private ClientAuthVerifier buildClientAuthVerifier(String authCode, String password) {
		Log.getInstance().debug("buildClientAuthVerifier()");
		
		byte[] passwordSaltBin = generatePasswordSalt();
		String passwordSalt = Base64.encodeBase64String(passwordSaltBin);			
		String passwordHash = generatePasswordHash(password, passwordSaltBin);
		
		byte[] authSaltBin = generateAuthSalt();
		String authSalt = Base64.encodeBase64String(authSaltBin);
		String authDigest = generateAuthDigest(authCode + passwordHash, authSaltBin);
		
		ClientAuthVerifier authVerifier = new ClientAuthVerifier();
		authVerifier.setInnerSalt(passwordSalt);
		authVerifier.setSalt(authSalt);
		authVerifier.setDigest(authDigest);

		Log.getInstance().debug(String.format("digest: %s, salt: %s, innersalt: %s, authcode: %s, password: %s", authVerifier.getDigest(), authVerifier.getSalt(), authVerifier.getInnerSalt(), authCode, password));

		return authVerifier;
	}
	
	/**
	 * verifyClientAuthRequestAuthCode
	 * @param cav
	 * @param authCode
	 * @param password
	 * @return boolean
	 * 
	 * Verifies that client auth request has NOT been intercepted by a MiTM attach.
	 * Note caveats above in buildClientAuthVerifier()
	 *  
	 */
	private boolean verifyClientAuthRequestAuthCode(ClientAuthVerifier cav, String authCode, String password) {
		Log.getInstance().debug("verifyClientAuthRequestAuthCode()");
		
		Log.getInstance().debug(String.format("digest: %s, salt: %s, innersalt: %s, authcode: %s, password: %s", cav.getDigest(), cav.getSalt(), cav.getInnerSalt(), authCode, password));
		
		byte[] passwordSaltBin = Base64.decodeBase64(cav.getInnerSalt());
		String passwordHash = generatePasswordHash(password, passwordSaltBin);
		
		byte[] authSaltBin = Base64.decodeBase64(cav.getSalt());
		String authDigest = generateAuthDigest(authCode.toUpperCase() + passwordHash, authSaltBin);
		
		if ( authDigest.equals(cav.getDigest()) ) {
			Log.getInstance().info("Client auth verification succeeded");
			return true;
		} else {
			Log.getInstance().info("Client auth verification failed");
			return false;
		}
	}

	private String getAuthorisedSyncKey() {
		if ( wc.getStorageVersion() == StorageVersion.v5 ) {
			return ((WeaveSyncV5AccountParams)wc.getClientParams()).syncKey;
		} else {
			return null;
		}
	}

	private boolean isAuthorised() {
		//FIXME - refactor ExfioPeerV1 as WeaveAccount
		//return wc.isAuthorised();
		return false;
	}

	public boolean isInitialised() throws WeaveException {
		return comms.isInitialised();
	}
	
	public void initClientAuth(String clientName, String database) throws WeaveException {
		Log.getInstance().debug("initClientAuth()");

		if ( comms.isInitialised() && !isAuthorised() ) {
			throw new WeaveException("Must be an authorised client to reset client auth collections");
		}
		
		//FIXME - Rotate sync key and revoke auth status for other clients (preserve clientId?)
		//For now clean clientauth collections and recreate client from scratch
		
		comms.initServer();
		comms.initClient(clientName, true, database);
		
		//Set clientauth properties
		authStatus = "authorised";
		syncKey    = getAuthorisedSyncKey();
		
		comms.setProperty(KEY_CLIENT_CONFIG_AUTHSTATUS, "authorised");
		comms.setProperty(KEY_CLIENT_CONFIG_AUTHSYNCKEY, syncKey);
		comms.setProperty(KEY_CLIENT_CONFIG_AUTHBY, "self");
	}

	public void requestClientAuth(String clientName, String password, String database) throws WeaveException {
		Log.getInstance().debug("authoriseClient()");

		//TODO - Warn user if client has already been initialised

		//Initialise database and create client record
		comms.initClient(clientName, isAuthorised(), database);
		
		if ( isAuthorised() ) {

			//Client already authorised
			authStatus = "authorised";
			syncKey    = getAuthorisedSyncKey();
			
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHSTATUS, "authorised");
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHSYNCKEY, syncKey);
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHBY, "self");
			return;
		}
		
		//Generate and store auth code
		authStatus = "pending";
		authCode   = generateAuthCode();
		ClientAuthVerifier cav = buildClientAuthVerifier(authCode, password);
		
		comms.setProperty(KEY_CLIENT_CONFIG_AUTHSTATUS, authStatus);
		comms.setProperty(KEY_CLIENT_CONFIG_AUTHCODE, authCode);
		
		//Get existing clients and create registration for each
		Client[] clients = comms.getClients();
		
		for (int i = 0; i < clients.length; i++) {
										
			if ( clients[i].isSelf() ) {
				//Ignore our own client record
				Log.getInstance().info(String.format("Client '%s' (%s) is self. Skipping...", clients[i].getClientName(),clients[i].getClientId()));
				continue;
			}

			if ( !clients[i].getStatus().equalsIgnoreCase("authorised") ) {
				//Ignore unauthorised clients
				Log.getInstance().info(String.format("Client '%s' (%s) is not authorised. Skipping...", clients[i].getClientName(),clients[i].getClientId()));
				continue;
			}
		
			//Create new session for client
			MessageSession session = null;
			try {
				session = comms.createOutgoingMessageSession(clients[i].getClientId());
			} catch (NoPublishedKeysException e) {
				Log.getInstance().warn(e.getMessage());
				continue;
			}
			
	        ClientAuthRequestMessage msg = new ClientAuthRequestMessage(comms.getNewMessage(session));	        
	        msg.setSequence(1);
	        
			//Build client auth request
			msg.setClientId(comms.getClientId());
			msg.setClientName(comms.getClientName());
			msg.setAuth(cav);
			
			@SuppressWarnings("unused")
			Double modified = comms.sendMessage(msg);
		}		
	}

	private void sendClientAuthResponse(String sessionId, boolean authorised, String authCode, String password) throws WeaveException, AuthcodeVerificationFailedException {
		Log.getInstance().debug("sendClientAuthResponse()");
				
		Message[] sessMsgs = null;
		try {
			sessMsgs = comms.getMessagesBySession(sessionId);
		} catch (StorageNotFoundException e) {
			throw new WeaveException(String.format("Couldn't get messages for session '%s'", sessionId));
		}
		if ( sessMsgs.length != 1 ) {
			throw new WeaveException(String.format("Multiple messages in session '%s'. Only one message expected.", sessionId));
		}
		if ( !sessMsgs[0].getMessageType().equalsIgnoreCase("clientauthrequest") ) {
			throw new WeaveException(String.format("Message '%s' is type '%s'. Client auth request message expected.", sessMsgs[0].getMessageId(), sessMsgs[0].getMessageType()));
		}
		
		ClientAuthRequestMessage caRequestMsg = new ClientAuthRequestMessage(sessMsgs[0]);
		
		if ( !caRequestMsg.getSession().getState().equalsIgnoreCase("responsepending") ) {
			throw new WeaveException(String.format("Invalid state for client auth message '%s'", sessionId));
		}
		
		if (authorised && !verifyClientAuthRequestAuthCode(caRequestMsg.getAuth(), authCode, password) ) {
			throw new AuthcodeVerificationFailedException(String.format("Auth code verfication failed for client '%s' (%s)", caRequestMsg.getClientName(), caRequestMsg.getClientId()));
		}

		ClientAuthResponseMessage caResponseMsg = new ClientAuthResponseMessage(comms.getNewMessage(caRequestMsg.getMessageSessionId()));
		
		caResponseMsg.setClientId(comms.getClientId());
		caResponseMsg.setClientName(comms.getClientName());
				
		if ( authorised ) {
			caResponseMsg.setStatus("okay");
			caResponseMsg.setMessage("Client authentication request approved");
			caResponseMsg.setSyncKey(syncKey);
		} else {
			caResponseMsg.setStatus("fail");
			caResponseMsg.setMessage("Client authentication request declined");
		}
		
		comms.sendMessage(caResponseMsg);
		
	}

	public void approveClientAuth(String sessionId, String authCode) throws WeaveException {
		approveClientAuth(sessionId, authCode, wc.getClientParams().password);
	}

	public void approveClientAuth(String sessionId, String authCode, String password) throws WeaveException {
		sendClientAuthResponse(sessionId, true, authCode, password);
	}

	public void rejectClientAuth(String sessionId) throws WeaveException {
		sendClientAuthResponse(sessionId, false, null, null);
	}
	
	public Message[] getPendingClientAuthMessages() throws WeaveException {
		
		List<Message> msgPending = new ArrayList<Message>(Arrays.asList(comms.getPendingMessages(MESSAGE_TYPE_CLIENTAUTHREQUEST)));
		
		ListIterator<Message> iterPending = msgPending.listIterator();
		while ( iterPending.hasNext() ) {
			Message msg = iterPending.next();

			Client otherClient = comms.getClient(msg.getSourceClientId());
			
			//Nothing to do if status no longer pending
			if ( !otherClient.getStatus().equals("pending") ) {
				Log.getInstance().info(String.format("Client '%s' (%s) status '%s'. Discarding client auth request", otherClient.getClientName(), otherClient.getClientId(), otherClient.getStatus()));
				comms.updateMessageSession(msg.getMessageSessionId(), "closed");
				//remove message from pending list
				iterPending.remove();
				continue;
			}

			//Re instansiate as ClientAuthRequestMessage
			iterPending.set(new ClientAuthRequestMessage(msg));
		}
		return msgPending.toArray(new Message[0]);
	}
	
	public Message[] processClientAuthMessages() throws WeaveException {
		Log.getInstance().debug("processClientAuthMessages()");

		comms.checkMessages();
		
		List<Message> msgUnread = new ArrayList<Message>(Arrays.asList(comms.getUnreadMessages()));

		Iterator<Message> iterUnread = msgUnread.listIterator();
		while ( iterUnread.hasNext() ) {
			Message msg = iterUnread.next();
			
			if ( msg.getSession().getState().equals("closed") ) {
				Log.getInstance().warn(String.format("Message could not be processed - session '%s' is closed:", msg.getMessageSessionId()));
			}
			
			if ( msg.getMessageType().equals(MESSAGE_TYPE_CLIENTAUTHREQUEST) ) {
				processClientAuthRequest(new ClientAuthRequestMessage(msg));
			} else if ( msg.getMessageType().equals(MESSAGE_TYPE_CLIENTAUTHRESPONSE) ) {
				processClientAuthResponse(new ClientAuthResponseMessage(msg));	
			}
		}
	
		return getPendingClientAuthMessages();
	}

	public void processClientAuthResponse(ClientAuthResponseMessage msg) throws WeaveException {
		Log.getInstance().debug("processClientAuthResponse()");

		MessageSession session = msg.getSession();
				
		if ( !msg.getClientId().equals(session.getOtherClientId()) ) {
			Log.getInstance().error(String.format("Invalid client auth response, client Id mismatch '%s' - sessionid: '%s', client: '%s' (%s)", msg.getClientId(), session.getSessionId(), msg.getClientName(), msg.getClientId()));
			comms.updateMessageSession(session.getSessionId(), "closed");
			throw new WeaveException("Invalid client auth response");
		}

		if ( !msg.getStatus().matches("(?i)okay|fail") ) {
			Log.getInstance().error(String.format("Invalid client auth response, unknown status '%s' - sessionid: '%s', client: '%s' (%s) is unknown - %s: %s", msg.getStatus(), session.getSessionId(), msg.getClientName(), msg.getClientId()));					
			comms.updateMessageSession(session.getSessionId(), "closed");
			throw new WeaveException("Invalid client auth response");
		}

		Log.getInstance().info(String.format("Client auth response from client '%s' (%s) - %s: %s", msg.getClientName(), msg.getClientId(), msg.getStatus(), msg.getMessage()));

		if ( msg.getStatus().equalsIgnoreCase("okay") ) {
			
			if ( msg.getSyncKey() == null || msg.getSyncKey().length() == 0 ) {
				Log.getInstance().error(String.format("Invalid client auth response, synckey missing - sessionid: '%s', client: '%s' (%s) is unknown - %s: %s", msg.getStatus(), session.getSessionId(), msg.getClientName(), msg.getClientId()));					
				comms.updateMessageSession(session.getSessionId(), "closed");
				throw new WeaveException("Invalid client auth response");
			}
			
			//Success!
			authStatus = "authorised";
			syncKey = msg.getSyncKey();
			authBy  = msg.getClientName();
			
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHSTATUS, authStatus);
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHSYNCKEY, syncKey);
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHBY, authBy);
			
			//Update client record
			comms.getClientSelf().setStatus(authStatus);
			comms.updateClient();
			
		} else {
			//Do nothing
		}

		//Set message to read and close session
		comms.updateMessage(msg.getMessageId(), true, false);
		comms.updateMessageSession(session.getSessionId(), "closed");
		
	}

	public void processClientAuthRequest(ClientAuthRequestMessage msg) throws WeaveException {
		Log.getInstance().debug("processClientAuthRequet()");
		
		MessageSession caSession = msg.getSession();
		
		Client otherClient = comms.getClient(caSession.getOtherClientId());

		if ( !msg.getClientId().equals(caSession.getOtherClientId()) ) {
			Log.getInstance().error(String.format("Invalid client auth request, client Id mismatch '%s' - sessionid: '%s', client: '%s' (%s)", msg.getClientId(), caSession.getSessionId(), msg.getClientName(), msg.getClientId()));
			comms.updateMessageSession(caSession.getSessionId(), "closed");
			throw new WeaveException("Invalid client auth request");
		}

		//Nothing to do if status no longer pending
		if ( !otherClient.getStatus().equals("pending") ) {
			Log.getInstance().warn(String.format("Client '%s' (%s) status '%s'. Nothing to do", otherClient.getClientName(), otherClient.getClientId(), otherClient.getStatus()));
			comms.updateMessageSession(caSession.getSessionId(), "closed");
		}

		//Set message to read
		comms.updateMessage(msg.getMessageId(), true, false);
						
	}	
}
