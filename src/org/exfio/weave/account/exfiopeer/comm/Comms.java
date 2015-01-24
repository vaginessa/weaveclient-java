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
package org.exfio.weave.account.exfiopeer.comm;

import java.lang.AssertionError;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import lombok.Getter;

import java.security.KeyPair;
import java.security.SecureRandom;

import org.exfio.weave.WeaveException;
import org.exfio.weave.account.exfiopeer.comm.Client;
import org.exfio.weave.account.exfiopeer.comm.CommsStorage;
import org.exfio.weave.account.exfiopeer.comm.Message;
import org.exfio.weave.account.exfiopeer.comm.StorageNotFoundException;
import org.exfio.weave.account.exfiopeer.comm.Client.EphemeralKey;
import org.exfio.weave.account.exfiopeer.comm.Message.MessageSession;
import org.exfio.weave.account.exfiopeer.crypto.ECDH;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.storage.WeaveBasicObject;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;

public class Comms {

	public static final String PROTO_VERSION = "1";
	
	public static final int CLIENT_EPHEMERAL_KEYS_NUM = 10;
	
	public static final String KEY_META_PATH         = "meta/exfio";
	public static final String KEY_META_COLLECTION   = "meta";
	public static final String KEY_META_ID           = "exfio";

	//Client config
	public static final String KEY_CLIENT_CONFIG_LASTMESSAGEPOLL = "lastcheckedmessages";

	
	private WeaveClient wc;
	private Connection db;
	private CommsApiV1 commsApi;
	
	@Getter private Client clientSelf;
	@Getter private String clientId;
	@Getter private String clientName;
	@Getter private KeyPair identityKeyPair;
	@Getter private String identityPublicKey;
	@Getter private String identityPrivateKey;
	
	
	public Comms(WeaveClient wc) {
		this.wc       = wc;
		this.db       = null;
		this.commsApi = new CommsApiV1(wc);
	}
	
	public Comms(WeaveClient wc, String database) {	
		try {
			Connection db = getDatabaseConnection(database);
			init(wc, db);
		} catch (SQLException e) {
			throw new AssertionError(e.getMessage());		
		}
	}

	public Comms(WeaveClient wc, Connection db) {
		init(wc, db);
	}

	private void init(WeaveClient wc, Connection db) {
		this.wc       = wc;
		this.db       = db;
		this.commsApi = new CommsApiV1(wc);

		//load client info from local storage
		try {
			clientSelf = CommsStorage.getClientSelf(db);
			clientId   = clientSelf.getClientId();
			clientName = clientSelf.getClientName();
			identityPrivateKey = clientSelf.getPrivateKey();
			identityPublicKey  = clientSelf.getPublicKey();

		} catch (StorageNotFoundException e) {
			throw new AssertionError("Couldn't load client config from local storage - " + e.getMessage());
		} catch (SQLException e) {
			throw new AssertionError("Couldn't load client config from local storage - " + e.getMessage());
		}
		
		try {
			ECDH ecdh = new ECDH();
			this.identityKeyPair = ecdh.extractECDHKeyPair(identityPrivateKey, identityPublicKey);
		} catch (WeaveException e) {
			throw new AssertionError("Couldn't extract ECDH keys - " + e.getMessage());
		}
	}

	public boolean isInitialised() throws WeaveException {
		return commsApi.isInitialised();
	}
	
	public void initServer() throws WeaveException {
		commsApi.initServer(PROTO_VERSION);
	}
	
