package org.exfio.weave.account.fxa;

import lombok.Getter;
import lombok.Setter;

import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerToken;
import org.mozilla.gecko.tokenserver.TokenServerException.TokenServerMalformedResponseException;
import org.exfio.weave.WeaveException;

/**
 * Sync auth token JSON object
 * {
 *   "uid": 16999487, //FirefoxSync ID
 *   "hashalg": "sha256",
 *   "api_endpoint": "https://sync-176-us-west-2.sync.services.mozilla.com/1.5/16999487", //FirefoxSync storage endpoint
 *   "key": "G_QwGbDXc6aYtXVrhmO5-ymQZbyZQoES8q75a-eFyik=", //Hawk auth key. NOTE: DO NOT decode
 *   "id": "eyJub2RlIjogImh0dHBzOi8vc3luYy0xNzYtdXMtd2VzdC0yLnN5bmMuc2VydmljZXMubW96aWxsYS5jb20iLCAiZXhwaXJlcyI6IDE0MjIyNTQzNTMsICJzYWx0IjogIjdiYTQ0YyIsICJ1aWQiOiAxNjk5OTQ4N32olTf0a2mlUz9BezgYVASI_4hQ8nEl6VZVFM5RbwmQmA==" //Hawk auth id. NOTE: DO NOT decode
 *   "duration": 3600,
 * }
 * 
 */
public class FxAccountSyncToken {
	
	public static String JSON_KEY_EXPIRES = "expires";
	
	public String id       = null;
	public String key      = null;
	public String uid      = null;
	public String endpoint = null;
	public long duration   = 0;
	
	@Getter
	@Setter
	private long expires  = 0;
	
	public FxAccountSyncToken(TokenServerToken token) {
		this.id       = token.id;
		this.key      = token.key;
		this.uid      = token.uid;
		this.endpoint = token.endpoint;
		this.duration = token.duration;
	}

	public static FxAccountSyncToken fromTokenServerToken(TokenServerToken token) {
		return new FxAccountSyncToken(token);
	}

	public static FxAccountSyncToken fromJSONObject(ExtendedJSONObject jsonObject) throws WeaveException {
		try {
			TokenServerToken tsToken = TokenServerClient.fromJSONObject(jsonObject);
			FxAccountSyncToken fxaToken = new FxAccountSyncToken(tsToken);
			fxaToken.setExpires(jsonObject.getLong(JSON_KEY_EXPIRES));
			return fxaToken;
		} catch (TokenServerMalformedResponseException e) {
			throw new WeaveException(e);
		}
	}

	public ExtendedJSONObject toJSONObject() {
		ExtendedJSONObject jsonObject = new ExtendedJSONObject();
		jsonObject.put(TokenServerClient.JSON_KEY_UID, Long.parseLong(this.uid));
		jsonObject.put(TokenServerClient.JSON_KEY_ID, this.id);
		jsonObject.put(TokenServerClient.JSON_KEY_KEY, this.key);
		jsonObject.put(TokenServerClient.JSON_KEY_API_ENDPOINT, this.endpoint);
		jsonObject.put(TokenServerClient.JSON_KEY_DURATION, this.duration);
		jsonObject.put(JSON_KEY_EXPIRES, this.expires);
		return jsonObject;
	}
}
