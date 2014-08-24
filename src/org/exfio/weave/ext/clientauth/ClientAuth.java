package org.exfio.weave.ext.clientauth;

import java.lang.AssertionError;
import java.lang.Math;
import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base32;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.NotFoundException;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.client.WeaveClientV5Params;
import org.exfio.weave.client.WeaveClientFactory.StorageVersion;
import org.exfio.weave.ext.comm.Client;
import org.exfio.weave.ext.comm.Comms;
import org.exfio.weave.ext.comm.Message;
import org.exfio.weave.ext.comm.CommsStorage;
import org.exfio.weave.ext.comm.Message.MessageSession;
import org.exfio.weave.ext.crypto.PBKDF2;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;


public class ClientAuth {

	public static final String MESSAGE_TYPE_CLIENTAUTHREQUEST  = "clientauthrequest";
	public static final String MESSAGE_TYPE_CLIENTAUTHRESPONSE = "clientauthresponse";

	//ClientAuth config
	public static final String KEY_CLIENT_CONFIG_AUTHCODE    = "clientauth.authcode";
	public static final String KEY_CLIENT_CONFIG_AUTHBY      = "clientauth.authby";
	public static final String KEY_CLIENT_CONFIG_AUTHSYNCKEY = "clientauth.synckey";
			
	private WeaveClient wc;
	private Comms comms;
	private Connection db;
		
	@lombok.Getter private String authCode;
	@lombok.Getter private String syncKey;
		
	public ClientAuth(WeaveClient wc) {
		this.wc       = wc;
		this.db       = null;
		this.comms    = new Comms(wc);
	}

