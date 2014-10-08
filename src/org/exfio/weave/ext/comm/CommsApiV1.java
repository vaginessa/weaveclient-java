package org.exfio.weave.ext.comm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.client.NotFoundException;
import org.exfio.weave.client.WeaveBasicObject;
import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.client.WeaveClientFactory;
import org.exfio.weave.crypto.PayloadCipher;
import org.exfio.weave.crypto.WeaveKeyPair;
import org.exfio.weave.ext.comm.Client.EphemeralKey;
import org.exfio.weave.ext.comm.Message.EncodedMessage;
import org.exfio.weave.ext.crypto.ECDH;
import org.exfio.weave.ext.crypto.ECKeyPair;
import org.exfio.weave.util.Log;

public class CommsApiV1 {

	public static final String PROTO_CLIENT_VERSION      = "1";
	public static final String PROTO_MESSAGE_VERSION     = "1";
	
	public static final int CLIENT_EPHEMERAL_KEYS_NUM    = 10;
	
	public static final String KEY_META_PATH            = "meta/exfio";
	public static final String KEY_META_COLLECTION      = "meta";
	public static final String KEY_META_ID              = "exfio";
	public static final String KEY_META_CLIENT_VERSION  = "clientVersion";
	public static final String KEY_META_MESSAGE_VERSION = "messageVersion";

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
	public static final String KEY_MESSAGE_SOURCE_CLIENTID      = "srcclientid";
	public static final String KEY_MESSAGE_SOURCE_KEYID         = "srckeyid";
	public static final String KEY_MESSAGE_SOURCE_KEY           = "srckey";
	public static final String KEY_MESSAGE_DESTINATION_CLIENTID = "dstclientid";
	public static final String KEY_MESSAGE_DESTINATION_KEYID    = "dstkeyid";
	public static final String KEY_MESSAGE_SEQUENCE             = "sequence";
	public static final String KEY_MESSAGE_TYPE                 = "type";
	public static final String KEY_MESSAGE_CONTENT              = "content";

	private WeaveClient wc;
			
	
	public CommsApiV1(WeaveClient wc) {
		init(wc);
	}

	private void init(WeaveClient wc) {
		this.wc = wc;
				
		java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}

	@SuppressWarnings("unchecked")
	private
	static JSONObject encodeClientWeavePayload(Client client) {
		
		JSONObject payloadClient = new JSONObject();
		
		payloadClient.put(KEY_CLIENT_VERSION, PROTO_CLIENT_VERSION);
		payloadClient.put(KEY_CLIENT_ID, client.getClientId());
		payloadClient.put(KEY_CLIENT_NAME, client.getClientName());
		payloadClient.put(KEY_CLIENT_IDENTITY_KEY, client.getPublicKey());
		payloadClient.put(KEY_CLIENT_AUTHLEVEL, client.getAuthLevel());
		payloadClient.put(KEY_CLIENT_STATUS, client.getStatus());
		String hmac = "";
		if ( client.getStatus() == "authorised" ) {
			//FIXME - calculate hmac
			hmac = "TODO";
		}
		payloadClient.put(KEY_CLIENT_HMAC, hmac);			

		//Build ephemeral keys JSONUtils
		JSONArray jsonEKeys = new JSONArray();

		Iterator<EphemeralKey> iter = client.getEphemeralKeys().listIterator(); 
		while ( iter.hasNext() ) {
			EphemeralKey key = iter.next();
			
			JSONObject keyObject = new JSONObject();
			keyObject.put(KEY_CRYPTOKEY_KEYID, key.getKeyId());
			keyObject.put(KEY_CRYPTOKEY_KEY, key.getPublicKey());
			jsonEKeys.add(keyObject);
		}
		payloadClient.put(KEY_CLIENT_EPHEMERAL_KEYS, jsonEKeys);
		
		return payloadClient;
	}

	private static Client decodeClientWeavePayload(JSONObject payload) throws WeaveException {
		return decodeClientWeavePayload(payload, new Client());
	}

