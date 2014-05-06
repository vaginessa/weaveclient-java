package org.exfio.weave.resource;

import lombok.Getter;
import lombok.ToString;

import org.json.simple.JSONObject;

@ToString
public class WeaveBasicObject {
	@Getter protected String id;
	@Getter protected Float modified;
	@Getter protected Integer sortindex;
	@Getter protected Integer ttl;
	@Getter protected String payload;
	@Getter protected JSONObject jsonPayload;
	@Getter protected boolean encrypted;
	
	public WeaveBasicObject(String id) {
		this.id = id;
	}

	public WeaveBasicObject(String id, Float modified, Integer sortindex, Integer ttl, String payload) {
		this.id          = id;
		this.modified    = modified;
		this.sortindex   = sortindex;
		this.ttl         = ttl;
		this.payload     = payload;
		this.jsonPayload = null;
		this.encrypted   = true;
	}

	public WeaveBasicObject(String id, Float modified, Integer sortindex, Integer ttl, String payload, JSONObject jsonPayload) {
		this.id          = id;
		this.modified    = modified;
		this.sortindex   = sortindex;
		this.ttl         = ttl;
		this.payload     = payload;
		this.jsonPayload = jsonPayload;
		this.encrypted   = false;
	}
}
