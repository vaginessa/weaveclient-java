package org.exfio.weave.account.fxa;

import lombok.Getter;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.DSACryptoImplementation;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.exfio.weave.WeaveException;
import org.exfio.weave.util.Log;

public class FxAccountCertificate {

	@Getter private BrowserIDKeyPair keyPair;
	@Getter private String certificate;

	public FxAccountCertificate(ExtendedJSONObject jsonObject) throws WeaveException {
		Log.getInstance().debug("FxAccountCertificate()");
		
		this.certificate = jsonObject.getString("certificate");		
		
		try {
			String alg = jsonObject.getObject(BrowserIDKeyPair.JSON_KEY_PRIVATEKEY).getString("algorithm");			
			if ( alg.equals("RS") ) {
				this.keyPair = RSACryptoImplementation.fromJSONObject(jsonObject);
			} else if ( alg.equals("DS") ) {
				this.keyPair = DSACryptoImplementation.fromJSONObject(jsonObject);
			} else {
				throw new WeaveException(String.format("BrowserID algorithm '%s' not recognised", alg));
			}
		} catch (NonObjectJSONException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new WeaveException("Couldn't parse BrowserID key pair - " + e.getMessage());
		}
	}
	
	public FxAccountCertificate(BrowserIDKeyPair keyPair, String certificate) {
		this.keyPair = keyPair;
		this.certificate = certificate;
	}

	public ExtendedJSONObject toJSONObject() {
		ExtendedJSONObject jsonObject = this.keyPair.toJSONObject();
		jsonObject.put("certificate", this.certificate);
		return jsonObject;
	}
	
	public static FxAccountCertificate fromJSONObject(ExtendedJSONObject jsonObject) throws WeaveException {
		return new FxAccountCertificate(jsonObject);
	}
}