	private static Client decodeClientWeavePayload(JSONObject payload, Client client) throws WeaveException {

		if ( !validateClientJson(payload) ) {
			throw new WeaveException("Client record invalid");
		}
			
		client.setClientId((String)payload.get(KEY_CLIENT_ID));
		client.setClientName((String)payload.get(KEY_CLIENT_NAME));
		client.setPublicKey((String)payload.get(KEY_CLIENT_IDENTITY_KEY));
		client.setAuthLevel((String)payload.get(KEY_CLIENT_AUTHLEVEL));
		client.setStatus((String)payload.get(KEY_CLIENT_STATUS));
		client.setVersion((String)payload.get(KEY_CLIENT_VERSION));
		
		List<EphemeralKey> clientKeys = new LinkedList<EphemeralKey>();
		
		JSONArray ekeys = (JSONArray)payload.get(KEY_CLIENT_EPHEMERAL_KEYS);
		
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> iter = ekeys.listIterator();
		while ( iter.hasNext() ) {
			JSONObject ekey = iter.next();
			EphemeralKey clientKey = new EphemeralKey();
			clientKey.setKeyId((String)ekey.get(KEY_CRYPTOKEY_KEYID));
			clientKey.setPublicKey((String)ekey.get(KEY_CRYPTOKEY_KEY));
			clientKey.setStatus("published");
			clientKeys.add(clientKey);
		}
		client.setEphemeralKeys(clientKeys);
		
		return client;
	}

	private static boolean isEncryptedMessage(EncodedMessage msg) throws ParseException {
		//Determine if ClientAuthReqMessage content is encrypted or not
		JSONObject jsonContent = msg.getContentAsJSONObject();
		return ( jsonContent.containsKey("ciphertext") && jsonContent.containsKey("IV") && jsonContent.containsKey("hmac") );
	}

	private static JSONObject encodeMessageWeavePayload(EncodedMessage msg) {
		return encodeMessageWeavePayload(msg, new JSONObject());
	}
	
	@SuppressWarnings("unchecked")
	private static JSONObject encodeMessageWeavePayload(EncodedMessage msg, JSONObject msgObject) {

		//Build message payload
		msgObject.put(KEY_MESSAGE_VERSION, PROTO_MESSAGE_VERSION);
		msgObject.put(KEY_MESSAGE_SOURCE_CLIENTID, msg.getSourceClientId());
		msgObject.put(KEY_MESSAGE_SOURCE_KEYID, msg.getSourceKeyId());
		msgObject.put(KEY_MESSAGE_SOURCE_KEY, msg.getSourceKey());
		msgObject.put(KEY_MESSAGE_DESTINATION_CLIENTID, msg.getDestinationClientId());
		msgObject.put(KEY_MESSAGE_DESTINATION_KEYID, msg.getDestinationKeyId());
		msgObject.put(KEY_MESSAGE_TYPE, msg.getMessageType());
		msgObject.put(KEY_MESSAGE_SEQUENCE, msg.getSequence());
		msgObject.put(KEY_MESSAGE_CONTENT, msg.getContent());
		
		return msgObject;
	}
	
	private static EncodedMessage decodeMessageWeavePayload(JSONObject payload) throws WeaveException {
		return decodeMessageWeavePayload(payload, new EncodedMessage());
	}

	private static EncodedMessage decodeMessageWeavePayload(JSONObject payload, EncodedMessage msg) throws WeaveException{

		if ( !validateMessageJson(payload) ) {
			throw new WeaveException("Message record invalid");
		}
		
		msg.setVersion((String)payload.get(KEY_MESSAGE_VERSION));
		msg.setSourceClientId((String)payload.get(KEY_MESSAGE_SOURCE_CLIENTID));
		msg.setSourceKeyId((String)payload.get(KEY_MESSAGE_SOURCE_KEYID));
		msg.setSourceKey((String)payload.get(KEY_MESSAGE_SOURCE_KEY));
		msg.setDestinationClientId((String)payload.get(KEY_MESSAGE_DESTINATION_CLIENTID));
		msg.setDestinationKeyId((String)payload.get(KEY_MESSAGE_DESTINATION_KEYID));
		msg.setMessageType((String)payload.get(KEY_MESSAGE_TYPE));
		msg.setSequence((Long)payload.get(KEY_MESSAGE_SEQUENCE));
		msg.setContent((String)payload.get(KEY_MESSAGE_CONTENT));
		
		return msg;
	}
		
