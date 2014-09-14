package org.exfio.weave.ext.comm;


import java.lang.AssertionError;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import lombok.Getter;

import java.security.KeyPair;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.NotFoundException;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.ext.comm.Client;
import org.exfio.weave.ext.comm.Client.EphemeralKey;
import org.exfio.weave.ext.comm.Message;
import org.exfio.weave.ext.comm.CommsStorage;
import org.exfio.weave.ext.comm.Message.MessageSession;
import org.exfio.weave.ext.crypto.ECDH;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.Base64;

public class Comms {

	public static final String PROTO_VERSION = "1";
	
	public static final int CLIENT_EPHEMERAL_KEYS_NUM = 10;
		
	//Client config
	//public static final String KEY_CLIENT_CONFIG_CLIENTID       = "clientid";
	//public static final String KEY_CLIENT_CONFIG_NAME           = "name";
	//public static final String KEY_CLIENT_CONFIG_PUBLIC_KEY     = "publickey";
	//public static final String KEY_CLIENT_CONFIG_PRIVATE_KEY    = "privatekey";
	//public static final String KEY_CLIENT_CONFIG_AUTHLEVEL      = "authlevel";

	
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
			
			/*
			this.clientId   = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_CLIENTID);
			this.clientName = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_NAME);
			
			identityPrivateKey = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_PRIVATE_KEY);
			identityPublicKey  = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_PUBLIC_KEY);
			*/						
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

	public void initServer() throws WeaveException {

		commsApi.initServer(PROTO_VERSION);
		
		/*
		//Cleanup exfioclient collection
		List<Client> clients = new ArrayList<Client>(Arrays.asList(commsApi.getClients()));
		Iterator<Client> iter = clients.listIterator();
		while ( iter.hasNext() ) {
			Client client = iter.next();
			
			try {
				commsApi.deleteClient(client.getClientId());
			} catch (NotFoundException e) {
				Log.getInstance().warn(String.format("Couldn't delete client '%s' - not found", client.getClientId()));
			}
			
		}
		
		//Cleanup exfiomessage collection
		List<String> msgIds = new ArrayList<String>();
		try {
			msgIds = new ArrayList<String>(Arrays.asList(commsApi.getMessageIds(null)));
		} catch (NotFoundException e) {
			//No messages found
		}

		Iterator<String> iterMsg = msgIds.listIterator();
		while ( iterMsg.hasNext() ) {
			String msgId = iterMsg.next();
			
			try {
				commsApi.deleteMessage(msgId);
			} catch (NotFoundException e) {
				Log.getInstance().warn(String.format("Couldn't delete message '%s' - not found", msgId));
			}
			
		}		

		//TODO - cleanup other collections or WBOs?
		*/
	}
	