	public EphemeralKey getEphemeralKey(String cId, String keyId) throws StorageNotFoundException, WeaveException {
		try {
			return CommsStorage.getEphemeralKey(db, cId, keyId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't get ephemeral key '%s' - %s", keyId, e.getMessage()));
		}
	}
	
	public Connection getDB() {
		return db;
	}
	
	public static Connection getDatabaseConnection(String database) throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + database);
	}

	/**
	 * generateAccountGuid
	 * 
	 * @param baseUrl
	 * @param username
	 * @return String
	 * @throws WeaveException
	 * 
	 * Build unique account guid that is also valid filename
	 * 
	 */
	public static String generateAccountGuid(String baseUrl, String username) throws WeaveException {

		String baseHost = null;
		try {
			URI baseURL = new URI(baseUrl);
			baseHost = baseURL.getHost();
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		
		//Random url safe string
        SecureRandom rnd = new SecureRandom();
        byte[] rndBin  = rnd.generateSeed(9);
        String rndText = Base64.encodeToString(rndBin, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);

		return String.format("%s-%s-%s", username, baseHost, rndText);
	}
	
	public void initClient(String name, boolean isAuthorised, String database) throws WeaveException {
		initClient(name, isAuthorised, database, wc.generateWeaveID());
	}
	
	public void initClient(String name, boolean isAuthorised, String database, String clientId) throws WeaveException {
		Connection db = null;
		try {
			db = getDatabaseConnection(database);		
		} catch (SQLException e) {
			throw new WeaveException("Couldn't initialise database - " + e.getMessage());
		}
		initClient(name, isAuthorised, db, clientId);
	}
	
	public void initClient(String name, boolean isAuthorised, Connection db) throws WeaveException {
		initClient(name, isAuthorised, db, wc.generateWeaveID());
	}
	
	public void initClient(String name, boolean isAuthorised, Connection db, String clientId) throws WeaveException {
		Log.getInstance().debug("initClient()");
		
		this.clientId   = clientId;
		this.clientName = name;		
		this.db         = db;
		
		//Initialise database
		try {
			CommsStorage.initDB(db, true);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't initialise database - " + e.getMessage());
		}
		
		//Generate ECDH keypair
		ECDH ecdh = new ECDH();
		identityKeyPair = ecdh.generateECDHKeyPair();
		
		identityPublicKey  = Base64.encodeBase64String(identityKeyPair.getPublic().getEncoded());
        identityPrivateKey = Base64.encodeBase64String(identityKeyPair.getPrivate().getEncoded());

        //Create client record for self
        clientSelf = new Client();
        clientSelf.setClientId(clientId);
        clientSelf.setSelf(true);
        clientSelf.setClientName(clientName);
		clientSelf.setPublicKey(identityPublicKey);
		clientSelf.setPrivateKey(identityPrivateKey);
        clientSelf.setStatus(isAuthorised ? "authorised" : "pending");
        clientSelf.setAuthLevel("all");
		clientSelf.setVersion(PROTO_VERSION);
		try {
			CommsStorage.createClient(db, clientSelf);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't create client record - " + e.getMessage());
		}
		
		updateClient();
	}

	public String getProperty(String key, String defaultValue) throws WeaveException {
		try {
			return CommsStorage.getProperty(db, key, defaultValue);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public String getProperty(String key) throws StorageNotFoundException, WeaveException {
		try {
			return CommsStorage.getProperty(db, key);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public boolean hasProperty(String key) throws WeaveException {
		try {
			return CommsStorage.hasProperty(db, key);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public void setProperty(String key, String value) throws WeaveException {
		try {
			CommsStorage.setProperty(db, key, value);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public void deleteProperty(String key) throws StorageNotFoundException, WeaveException {
		try {
			CommsStorage.deleteProperty(db, key);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Client getClient(String clientId) throws StorageNotFoundException, WeaveException {
		try {
			return CommsStorage.getClient(db, clientId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Client[] getClients() throws WeaveException {
		try {
			return CommsStorage.getClients(db);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public void updateClient() throws StorageNotFoundException, WeaveException {
		Log.getInstance().debug("updateClient()");
		
		
		//List<EphemeralKey> ephemeralKeys = clientSelf.getEphemeralKeys();
		List<EphemeralKey> ephemeralKeys = null;
		try {
			ephemeralKeys = new ArrayList<EphemeralKey>(Arrays.asList(CommsStorage.getClientEphemeralKeys(db, clientId)));
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't load ephemeral keys for client '%s'", clientId));
		}
		
		//Only published keys are synced with server
		List<EphemeralKey> publishedKeys = new ArrayList<EphemeralKey>();
		Iterator<EphemeralKey> iter = ephemeralKeys.listIterator();
		while ( iter.hasNext() ) {
			EphemeralKey eKey = iter.next();
			if ( eKey.getStatus().equals("published") ) {
				publishedKeys.add(eKey);
			}
		}
		
		if ( wc.isAuthorised() ) {
			//If client is authorised ensure CLIENT_EPHEMERAL_KEYS_NUM ephemeral keys are published			
			
			ECDH ecdh = new ECDH();
			
			while ( publishedKeys.size() < CLIENT_EPHEMERAL_KEYS_NUM ) {
				
				//Generate new ephemeral key
				String ephemeralKeyId = wc.generateWeaveID();
				KeyPair ephemeralKeyPair = ecdh.generateECDHKeyPair();
				String ephemeralPublicKey  = Base64.encodeBase64String(ephemeralKeyPair.getPublic().getEncoded());
		        String ephemeralPrivateKey = Base64.encodeBase64String(ephemeralKeyPair.getPrivate().getEncoded());
		        
		        //Save ephemeral key to database
		        Client.EphemeralKey eKey = new Client.EphemeralKey();
		        eKey.setKeyId(ephemeralKeyId);
		        eKey.setPublicKey(ephemeralPublicKey);
		        eKey.setPrivateKey(ephemeralPrivateKey);
		        eKey.setStatus("published");
	
		        ephemeralKeys.add(eKey);
		        publishedKeys.add(eKey);
			}
		}
		
		//Save client and updated list of ephemeral keys
		clientSelf.setEphemeralKeys(ephemeralKeys);
		try {
			CommsStorage.updateClient(db, clientSelf);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't save client '%s' to local storage - %s", clientId, e.getMessage()));
		}
		
		//Re-load client from local storage and add only published keys
		Client client = null;
		try {
			client = CommsStorage.getClient(db, clientId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't load client '%s' from local storage - %s", clientId, e.getMessage()));
		}

		//Sync client and published ephemeral keys with server
		client.setEphemeralKeys(publishedKeys);
		commsApi.putClient(client);
		
		updateOtherClients();
	}
	
	public void updateOtherClients() throws WeaveException {
		Log.getInstance().debug("updateOtherClients()");
		
		//Get other clients
		Client[] clients = commsApi.getClients();
		
		for (Client client: clients) {							
			
			if ( client.getClientId().equals(clientId) ) {
				//Ignore our own client record
				Log.getInstance().debug(String.format("Client '%s' (%s) is self. Skipping...", client.getClientName(), client.getClientId()));
				continue;
			}

			//TODO - make checking for existing client cleaner
			
			//Check if client already exists in storage
			Client tmpClient = null;
			try {
				tmpClient = CommsStorage.getClient(db, client.getClientId());
			} catch (StorageNotFoundException e) {
				tmpClient = null;
			} catch (SQLException e) {
				throw new WeaveException(e);
			}
			
			try {
				if ( tmpClient == null ) {
					CommsStorage.createClient(db, client);
				} else {
					CommsStorage.updateClient(db, client);
				}
			} catch (SQLException e) {
				throw new WeaveException(e);
			}

		}				
	}

	public Message[] getMessages() throws WeaveException {
		try {
			return CommsStorage.getMessages(db);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Message[] getMessagesBySession(String sessionId) throws WeaveException, StorageNotFoundException {
		try {
			return CommsStorage.getMessages(db, sessionId, null, null, true, false);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Message[] getUnreadMessages() throws WeaveException {
		return getUnreadMessages(null);
	}
	
	public Message[] getUnreadMessages(String sessionId) throws WeaveException {
		try {
			return CommsStorage.getMessages(db, sessionId, null, null, false, false);
		} catch (SQLException e) {
			throw new WeaveException(e);
		//} catch (NotFoundException e) {
		//	//Return empty array
		//	return new Message[0];
		}
	}

	public Message[] getPendingMessages() throws WeaveException {
		return getPendingMessages(null);
	}
	
	public Message[] getPendingMessages(String messageType) throws WeaveException {
		try {
			return CommsStorage.getMessages(db, null, messageType, "responsepending", true, false);
		} catch (SQLException e) {
			throw new WeaveException(e);
		//} catch (NotFoundException e) {
		//	//Return empty array
		//	return new Message[0];
		}
	}

	public Message getNewMessage(String sessionId) throws WeaveException {
		return getNewMessage(getMessageSession(sessionId));	
	}
	
	public Message getNewMessage(MessageSession session) throws WeaveException {
		
		String ephemeralPublicKey = null;		
		try {
			ephemeralPublicKey = CommsStorage.getEphemeralKey(db, clientId, session.getEphemeralKeyId()).getPublicKey();
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		
		Message msg = new Message.EncodedMessage();
		msg.setVersion(PROTO_VERSION);
		msg.setSourceClientId(clientId);
		msg.setSourceKeyId(session.getEphemeralKeyId());
		msg.setSourceKey(ephemeralPublicKey);
		msg.setDestinationClientId(session.getOtherClientId());
		msg.setDestinationKeyId(session.getOtherEphemeralKeyId());
		msg.setSequence(session.getSequence() + 1);
		msg.setSession(session);
				
		return msg;
	}

	public void updateMessage(int messageId, boolean isRead, boolean isDeleted) throws WeaveException {
		try {
			CommsStorage.updateMessage(db, messageId, isRead, isDeleted);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public void deleteMessage(int messageId) throws StorageNotFoundException, WeaveException {
		try {
			CommsStorage.deleteMessage(db, messageId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public MessageSession getMessageSession(Message msg) throws StorageNotFoundException, WeaveException {
		String sessionId = null;
		
		if ( msg.getDestinationClientId().equals(clientId) ) {
			//incoming message
			sessionId = msg.getDestinationKeyId() +  msg.getSourceKeyId();
		} else {
			//outgoing message
			sessionId = msg.getSourceKeyId() + msg.getDestinationKeyId();
		}
		
		return 	getMessageSession(sessionId);
	}

	public MessageSession getMessageSession(String sessionId) throws StorageNotFoundException, WeaveException {
		try {
			return CommsStorage.getMessageSession(db, sessionId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public MessageSession[] getMessageSessions(String clientId) throws WeaveException {
		try {
			return CommsStorage.getMessageSessions(db, clientId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public MessageSession createIncomingMessageSession(Message msg) throws WeaveException {

		Client otherClient = null;
		try {
			otherClient = CommsStorage.getClient(db, msg.getSourceClientId());
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't load client '%s'", msg.getSourceClientId()), e);
		} finally {
			if ( otherClient == null ) {
				throw new WeaveException(String.format("Client '%s' not found", msg.getSourceClientId()));
			}
		}

		return createIncomingMessageSession(msg.getDestinationKeyId(), msg.getSourceClientId(), otherClient.getPublicKey(), msg.getSourceKeyId(), msg.getSourceKey());
	}

	public MessageSession createIncomingMessageSession(String ephemeralKeyId, String otherClientId, String otherIdentityKey, String otherEphemeralKeyId, String otherEphemeralKey) throws WeaveException {
		return  createIncomingMessageSession(ephemeralKeyId, otherClientId, otherIdentityKey, otherEphemeralKeyId, otherEphemeralKey, "responsepending", 1L);
	}
	
	public MessageSession createIncomingMessageSession(String ephemeralKeyId, String otherClientId, String otherIdentityKey, String otherEphemeralKeyId, String otherEphemeralKey, String state, Long sequence) throws WeaveException {

		EphemeralKey ekey = null;
		try {
			ekey = CommsStorage.getEphemeralKey(db, clientId, ephemeralKeyId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't get ephemeral key '%s' - %s", ephemeralKeyId, e.getMessage()));
		}
		
		if ( ekey == null) {
			throw new WeaveException(String.format("Ephemeral key '%s' not found", ephemeralKeyId));
		}

		if ( !ekey.getStatus().equals("published") ) {
			throw new WeaveException(String.format("Ephemeral key '%s' already provisioned", ephemeralKeyId));
		}
		
		MessageSession sess = new MessageSession();
		sess.setSessionId(ephemeralKeyId + otherEphemeralKeyId);
		sess.setEphemeralKeyId(ephemeralKeyId);
		sess.setSequence(0);
		sess.setOtherClientId(otherClientId);
		sess.setOtherIdentityKey(otherIdentityKey);
		sess.setOtherEphemeralKeyId(otherEphemeralKeyId);
		sess.setOtherEphemeralKey(otherEphemeralKey);
		sess.setOtherSequence(sequence);
		sess.setState(state);
		try {
			CommsStorage.createMessageSession(db, sess);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't create message session '%s' - %s", sess.getSessionId(), e.getMessage()));
		}

		ekey.setStatus("provisioned");
		try {
			CommsStorage.updateEphemeralKey(db, ekey);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't update ephemeral key '%s' - %s", ephemeralKeyId, e.getMessage()));
		}
        
		return sess;
	}

	public MessageSession createOutgoingMessageSession(String otherClientId) throws WeaveException, NoPublishedKeysException {
		return createOutgoingMessageSession(otherClientId, "requestpending");
	}
	
	public MessageSession createOutgoingMessageSession(String otherClientId, String state) throws WeaveException, NoPublishedKeysException {

		//Generate and store ephemeral ECDH keypair
		ECDH ecdh = new ECDH();
		KeyPair ephemeralKeyPair   = ecdh.generateECDHKeyPair();
		String ephemeralKeyId      = wc.generateWeaveID();
		String ephemeralPublicKey  = Base64.encodeBase64String(ephemeralKeyPair.getPublic().getEncoded());
        String ephemeralPrivateKey = Base64.encodeBase64String(ephemeralKeyPair.getPrivate().getEncoded());
        
        Client.EphemeralKey ekey = new Client.EphemeralKey();
        ekey.setKeyId(ephemeralKeyId);
        ekey.setPublicKey(ephemeralPublicKey);;
        ekey.setPrivateKey(ephemeralPrivateKey);
        ekey.setStatus("provisioned");
		try {
	        CommsStorage.createEphemeralKey(db, clientId, ekey);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't create ephemeral key '%s'", ephemeralKeyId));
		}

		//Randomly select an ephemeral key from other client
        Client otherClient = null;
		try {
	        otherClient = CommsStorage.getClient(db, otherClientId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt get client '%s'", otherClientId));
		} finally {
			if ( otherClient == null ) {
				throw new WeaveException(String.format("Client '%s' not found", otherClientId));
			}
		}
        
		List<EphemeralKey> ekeys = otherClient.getEphemeralKeys();
		if ( ekeys.size() == 0 ) {
			throw new NoPublishedKeysException(String.format("Can't create message session no published keys found for client '%s'", otherClientId));
		}
		
		int keyIndex = (int)(Math.random() * ekeys.size());
		EphemeralKey otherEphemeralKey = ekeys.get(keyIndex);

		MessageSession sess = new MessageSession();
		sess.setSessionId(ephemeralKeyId + otherEphemeralKey.getKeyId());
		sess.setEphemeralKeyId(ephemeralKeyId);
		sess.setSequence(0);
		sess.setOtherClientId(otherClient.getClientId());
		sess.setOtherIdentityKey(otherClient.getPublicKey());
		sess.setOtherEphemeralKeyId(otherEphemeralKey.getKeyId());
		sess.setOtherEphemeralKey(otherEphemeralKey.getPublicKey());
		sess.setOtherSequence(0);
		sess.setState(state);
		try {
			CommsStorage.createMessageSession(db, sess);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt create message session '%s' - %s", sess.getSessionId(), e.getMessage()));
		}
				
		return sess;
	}

	public void updateMessageSession(String sessionId, String state) throws StorageNotFoundException, WeaveException {
		updateMessageSession(sessionId, state, null, null);
	}

	public void updateMessageSession(String sessionId, String state, Long sequence, Long otherSequence) throws StorageNotFoundException, WeaveException {
		try {
			CommsStorage.updateMessageSession(db, sessionId, state, sequence, otherSequence);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't update message session '%s'", sessionId));
		}
	}

	public Double sendMessage(Message msg) throws WeaveException {
		int msgId;

		//TODO - make atomic

		try {
			//Set message sequence
			MessageSession session = CommsStorage.getMessageSession(db, msg.getMessageSessionId());
			msg.setSequence(session.getSequence() + 1);
			
			msgId = CommsStorage.createMessage(db, msg);
			
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		
		Double modified = commsApi.putMessage(msg.getEncodedMessage());
				
		//Set message isread to true and update session state and sequence
		try {
			CommsStorage.updateMessage(db, msgId, true, false);
			
			if ( msg.getSession().getState().equals("requestpending") ) {
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "requestsent", msg.getSequence(), null);
			} else if ( msg.getSession().getState().equals("responsepending") ) {
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "responsesent", msg.getSequence(), null);			
			} else {
				Log.getInstance().warn(String.format("Unrecognised state '%s' for message session '%s'", msg.getSession().getState(), msg.getMessageSessionId()));
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "messagesent", msg.getSequence(), null);
			}
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		
		return modified;
	}

	@SuppressWarnings("unused")
	private boolean validateMessageSession(Message msg) throws WeaveException {	
		return validateMessageSession(msg, getMessageSession(msg.getMessageSessionId()));
	}
	
	private boolean validateMessageSession(Message msg, MessageSession session) throws WeaveException {
			
		boolean valid = false;

		if (
			msg != null
			&&
			session != null
			&&
			session.getEphemeralKeyId().equals(msg.getDestinationClientId().equals(clientId) ? msg.getDestinationKeyId() : msg.getSourceKeyId())
			&&
			session.getOtherClientId().equals(msg.getDestinationClientId().equals(clientId) ? msg.getSourceClientId() : msg.getDestinationClientId())
			&&
			session.getOtherEphemeralKeyId().equals(msg.getDestinationClientId().equals(clientId) ? msg.getSourceKeyId() : msg.getDestinationKeyId())
		) {
			valid = true;
		}
		
		return valid;
	}
	
	public void checkMessages() throws WeaveException {
		Log.getInstance().debug("checkMessages()");
		
		boolean syncError = false;
		
		//First update client records
		updateOtherClients();
		
		//Get last poll value
		Double lastPoll = null;
		Double currPoll = ((double)System.currentTimeMillis())/1000;
		
		//FIXME - Ensure modified time calculation and representation is correct
		//try {
		//	if ( CommsStorage.hasProperty(db, KEY_CLIENT_CONFIG_LASTMESSAGEPOLL) ) {
		//		lastPoll = Double.parseDouble(CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_LASTMESSAGEPOLL));
		//	}
		//} catch (SQLException e) {
		//	throw new WeaveException(String.format("Error reading properties - %s", e.getMessage()));
		//}
		
		//Get message ids
		String[] msgIds = null;

		Log.getInstance().debug(String.format("Polling server for new messages since '%.2f'", (lastPoll == null ? 0 : lastPoll)));

		try {
			msgIds = commsApi.getMessageIds(lastPoll);
		} catch (NotFoundException e) {
			throw new WeaveException("Couldn't check messages - " + e.getMessage());
		}
			
		Log.getInstance().debug(String.format("Processing %d messages", msgIds.length));
		
		for (String msgId: msgIds) {
			
			Message msg = null;
			try {
				msg = commsApi.getMessage(msgId);
			} catch (NotFoundException e) {
				Log.getInstance().warn(String.format("Error processsing message '%s' - Message not found", msgId));
				syncError = true;
				continue;
			}
			
			//Check message is ours
			if ( !msg.getDestinationClientId().equals(clientId) ) {
				Log.getInstance().info(String.format("Message '%s' for other client '%s'. Skipping...", msgId, msg.getDestinationClientId()));
				continue;
			}
			
			//Get corresponding Ephemeral Key
			EphemeralKey ekey = null;
			try {
				ekey = CommsStorage.getEphemeralKey(db, clientId, msg.getDestinationKeyId());
			} catch (SQLException e) {
				Log.getInstance().error(String.format("Couldn't get ephemeral key for keyid '%s' - %s", msg.getDestinationKeyId(), e.getMessage()));
				syncError = true;
				continue;
			}
			
			if ( ekey == null ) {
				Log.getInstance().error(String.format("Couldn't get ephemeral key for keyid '%s' - not found", msg.getDestinationKeyId()));
				syncError = true;
				continue;
			}

			//Save message to local storage
			try {

				//Get message session
				MessageSession session = getMessageSession(msg);
				
				if ( session == null && msg.getSequence() == 1 ) {
					//This is a new session
					
					Client otherClient = CommsStorage.getClient(db, msg.getSourceClientId());
					if ( otherClient == null ) {
						Log.getInstance().error(String.format("Couldn't load client '%s'", msg.getSourceClientId()));
						syncError = true;
						continue;
					}
										
					try {
						createIncomingMessageSession(msg.getDestinationKeyId(), msg.getSourceClientId(), otherClient.getPublicKey(), msg.getSourceKeyId(), msg.getSourceKey());
					} catch (WeaveException e) {
						Log.getInstance().error(String.format("Couldn't create message session for message '%s' - %s", msgId, e.getMessage()));
						syncError = true;
						continue;
					}
					
					session = getMessageSession(msg);
				}
				
				//Check session validity
				if ( !validateMessageSession(msg, session) ) {
					Log.getInstance().error(String.format("Message session invalid for message '%s'", msgId));
					syncError = true;
					continue;
				}
				
				//Finally add session to message and save
				msg.setSession(session);
				CommsStorage.createMessage(db, msg);
				
			} catch (SQLException e) {
				Log.getInstance().error(String.format("Error processing message '%s' - Couldn't save message to local storage - %s", msgId, e.getMessage()));
				syncError = true;
				continue;
			} catch (WeaveException e) {
				Log.getInstance().warn(String.format("Error processing message '%s' - %s", msgId, e.getMessage()));
				syncError = true;
				continue;			
			}

			//delete message from server
			try {
				commsApi.deleteMessage(msgId);
			} catch (NotFoundException e) {
				Log.getInstance().warn(String.format("Couldn't delete message '%s' - Message not found", msgId));
				syncError = true;
				continue;
			} catch (WeaveException e) {
				Log.getInstance().warn(String.format("Couldn't delete message '%s' - %s", msgId, e.getMessage()));
				syncError = true;
				continue;					
			}

		}
		
		//Update last poll value
		if ( syncError ) {
			Log.getInstance().warn("Errors occurred while syncing messages, last poll timestamp not updated");
		} else {
			try {
				CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_LASTMESSAGEPOLL, String.format("%.2f", currPoll.doubleValue()));
			} catch (SQLException e) {
				throw new WeaveException(String.format("Error writing properties - %s", e.getMessage()));
			}
		}
				
		updateClient();
	}
	
}