	private static boolean validateKeyJson(JSONObject payload) {
		if (!(
				payload.containsKey(KEY_CRYPTOKEY_KEYID) && payload.get(KEY_CRYPTOKEY_KEYID) instanceof String
				&&
				payload.containsKey(KEY_CRYPTOKEY_KEY) && payload.get(KEY_CRYPTOKEY_KEY) instanceof String
			)) {
				return false;
			}
		return true;
	}
	
	private static boolean validateClientJson(JSONObject payload) {
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
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> iter = ekeys.iterator();
		while (iter.hasNext()) {
			JSONObject keyObject = iter.next();
			if (!validateKeyJson(keyObject)) {
				return false;
			}
		}
		
		return true;
	}

	private static boolean validateMessageJson(JSONObject payload) {
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
	
	@SuppressWarnings("unused")
	private EncodedMessage decryptMessage(EncodedMessage msg, ECKeyPair clientKeyPair, ECKeyPair ephemeralKeyPair) throws WeaveException {
		try {
			if ( !isEncryptedMessage(msg) ) {
				throw new WeaveException("ClientAuthReqMessage content already decrypted");
			}
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		
		//Get 3DHE key pair for session
		//EphemeralKey sessionKey = ClientAuthStorage.getEphemeralKey(db, msg.getSession().getEphemeralKeyId());
		ECDH ecdh = new ECDH();
		WeaveKeyPair keyPair = ecdh.get3DHEKeyPair(clientKeyPair.getPrivateKey(), ephemeralKeyPair.getPrivateKey() , msg.getSession().getOtherIdentityKey(), msg.getSession().getOtherEphemeralKey());

		//Decrypt message content
		EncodedMessage decryptMsg = new EncodedMessage(msg);
		decryptMsg.setContent(decryptMessageContent(msg.getContent(), keyPair));
		return decryptMsg;
	}
	
	private String decryptMessageContent(String content, WeaveKeyPair keyPair) throws WeaveException {
		PayloadCipher cipher = new PayloadCipher();
        return cipher.decrypt(content, keyPair);        
	}

	@SuppressWarnings("unused")
	private EncodedMessage encryptMessage(EncodedMessage msg, ECKeyPair clientKeyPair, ECKeyPair ephemeralKeyPair) throws WeaveException {
		try {
			if ( isEncryptedMessage(msg) ) {
				throw new WeaveException("ClientAuthReqMessage content already encrypted");
			}
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		
		//Get 3DHE key pair for session
		//EphemeralKey sessionKey = ClientAuthStorage.getEphemeralKey(db, msg.getSession().getEphemeralKeyId());
		ECDH ecdh = new ECDH();
		WeaveKeyPair keyPair = ecdh.get3DHEKeyPair(clientKeyPair.getPrivateKey(), ephemeralKeyPair.getPrivateKey() , msg.getSession().getOtherIdentityKey(), msg.getSession().getOtherEphemeralKey());

		//Encrypt message content
		EncodedMessage encryptMsg = new EncodedMessage(msg);
		encryptMsg.setContent(encryptMessageContent(msg.getContent(), keyPair));
		return encryptMsg;
	}

	/**
	 * encrypt()
	 *
	 * Given a plaintext object, encrypt it and return the ciphertext value.
	 */
	private String encryptMessageContent(String plaintext, WeaveKeyPair keyPair) throws WeaveException {		
		Log.getInstance().debug( "encryptMessageContent()");
		Log.getInstance().debug( "plaintext:\n" + plaintext);
	        		
		PayloadCipher cipher = new PayloadCipher();
		return cipher.encrypt(plaintext, keyPair);
	}
	
	public boolean isInitialised() throws WeaveException {
		//Default to true as false negative could result in reset
		boolean meta = true;
		
		@SuppressWarnings("unused")
		WeaveBasicObject wboMeta = null;
		try {
			wboMeta = wc.get(KEY_META_COLLECTION, KEY_META_ID, false);
		} catch (NotFoundException e) {
			meta = false;
		}
		
		return meta;
	}

	@SuppressWarnings("unchecked")
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

		//create meta/exfio record with exfio client and message version
		JSONObject metaObject = new JSONObject();
		metaObject.put("clientVersion", PROTO_CLIENT_VERSION);
		metaObject.put("messageVersion", PROTO_MESSAGE_VERSION);

		WeaveBasicObject wboMeta = new WeaveBasicObject(KEY_META_ID, null, null, null, metaObject.toJSONString());
		
		//Note meta/exfio is NOT encrypted
		wc.put(KEY_META_COLLECTION, KEY_META_ID, wboMeta, false);
		
		//FIXME - create bulk keys for exfioclient and exfiomessage collections

	}
	
	public Client getClient(String clientId) throws WeaveException, NotFoundException {
		try {
			WeaveBasicObject wbo = wc.get(KEY_CLIENT_COLLECTION, clientId, false);
			return decodeClientWeavePayload(wbo.getPayloadAsJSONObject());
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
	}

	public Client[] getClients() throws WeaveException {
		WeaveBasicObject[] wbos = null;
		try {
			wbos = wc.getCollection(KEY_CLIENT_COLLECTION, null, null, null, null, null, null, null, null, null, false);
		} catch (NotFoundException e) {
			throw new WeaveException(e);
		}
		
		List<Client> clients = new ArrayList<Client>();
		for (int i = 0; i < wbos.length; i++) {
			try {
				clients.add(decodeClientWeavePayload(wbos[i].getPayloadAsJSONObject()));
			} catch (ParseException e) {
				Log.getInstance().error("Couldn't parse client record - " + e.getMessage());
				continue;
			} catch (WeaveException e) {
				Log.getInstance().error(e.getMessage());
				continue;
			}
		}
		return clients.toArray(new Client[0]);
	}

	public Double putClient(Client client) throws WeaveException {
		JSONObject payloadClient = encodeClientWeavePayload(client);
		WeaveBasicObject wbo = new WeaveBasicObject(client.getClientId(), null, null, null, payloadClient.toJSONString());		
		return wc.put(KEY_CLIENT_COLLECTION, client.getClientId(), wbo, false);
	}

	public Double deleteClient(String clientId) throws WeaveException, NotFoundException {
		return wc.delete(KEY_CLIENT_COLLECTION, clientId);
	}
	
	public EncodedMessage getMessage(String keyId) throws WeaveException, NotFoundException {
		try {
			WeaveBasicObject wbo = wc.get(KEY_MESSAGE_COLLECTION, keyId, false);
			EncodedMessage msg = decodeMessageWeavePayload(wbo.getPayloadAsJSONObject());
			return msg;
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
	}

	public String[] getMessageIds(Double modifedSince) throws WeaveException, NotFoundException {
		return wc.getCollectionIds(KEY_MESSAGE_COLLECTION, null, null, modifedSince, null, null, null, null, null);
	}

	public Double putMessage(EncodedMessage msg) throws WeaveException {
		JSONObject payloadClient = encodeMessageWeavePayload(msg);
		WeaveBasicObject wbo = new WeaveBasicObject(msg.getDestinationKeyId(), null, null, null, payloadClient.toJSONString());		
		return wc.put(KEY_MESSAGE_COLLECTION, msg.getDestinationKeyId(), wbo, false);
	}

	public Double deleteMessage(String keyId) throws WeaveException, NotFoundException {
		return wc.delete(KEY_MESSAGE_COLLECTION, keyId);
	}
}
