package org.exfio.weave.account.exfiopeer;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.exfio.weave.WeaveException;
import org.exfio.weave.account.exfiopeer.comm.Message;

@Data
@EqualsAndHashCode(callSuper=true)
public class ClientAuthResponseMessage extends Message {
	
	//Client auth response message
	public static final String KEY_CLIENTAUTH_RESPONSE_CLIENTID = "clientid";
	public static final String KEY_CLIENTAUTH_RESPONSE_NAME     = "name";
	public static final String KEY_CLIENTAUTH_RESPONSE_STATUS   = "status";
	public static final String KEY_CLIENTAUTH_RESPONSE_MESSAGE  = "message";
	public static final String KEY_CLIENTAUTH_RESPONSE_SYNCKEY  = "synckey";

	private String clientId;
	private String clientName;
	private String status;
	private String message;
	private String syncKey;	

	public ClientAuthResponseMessage() {	
		messageType = ExfioPeerV1.MESSAGE_TYPE_CLIENTAUTHRESPONSE;
	}

	public ClientAuthResponseMessage(Message msg) throws WeaveException {
		super(msg);
		messageType = ExfioPeerV1.MESSAGE_TYPE_CLIENTAUTHRESPONSE;
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
			throw new WeaveException("ClientAuthResponseMessage content invalid");
		}
		
		clientId   = (String)caObject.get(KEY_CLIENTAUTH_RESPONSE_CLIENTID);
		clientName = (String)caObject.get(KEY_CLIENTAUTH_RESPONSE_NAME);
		status     = (String)caObject.get(KEY_CLIENTAUTH_RESPONSE_STATUS);
		message    = (String)caObject.get(KEY_CLIENTAUTH_RESPONSE_MESSAGE);	
		syncKey    = (String)caObject.get(KEY_CLIENTAUTH_RESPONSE_SYNCKEY);
	}

	private boolean validateContentJson(JSONObject payload) {
		//Validate client auth response message
		if (!(
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_CLIENTID) && payload.get(KEY_CLIENTAUTH_RESPONSE_CLIENTID) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_NAME) && payload.get(KEY_CLIENTAUTH_RESPONSE_NAME) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_STATUS) && payload.get(KEY_CLIENTAUTH_RESPONSE_STATUS) instanceof String
			&&
			payload.containsKey(KEY_CLIENTAUTH_RESPONSE_MESSAGE) && payload.get(KEY_CLIENTAUTH_RESPONSE_MESSAGE) instanceof String
			//synckey only required on okay response
			//&&
			//payload.containsKey(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) && payload.get(KEY_CLIENTAUTH_RESPONSE_SYNCKEY) instanceof String
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

		JSONObject caObject = new JSONObject();
		caObject.put(KEY_CLIENTAUTH_RESPONSE_CLIENTID, getClientId());
		caObject.put(KEY_CLIENTAUTH_RESPONSE_NAME, getClientName());
		caObject.put(KEY_CLIENTAUTH_RESPONSE_STATUS, getStatus());
		caObject.put(KEY_CLIENTAUTH_RESPONSE_MESSAGE, getMessage());			
		caObject.put(KEY_CLIENTAUTH_RESPONSE_SYNCKEY, getSyncKey());
	
		return caObject.toJSONString();
	}
}
	
