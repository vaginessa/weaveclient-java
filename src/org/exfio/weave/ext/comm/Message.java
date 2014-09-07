package org.exfio.weave.ext.comm;

import java.util.Date;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lombok.Data;

@Data
public abstract class Message {
		
	protected int    messageId;
	protected String sourceClientId;
	protected String sourceIdentityKey;
	protected String sourceKeyId;
	protected String sourceKey;
	protected String destinationClientId;
	protected String destinationKeyId;
	protected String version;
	protected long   sequence;
	protected String messageType;
	protected Date   modifiedDate;

	protected MessageSession session;
	
	public Message() {
	}
	
	public Message(Message msg) {
		//Clone common message fields
		
		this.messageId           = msg.getMessageId();
		this.sourceClientId      = msg.getSourceClientId();
		this.sourceIdentityKey   = msg.getSourceIdentityKey();
		this.sourceKeyId         = msg.getSourceKeyId();
		this.sourceKey           = msg.getSourceKey();
		this.destinationClientId = msg.getDestinationClientId();
		this.destinationKeyId    = msg.getDestinationKeyId();
		this.version             = msg.getVersion();
		this.sequence            = msg.getSequence();
		this.messageType         = msg.getMessageType();
		this.modifiedDate        = msg.getModifiedDate();
		
		this.session             = msg.getSession();
	}
	
	public String getMessageSessionId() {
		return session.getSessionId();
	}
	
	public abstract EncodedMessage getEncodedMessage();
	
	public static class EncodedMessage extends Message {
		
		protected String content;
		protected JSONObject jsonContent = null;

		public EncodedMessage() {
			super();
		}

		public EncodedMessage(Message msg) {
			super(msg);
		}

		public EncodedMessage getEncodedMessage() {
			return this;
		}

		public String getContent() {
			return content;
		}
		
		public void setContent(String content) {
			this.content = content;
			this.jsonContent = null;
		}
		
		@SuppressWarnings("unchecked")
		public JSONObject getContentAsJSONObject() throws ParseException {
			if ( content == null || content.length() == 0 ) {
				return null;
			}
			
			if ( jsonContent == null ) {
				JSONParser parser = new JSONParser();
				Object jsonTmp = parser.parse(content);
				if ( jsonTmp instanceof JSONArray ) {
					//Wrap array in JSONObject
					jsonContent = new JSONObject();
					jsonContent.put(null, (JSONArray)jsonTmp);
				} else {
					jsonContent = (JSONObject)jsonTmp;
				}
			}
			return jsonContent;
		}
	}
		
	@Data
	public static class MessageSession {
		
		protected String sessionId;
		protected String ephemeralKeyId;
		protected String otherClientId;
		protected String otherIdentityKey;
		protected String otherEphemeralKeyId;
		protected String otherEphemeralKey;
		protected String state;
		
		public MessageSession() {	
		}
		
		public MessageSession(MessageSession session) {	
			this.sessionId           = session.getSessionId();
			this.ephemeralKeyId      = session.getEphemeralKeyId();
			this.otherClientId       = session.getOtherClientId();
			this.otherIdentityKey    = session.getOtherIdentityKey();
			this.otherEphemeralKeyId = session.getOtherEphemeralKeyId();
			this.otherEphemeralKey   = session.getOtherEphemeralKey();
		}
		
		public MessageSession(String ephemeralKeyId, String otherClientId, String otherIdentityKey, String otherEphemeralKeyId, String otherEphemeralKey) {
			this.sessionId           = ephemeralKeyId + otherEphemeralKeyId;
			this.ephemeralKeyId      = ephemeralKeyId;
			this.otherClientId       = otherClientId;
			this.otherIdentityKey    = otherIdentityKey;
			this.otherEphemeralKeyId = otherEphemeralKeyId;
			this.otherEphemeralKey   = otherEphemeralKey;
		}
	}
}