	public ClientAuth(WeaveClient wc, String database) {	
		try {
			Connection jdbcDb = DriverManager.getConnection("jdbc:sqlite:" + database);
			init(wc, jdbcDb);
		} catch (SQLException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public ClientAuth(WeaveClient wc, Connection db) {
		init(wc, db);
	}

	private void init(WeaveClient wc, Connection db) {
		this.wc              = wc;
		this.db              = db;
		this.comms = new Comms(wc, db);		
				
		java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}
	
	//FIXME - Password is discarded as soon as it is used hence it is currently not possible to retrieve it from WeaveClient 
	private String getWeavePassword() throws WeaveException {
		String password = null;
		if ( wc.getStorageVersion() == WeaveClientFactory.StorageVersion.v5 ) {
			WeaveClientV5Params params = (WeaveClientV5Params)wc.getClientParams();
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
		return pbkdf.generatePBKDF2Digest(password, salt, 80000, 128);
	}

	private String generateAuthDigest(String cleartext, byte[] salt) {
		//Generate 128 bit (16 byte) digest
		PBKDF2 pbkdf = new PBKDF2();
		return pbkdf.generatePBKDF2Digest(cleartext, salt, 80000, 128);
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

	private boolean verifyClientAuthRequestAuthCode(String sessionId, String authCode) {
		//FIXME - Verify out-of-band authcode
		return true;
	}
	
	private boolean isAuthorised() {
		if ( wc.getStorageVersion() == StorageVersion.v5 ) {
			return ( ((WeaveClientV5Params)wc.getClientParams()).syncKey != null );
		} else {
			return false;
		}
	}
		
	//@SuppressWarnings("unchecked")
	public void authoriseClient(String clientName, String password, String database) throws WeaveException {
		Log.getInstance().debug("authoriseClient()");

		//FIXME - Warn user if client has already been initialised
		
		//Initialise database and create client record
		comms.initClient(clientName, isAuthorised(), database);
		db = comms.getDB();
		
		//Generate and store auth code
		authCode = generateAuthCode();
		
		try {
			CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_AUTHCODE, authCode);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't set property for key 'authcode' - " + e.getMessage());
		}
		
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
			MessageSession session = comms.createOutgoingMessageSession(clients[i].getClientId());
			
			
	        ClientAuthRequestMessage msg = new ClientAuthRequestMessage(comms.getNewMessage(session));	        
	        msg.setSequence(1);
	        
			//Build client auth request
			msg.setClientId(comms.getClientId());
			msg.setClientName(comms.getClientName());
			
			//Build auth verifier
			//To increase difficulty of MiTM attacks concatenate authcode and password hash and use PBKDF2 to make brute forcing expensive
			//IMPORTANT: If password is known by attacker it would be trivial to brute force authcode

			byte[] passwordSaltBin = generatePasswordSalt();
			String passwordSalt = Base64.encodeBase64String(passwordSaltBin);			
			String passwordHash = generatePasswordHash(password, passwordSaltBin);
			
			byte[] authSaltBin = generateAuthSalt();
			String authSalt = Base64.encodeBase64String(authSaltBin);
			String authDigest = generateAuthDigest(authCode + passwordHash, authSaltBin);
			
			ClientAuthRequestMessage.ClientAuthVerifier authVerifier = new ClientAuthRequestMessage.ClientAuthVerifier();
			authVerifier.setInnerSalt(passwordSalt);
			authVerifier.setSalt(authSalt);
			authVerifier.setDigest(authDigest);
			
			msg.setAuth(authVerifier);
			
			Double modified = comms.sendMessage(msg);
		}		
	}

	public void processClientAuthMessages() throws WeaveException {
		Log.getInstance().debug("processClientAuthMessages()");

		comms.checkMessages();
		
		Message[] msgs = comms.getUnreadMessages();

		for (int i = 0; i < msgs.length ; i++) {
			if ( msgs[i].getMessageType().equals(MESSAGE_TYPE_CLIENTAUTHREQUEST) ) {
				processClientAuthRequest(new ClientAuthRequestMessage(msgs[i]));
			} else if ( msgs[i].getMessageType().equals(MESSAGE_TYPE_CLIENTAUTHRESPONSE) ) {
				processClientAuthResponse(new ClientAuthResponseMessage(msgs[i]));	
			}
		}
	}

	public void processClientAuthResponse(ClientAuthResponseMessage msg) throws WeaveException {
		Log.getInstance().debug("processClientAuthResponse()");

		MessageSession session = msg.getSession();
				
		if ( !msg.getClientId().equals(session.getOtherClientId()) ) {
			Log.getInstance().error(String.format("Invalid client auth response, client Id mismatch '%s' - sessionid: '%s', client: '%s' (%s)", msg.getClientId(), session.getSessionId(), msg.getClientName(), msg.getClientId()));
			try {
				CommsStorage.updateMessageSession(db, session.getSessionId(), "closed");
			} catch (SQLException e) {
				throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
			}
			throw new WeaveException("Invalid client auth response");
		}

		if ( !msg.getStatus().matches("(?i)okay|fail") ) {
			Log.getInstance().error(String.format("Invalid client auth response, unknown status '%s' - sessionid: '%s', client: '%s' (%s) is unknown - %s: %s", msg.getStatus(), session.getSessionId(), msg.getClientName(), msg.getClientId()));					
			try {
				CommsStorage.updateMessageSession(db, session.getSessionId(), "closed");
			} catch (SQLException e) {
				throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
			}
			throw new WeaveException("Invalid client auth response");
		}

		Log.getInstance().info(String.format("Client auth response from client '%s' (%s) - %s: %s", msg.getClientName(), msg.getClientId(), msg.getStatus(), msg.getMessage()));

		if ( msg.getStatus().equalsIgnoreCase("okay") ) {
			
			if ( msg.getSyncKey() == null || msg.getSyncKey().length() == 0 ) {
				Log.getInstance().error(String.format("Invalid client auth response, synckey missing - sessionid: '%s', client: '%s' (%s) is unknown - %s: %s", msg.getStatus(), session.getSessionId(), msg.getClientName(), msg.getClientId()));					
				try {
					CommsStorage.updateMessageSession(db, session.getSessionId(), "closed");
				} catch (SQLException e) {
					throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
				}
				throw new WeaveException("Invalid client auth response");
			}
			
			//Success!			
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHSYNCKEY, msg.getSyncKey());
			comms.setProperty(KEY_CLIENT_CONFIG_AUTHBY, msg.getClientName());
						
		} else {
			//Do nothing
		}

		try {
			CommsStorage.updateMessageSession(db, session.getSessionId(), "closed");
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
		}
		
	}

	public void processClientAuthRequest(ClientAuthRequestMessage msg) throws WeaveException {
		Log.getInstance().debug("processClientAuthRequet()");
		
		MessageSession caSession = msg.getSession();
		
		Client otherClient = comms.getClient(caSession.getOtherClientId());

		if ( !msg.getClientId().equals(caSession.getOtherClientId()) ) {
			Log.getInstance().error(String.format("Invalid client auth request, client Id mismatch '%s' - sessionid: '%s', client: '%s' (%s)", msg.getClientId(), caSession.getSessionId(), msg.getClientName(), msg.getClientId()));
			try {
				CommsStorage.updateMessageSession(db, caSession.getSessionId(), "closed");
			} catch (SQLException e) {
				throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
			}
			throw new WeaveException("Invalid client auth response");
		}

		//Nothing to do if status no longer pending
		if ( !otherClient.getStatus().equals("pending") ) {
			Log.getInstance().warn(String.format("Client '%s' (%s) status '%s'. Nothing to do", otherClient.getClientName(), otherClient.getClientId(), otherClient.getStatus()));
			try {
				CommsStorage.updateMessageSession(db, caSession.getSessionId(), "closed");
			} catch (SQLException e) {
				throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
			}
			comms.updateMessage(msg.getMessageId(), true, false);
			return;
		}
		
		caSession.setState("responsepending");
		try {
			CommsStorage.updateMessageSession(db, caSession.getSessionId(), "closed");
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't update client auth session '%s'", msg.getMessageSessionId()));
		}
		comms.updateMessage(msg.getMessageId(), true, false);
						
		//FIXME Prompt user to authorise client
		
	}


	public void sendClientAuthResponse(String sessionId, boolean authorised, String authCode) throws WeaveException {
		Log.getInstance().debug("sendClientAuthResponse()");
		
		if ( wc.getStorageVersion() == StorageVersion.v5 ) {
			syncKey = ((WeaveClientV5Params)wc.getClientParams()).syncKey;
		} else {
			throw new WeaveException(String.format("Storage version %s not supported", WeaveClientFactory.storageVersionToString(wc.getStorageVersion())));
		}
		
		Message[] sessMsgs = null;
		try {
			sessMsgs = comms.getMessages(sessionId);
		} catch (NotFoundException e) {
			throw new WeaveException(String.format("Couldn't get messages for session '%s'", sessionId));
		}
		if ( sessMsgs.length != 1 ) {
			throw new WeaveException(String.format("Multiple messages in session '%s'. Only one message expected.", sessionId));
		}
		if ( !sessMsgs[0].getMessageType().equalsIgnoreCase("clientauth") ) {
			throw new WeaveException(String.format("Message '%s' is type '%s'. Client auth request message expected.", sessMsgs[0].getMessageId(), sessMsgs[0].getMessageType()));
		}
		
		ClientAuthRequestMessage caRequestMsg = new ClientAuthRequestMessage(sessMsgs[0]);
				
		if ( !caRequestMsg.getSession().getState().equalsIgnoreCase("responsepending") ) {
			throw new WeaveException(String.format("Invalid state for client auth message '%s'", sessionId));
		}
		
		boolean verified = false;
		
		if (authorised) {
			verified = verifyClientAuthRequestAuthCode(sessionId, authCode);
			if (!verified) {
				Log.getInstance().warn(String.format("Auth code verfication failed for client '%s' (%s)", caRequestMsg.getClientName(), caRequestMsg.getClientId()));
			}
		}

		ClientAuthResponseMessage caResponseMsg = new ClientAuthResponseMessage(comms.getNewMessage(caRequestMsg.getMessageSessionId()));

		//FIXME - Set in comms object?
		caResponseMsg.setSequence(2);
		
		caResponseMsg.setClientId(comms.getClientId());
		caResponseMsg.setClientName(comms.getClientName());
				
		if ( authorised && verified ) {
			caResponseMsg.setStatus("okay");
			caResponseMsg.setMessage("Client authentication request approved");
			caResponseMsg.setSyncKey(syncKey);
		} else {
			caResponseMsg.setStatus("fail");
			caResponseMsg.setMessage("Client authentication request declined");
		}
		
		comms.sendMessage(caResponseMsg);
	}
	
	
}
