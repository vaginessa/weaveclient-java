package org.exfio.weave.client;

import lombok.Getter;
import lombok.ToString;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@ToString
public class WeaveBasicObject {
	@Getter protected String id;
	@Getter protected Double modified;
	@Getter protected Long sortindex;
	@Getter protected Long ttl;
	@Getter protected String payload;
	
	protected JSONObject jsonPayload;
	
	public WeaveBasicObject(String id) {
		this.id = id;
	}

	public WeaveBasicObject(String id, Double modified, Long sortindex, Long ttl, String payload) {
		this.id          = id;
		this.modified    = modified;
		this.sortindex   = sortindex;
		this.ttl         = ttl;
		this.payload     = payload;
		this.jsonPayload = null;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject getPayloadAsJSONObject() throws ParseException {
		if ( jsonPayload == null ) {
			JSONParser parser = new JSONParser();
			Object jsonTmp = parser.parse(payload);
			if ( jsonTmp instanceof JSONArray ) {
				//Wrap array in JSONObject
				jsonPayload = new JSONObject();
				jsonPayload.put(null, (JSONArray)jsonTmp);
			} else {
				jsonPayload = (JSONObject)jsonTmp;
			}
		}
		return jsonPayload;
	}
}
