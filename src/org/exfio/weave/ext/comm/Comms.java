package org.exfio.weave.ext.comm;


import java.lang.AssertionError;
import java.util.List;
import java.util.ListIterator;
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
	public static final String KEY_CLIENT_CONFIG_CLIENTID       = "clientid";
	public static final String KEY_CLIENT_CONFIG_NAME           = "name";
	public static final String KEY_CLIENT_CONFIG_PUBLIC_KEY     = "publickey";
	public static final String KEY_CLIENT_CONFIG_PRIVATE_KEY    = "privatekey";
	public static final String KEY_CLIENT_CONFIG_AUTHLEVEL      = "authlevel";

	
	private WeaveClient wc;
	private Connection db;
	private CommsApiV1 commsApi;
	
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
			this.clientId   = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_CLIENTID);
			this.clientName = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_NAME);
			
			identityPrivateKey = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_PRIVATE_KEY);
			identityPublicKey  = CommsStorage.getProperty(db, KEY_CLIENT_CONFIG_PUBLIC_KEY);						
		} catch (SQLException e) {
			new AssertionError("Couldn't load client config from local storage - " + e.getMessage());			
		}
		
		try {
			ECDH ecdh = new ECDH();
			this.identityKeyPair = ecdh.extractECDHKeyPair(identityPrivateKey, identityPublicKey);
		} catch (WeaveException e) {
			new AssertionError("Couldn't extract ECDH keys - " + e.getMessage());
		}
	}

	public EphemeralKey getEphemeralKey(String keyId) throws WeaveException {
		try {
			return CommsStorage.getEphemeralKey(db, keyId);
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
		try {
			CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_CLIENTID, clientId);
			CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_NAME, clientName);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't save client config - " + e.getMessage());
		}
		
		//Generate ECDH keypair
		ECDH ecdh = new ECDH();
		identityKeyPair = ecdh.generateECDHKeyPair();
		
		identityPublicKey  = Base64.encodeBase64String(identityKeyPair.getPublic().getEncoded());
        identityPrivateKey = Base64.encodeBase64String(identityKeyPair.getPrivate().getEncoded());
		try {
			CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_PUBLIC_KEY, identityPublicKey);
			CommsStorage.setProperty(db, KEY_CLIENT_CONFIG_PRIVATE_KEY, identityPrivateKey);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't save client config - " + e.getMessage());
		}


        //Save client record for self
        Client cl = new Client();
        cl.setClientId(clientId);
        cl.setSelf(true);
        cl.setClientName(clientName);
		cl.setPublicKey(identityPublicKey);
		cl.setPrivateKey(identityPrivateKey);
        cl.setStatus(isAuthorised ? "authorised" : "pending");
        cl.setAuthLevel("all");
		cl.setVersion(PROTO_VERSION);
		try {
			CommsStorage.createClient(db, cl);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't create client record - " + e.getMessage());
		}

		int eKeyCount = 0;
		while ( eKeyCount < CLIENT_EPHEMERAL_KEYS_NUM ) {
			
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
			try {
		        CommsStorage.createEphemeralKey(db, clientId, eKey);
			} catch (SQLException e) {
				throw new WeaveException("Couldn't create ephemeral key record - " + e.getMessage());
			}
	        
	        eKeyCount++;
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

		Client client = null;
		try {
			client = CommsStorage.getClient(db, clientId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldn't load client '%s' from local storage - %s", clientId, e.getMessage()));
		}
		
		List<EphemeralKey> eKeys = client.getEphemeralKeys();
				
		//First remove used keys
		ListIterator<EphemeralKey> iter = eKeys.listIterator();
		while ( iter.hasNext() ) {
			EphemeralKey eKey = iter.next();
			if ( eKey.getStatus().equals("published") ) {
				iter.remove();
			}
		}
		
		ECDH ecdh = new ECDH();
		
		//Create additional keys if required
		iter = eKeys.listIterator();
		while ( eKeys.size() < CLIENT_EPHEMERAL_KEYS_NUM ) {
			
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
	        try {
	        	CommsStorage.createEphemeralKey(db, clientId, eKey);
	        } catch (SQLException e) {
	        	throw new WeaveException("Couldn't save ephemeral key - " + e.getMessage());
	        }

	        iter.add(eKey);
		}
		
		commsApi.putClient(client);
		
		//Get other clients
		Client[] clients = commsApi.getClients();
		
		for (int i = 0; i < clients.length; i++) {							
			
			if ( clients[i].isSelf() ) {
				//Ignore our own client record
				Log.getInstance().info(String.format("Client '%s' (%s) is self. Skipping...", clients[i].getClientName(), clients[i].getClientId()));
				continue;
			}
			try {
				if ( CommsStorage.getClient(db, client.getClientId()) == null ) {
					CommsStorage.createClient(db, client);
				} else {
					CommsStorage.updateClient(db, client);
				}
			} catch (SQLException e) {
				throw new WeaveException(e);
			}
		}		
	}
	
	public Message[] getMessages() throws WeaveException, NotFoundException {
		return getMessages(null);
	}

	public Message[] getMessages(String sessionId) throws WeaveException, NotFoundException {
		try {
			return CommsStorage.getMessages(db, sessionId);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}

	public Message[] getUnreadMessages() throws WeaveException {
		try {
			return getUnreadMessages(null);
		} catch (NotFoundException e) {
			//Return empty array
			return new Message[0];
		}
	}
	
	public Message[] getUnreadMessages(String sessionId) throws WeaveException, NotFoundException {
		try {
			return CommsStorage.getMessages(db, sessionId, false, false);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
	}
	

	public Message getNewMessage(String sessionId) throws WeaveException {
		return getNewMessage(getMessageSession(sessionId));	
	}
	
	public Message getNewMessage(MessageSession session) throws WeaveException {
		
		String identityPublicKey  = Base64.encodeBase64String(identityKeyPair.getPublic().getEncoded());
		String ephemeralPublicKey = null;
		
		try {
			ephemeralPublicKey = CommsStorage.getEphemeralKey(db, session.getEphemeralKeyId()).getPublicKey();
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

		EphemeralKey ekey = null;
		try {
			ekey = CommsStorage.getEphemeralKey(db, ephemeralKeyId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt get ephemeral key '%s'", ephemeralKeyId));
		}
		
		if ( ekey == null) {
			throw new WeaveException(String.format("Ephemeral key '%s' not found", ephemeralKeyId));
		}

		if ( !ekey.getStatus().equals("published") ) {
			throw new WeaveException(String.format("Ephemeral key '%s' already provisioned", ephemeralKeyId));
		}
		
		ekey.setStatus("provisioned");
		try {
			CommsStorage.updateEphemeralKey(db, ekey);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt update ephemeral key '%s'", ephemeralKeyId));
		}
        
		MessageSession sess = new MessageSession();
		sess.setSessionId(ephemeralKeyId + otherEphemeralKeyId);
		sess.setEphemeralKeyId(ephemeralKeyId);
		sess.setOtherClientId(otherClientId);
		sess.setOtherIdentityKey(otherIdentityKey);
		sess.setOtherEphemeralKeyId(otherEphemeralKeyId);
		sess.setOtherEphemeralKey(otherEphemeralKey);
		try {
			CommsStorage.createMessageSession(db, sess);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt create message session '%s'", sess.getSessionId()));
		}
				
		return sess;
	}

	public MessageSession createOutgoingMessageSession(String otherClientId) throws WeaveException {

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
	        CommsStorage.createEphemeralKey(db, otherClientId, ekey);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt create ephemeral key '%s'", ephemeralKeyId));
		}

		//Randomly select an ephemeral key from other client
        Client otherClient = null;
		try {
	        otherClient = CommsStorage.getClient(db, otherClientId);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt get client '%s'", otherClientId));
		}
        
		List<EphemeralKey> ekeys = otherClient.getEphemeralKeys();
		int keyIndex = (int)(Math.random() * ekeys.size());
		EphemeralKey otherEphemeralKey = ekeys.get(keyIndex);

		MessageSession sess = new MessageSession();
		sess.setSessionId(ephemeralKeyId + otherEphemeralKey.getKeyId());
		sess.setEphemeralKeyId(ephemeralKeyId);
		sess.setOtherClientId(otherClient.getClientId());
		sess.setOtherIdentityKey(otherClient.getPublicKey());
		sess.setOtherEphemeralKeyId(otherEphemeralKey.getKeyId());
		sess.setOtherEphemeralKey(otherEphemeralKey.getPublicKey());
		try {
			CommsStorage.createMessageSession(db, sess);
		} catch (SQLException e) {
			throw new WeaveException(String.format("Couldnt create message session '%s'", sess.getSessionId()));
		}
				
		return sess;
	}

	public Double sendMessage(Message msg) throws WeaveException {
		try {
			CommsStorage.createMessage(db, msg);
		} catch (SQLException e) {
			throw new WeaveException(e);
		}
		return commsApi.putMessage(msg.getEncodedMessage());
	}

	private boolean validateMessageSession(Message msg) throws WeaveException {
		
		boolean valid = false;

		MessageSession session = getMessageSession(msg.getMessageSessionId());
		if (
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
		
		//FIXME - store timestamp of last time messages were checked
		
		String[] msgIds = null;
		
		try {
			msgIds = commsApi.getMessageIds(null);
		} catch (NotFoundException e) {
			throw new WeaveException("Couldn't check messages - " + e.getMessage());
		}
		
		Client selfClient = null;
		try {
			selfClient = CommsStorage.getClient(db, clientId);
		} catch (SQLException e) {
			throw new WeaveException("Couldn't load client from local storage - " + e.getMessage());
		}
		
		for (int i = 0; i < msgIds.length; i++) {
			if ( selfClient.getEphemeralKey(msgIds[i]) != null ) {
				try {
					Message msg = commsApi.getMessage(msgIds[i]);
					
					if ( msg.getSequence() == 1 ) {
						try {
							createIncomingMessageSession(msg);
						} catch (WeaveException e) {
							Log.getInstance().error(String.format("Couldn't create message session for keyid '%s'", msgIds[i]));
							continue;
						}
					} else if ( !validateMessageSession(msg) ) {
						Log.getInstance().error(String.format("Message session invalid for keyid '%s'", msgIds[i]));
						continue;
					}
					
					CommsStorage.createMessage(db, msg);
					
				} catch (NotFoundException e) {
					Log.getInstance().warn(String.format("Message not found for keyid '%s'", msgIds[i]));
					continue;
				} catch (SQLException e) {
					Log.getInstance().error(String.format("Error saving message in local storage for keyid '%s'", msgIds[i]));
					continue;					
				}
			}
		}
		
		updateClient();
	}
	
}