	public EphemeralKey getEphemeralKey(String cId, String keyId) throws WeaveException {
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
	
	public void initClient(String name, boolean isAuthorised, String database) throws WeaveException {
		Connection db = null;
		try {
			db = getDatabaseConnection(database);		
		} catch (SQLException e) {
			throw new WeaveException("Couldn't initialise database - " + e.getMessage());
		}
		initClient(name, isAuthorised, db);
	}
	
	public void initClient(String name, boolean isAuthorised, Connection db) throws WeaveException {
		Log.getInstance().debug("initClient()");
		
		this.db = db;
		
		//Initialise database
		try {
			CommsStorage.initDB(db, true);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't initialise database - " + e.getMessage());
		}
		
		//Generate client id
		clientId   = wc.generateWeaveID();
		clientName = name;
		
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

	public String getProperty(String key) throws WeaveException {
		try {
			return CommsStorage.getProperty(db, key);
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

	public void deleteProperty(String key) throws WeaveException {
		try {
			CommsStorage.deleteProperty(db, key);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Client getClient(String clientId) throws WeaveException {
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

	public void updateClient() throws WeaveException {
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
		
		ECDH ecdh = new ECDH();
		
		//Create additional keys if required
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
		
		//Get other clients
		Client[] clients = commsApi.getClients();
		
		for (int i = 0; i < clients.length; i++) {							
			
			if ( clients[i].getClientId().equals(clientId) ) {
				//Ignore our own client record
				Log.getInstance().debug(String.format("Client '%s' (%s) is self. Skipping...", clients[i].getClientName(), clients[i].getClientId()));
				continue;
			}
			try {
				if ( CommsStorage.getClient(db, clients[i].getClientId()) == null ) {
					CommsStorage.createClient(db, clients[i]);
				} else {
					CommsStorage.updateClient(db, clients[i]);
				}
			} catch (SQLException e) {
				throw new WeaveException(e);
			}
		}				
	}
	
	public Message[] getMessages() throws WeaveException, NotFoundException {
		try {
			return CommsStorage.getMessages(db);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Message[] getMessagesBySession(String sessionId) throws WeaveException, NotFoundException {
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
		
		String identityPublicKey  = Base64.encodeBase64String(identityKeyPair.getPublic().getEncoded());
		String ephemeralPublicKey = null;
		
		try {
			ephemeralPublicKey = CommsStorage.getEphemeralKey(db, clientId, session.getEphemeralKeyId()).getPublicKey();
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		
		Message msg = new Message.EncodedMessage();
		msg.setVersion(PROTO_VERSION);
		msg.setSourceClientId(clientId);
		msg.setSourceIdentityKey(identityPublicKey);
		msg.setSourceKeyId(session.getEphemeralKeyId());
		msg.setSourceKey(ephemeralPublicKey);
		msg.setDestinationClientId(session.getOtherClientId());
		msg.setDestinationKeyId(session.getOtherEphemeralKeyId());
		msg.setSession(session);
		
		//FIXME - set sequence
		
		return msg;
	}

	public void updateMessage(int messageId, boolean isRead, boolean isDeleted) throws WeaveException {
		try {
			CommsStorage.updateMessage(db, messageId, isRead, isDeleted);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public void deleteMessage(int messageId) throws WeaveException {
		try {
			CommsStorage.deleteMessage(db, messageId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public MessageSession getIncomingMessageSession(Connection db, Message msg) throws WeaveException {
		return 	getMessageSession(msg.getDestinationKeyId() +  msg.getSourceKeyId());
	}

	public MessageSession getMessageSession(String sessionId) throws WeaveException {
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
		return createIncomingMessageSession(msg.getDestinationKeyId(), msg.getSourceClientId(), msg.getSourceIdentityKey(), msg.getSourceKeyId(), msg.getSourceKey());
	}

	public MessageSession createIncomingMessageSession(String ephemeralKeyId, String otherClientId, String otherIdentityKey, String otherEphemeralKeyId, String otherEphemeralKey) throws WeaveException {
		return  createIncomingMessageSession(ephemeralKeyId, otherClientId, otherIdentityKey, otherEphemeralKeyId, otherEphemeralKey, "responsepending");
	}
	
	public MessageSession createIncomingMessageSession(String ephemeralKeyId, String otherClientId, String otherIdentityKey, String otherEphemeralKeyId, String otherEphemeralKey, String state) throws WeaveException {

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
		sess.setOtherClientId(otherClientId);
		sess.setOtherIdentityKey(otherIdentityKey);
		sess.setOtherEphemeralKeyId(otherEphemeralKeyId);
		sess.setOtherEphemeralKey(otherEphemeralKey);
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

	public MessageSession createOutgoingMessageSession(String otherClientId) throws WeaveException {
		return createOutgoingMessageSession(otherClientId, "requestpending");
	}
	
	public MessageSession createOutgoingMessageSession(String otherClientId, String state) throws WeaveException {

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
		}
        
		List<EphemeralKey> ekeys = otherClient.getEphemeralKeys();
		if ( ekeys.size() == 0 ) {
			throw new WeaveException(String.format("Can't create message session no published keys found for client '%s'", otherClientId));
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

	public void updateMessageSession(String sessionId, String state) throws WeaveException {
		updateMessageSession(sessionId, state, null, null);
	}

	public void updateMessageSession(String sessionId, String state, Long sequence, Long otherSequence) throws WeaveException {
		try {
			CommsStorage.updateMessageSession(db, sessionId, state, sequence, otherSequence);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't update message session '%s'", sessionId));
		}
	}

	public Double sendMessage(Message msg) throws WeaveException {
		int msgId;
		
		try {
			msgId = CommsStorage.createMessage(db, msg);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		
		Double modified = commsApi.putMessage(msg.getEncodedMessage());
		
		//FIXME - properly track sequence values
		
		//Set message isread to true and update session state
		try {
			CommsStorage.updateMessage(db, msgId, true, false);
			
			if ( msg.getSession().getState().equals("requestpending") ) {
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "requestsent", 1L, null);
			} else if ( msg.getSession().getState().equals("responsepending") ) {
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "responsesent", 1L, null);			
			} else {
				Log.getInstance().warn(String.format("Unrecognised state '%s' for message session '%s'", msg.getSession().getState(), msg.getMessageSessionId()));
				CommsStorage.updateMessageSession(db, msg.getMessageSessionId(), "messagesent", null, null);	
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
			session.getEphemeralKeyId().equals(msg.getSession().getEphemeralKeyId())
			&&
			session.getOtherClientId().equals(msg.getSession().getOtherClientId())
			&&
			session.getOtherEphemeralKeyId().equals(msg.getSession().getOtherEphemeralKeyId())
		) {
			valid = true;
		}
		
		return valid;
	}
	
	public void checkMessages() throws WeaveException {
		
		//First update client records
		updateOtherClients();
		
		//FIXME - store timestamp of last time messages were checked
		
		//Get message ids
		String[] msgIds = null;
		
		try {
			msgIds = commsApi.getMessageIds(null);
		} catch (NotFoundException e) {
			throw new WeaveException("Couldn't check messages - " + e.getMessage());
		}
				
		for (int i = 0; i < msgIds.length; i++) {
			//Get corresponding Ephemeral Key
			EphemeralKey ekey = null;
			try {
				ekey = CommsStorage.getEphemeralKey(db, clientId, msgIds[i]);
			} catch (SQLException e) {				
				Log.getInstance().error(String.format("Couldn't get ephemeral key for keyid '%s' - %s", msgIds[i], e.getMessage()));
				continue;
			}
			
			if ( ekey != null ) {
				
				//Save message to local storage
				try {
					Message msg = commsApi.getMessage(msgIds[i]);

					if ( msg.getSequence() == 1 ) {

						Client otherClient = CommsStorage.getClient(db, msg.getSourceClientId());
						if ( otherClient == null ) {
							Log.getInstance().error(String.format("Couldn't load client '%s'", msg.getSourceClientId()));
							continue;
						}
						
						//FIXME - should initial message include client identity key?
						String sourceIdentityKey = msg.getSourceIdentityKey();
						if ( sourceIdentityKey == null ) {
							sourceIdentityKey = otherClient.getPublicKey();
						}
						
						try {
							createIncomingMessageSession(msg.getDestinationKeyId(), msg.getSourceClientId(), sourceIdentityKey, msg.getSourceKeyId(), msg.getSourceKey());
						} catch (WeaveException e) {
							Log.getInstance().error(String.format("Couldn't create message session for message '%s' - %s", msgIds[i], e.getMessage()));
							continue;
						}
					}
					
					//Add session to message
					MessageSession session = getIncomingMessageSession(db, msg); 
					if ( !validateMessageSession(msg, session) ) {
						Log.getInstance().error(String.format("Message session invalid for message '%s'", msgIds[i]));
						continue;
					}
					msg.setSession(session);
					
					CommsStorage.createMessage(db, msg);
					
				} catch (NotFoundException e) {
					Log.getInstance().warn(String.format("Error processsing message '%s' - Message not found", msgIds[i]));
					continue;
				} catch (SQLException e) {
					Log.getInstance().error(String.format("Error processing message '%s' - Couldn't save message to local storage - %s", msgIds[i], e.getMessage()));
					continue;					
				} catch (WeaveException e) {
					Log.getInstance().warn(String.format("Error processing message '%s' - %s", msgIds[i], e.getMessage()));
					continue;					
				}

				//delete message from server
				try {
					commsApi.deleteMessage(msgIds[i]);
				} catch (NotFoundException e) {
					Log.getInstance().warn(String.format("Couldn't delete message '%s' - Message not found", msgIds[i]));
					continue;
				} catch (WeaveException e) {
					Log.getInstance().warn(String.format("Couldn't delete message '%s' - %s", msgIds[i], e.getMessage()));
					continue;					
				}
			}
		}
		
		updateClient();
	}
	
}
