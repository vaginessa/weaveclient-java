package org.exfio.weave.client;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


@Data
public class WeaveBasicObject {
	protected String id;
	protected Double modified;
	protected Long sortindex;
	protected Long ttl;
	protected String payload;
	
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
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
