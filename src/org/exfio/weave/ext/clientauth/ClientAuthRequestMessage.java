package org.exfio.weave.ext.clientauth;

import org.exfio.weave.WeaveException;
import org.exfio.weave.ext.comm.Message;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class ClientAuthRequestMessage extends Message {
	
	//Authentication data struct
	public static final String KEY_AUTH_INNERSALT = "innersalt";
	public static final String KEY_AUTH_SALT      = "salt";
	public static final String KEY_AUTH_DIGEST    = "digest";

	//Client auth request message
	public static final String KEY_CLIENTAUTH_REQUEST_CLIENTID  = "clientid";
	public static final String KEY_CLIENTAUTH_REQUEST_NAME      = "name";
	public static final String KEY_CLIENTAUTH_REQUEST_AUTH      = "auth";     //authentication data struct


	protected String clientId;
	protected String clientName;
	protected ClientAuthVerifier auth;

	public ClientAuthRequestMessage() {
		messageType = ClientAuth.MESSAGE_TYPE_CLIENTAUTHREQUEST;
	}
	
	public ClientAuthRequestMessage(Message msg) throws WeaveException {
		super(msg);
		messageType = ClientAuth.MESSAGE_TYPE_CLIENTAUTHREQUEST;
		initFromEncodedMessage(msg.getEncodedMessage());
	}
	
	public void initFromEncodedMessage(EncodedMessage msg) throws WeaveException {
		
		//Populate local variables from content
		JSONObject caObject = null;
		try {
			caObject = msg.getContentAsJSONObject();
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		
		if ( caObject != null ) {
			initFromJSONObject(caObject);
		}
	}
	
	public void initFromJSONObject(JSONObject caObject) throws WeaveException {

		if ( !validateContentJson(caObject) ) {
			throw new WeaveException("ClientAuthRequestMessage content invalid");
		}
		
		clientId   = (String)caObject.get(KEY_CLIENTAUTH_REQUEST_CLIENTID);
		clientName = (String)caObject.get(KEY_CLIENTAUTH_REQUEST_NAME);
		
		//Authentication verifier
		String authString = (String)caObject.get(KEY_CLIENTAUTH_REQUEST_AUTH);
		
		JSONObject authObject = null;
		try {
			JSONParser parser = new JSONParser();
			authObject = (JSONObject)parser.parse(authString);
		} catch (ParseException e) {
			throw new WeaveException(e);
		}
		
		auth = new ClientAuthVerifier();
		auth.setInnerSalt((String)authObject.get(KEY_AUTH_INNERSALT));
		auth.setSalt((String)authObject.get(KEY_AUTH_SALT));
		auth.setDigest((String)authObject.get(KEY_AUTH_DIGEST));
	}
	
	private boolean validateContentJson(JSONObject payload) {
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
	
	public EncodedMessage getEncodedMessage() {
		EncodedMessage msg = new EncodedMessage(this);
		msg.setContent(getContentAsJSONString());
		return msg;
	}
	
	@SuppressWarnings("unchecked")
	public String getContentAsJSONString() {

		//Build message type specific fields
		JSONObject caObject = new JSONObject();
		caObject.put(KEY_CLIENTAUTH_REQUEST_CLIENTID, getClientId());
		caObject.put(KEY_CLIENTAUTH_REQUEST_NAME, getClientName());
		
		//Authentication verifier
		JSONObject authObject = new JSONObject();
		authObject.put(KEY_AUTH_INNERSALT, getAuth().getInnerSalt());
		authObject.put(KEY_AUTH_SALT, getAuth().getSalt());
		authObject.put(KEY_AUTH_DIGEST, getAuth().getDigest());
		caObject.put(KEY_CLIENTAUTH_REQUEST_AUTH, authObject);
	
		return caObject.toJSONString();
	}

	@Data
	public static class ClientAuthVerifier {
		
		private String innerSalt;
		private String salt;
		private String digest;
	}

}

