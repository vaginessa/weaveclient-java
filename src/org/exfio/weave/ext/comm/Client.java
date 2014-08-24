package org.exfio.weave.ext.comm;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class Client {
	
	private String  clientId;
	private boolean isSelf;
	private String  clientName;
	private String  publicKey;
	private String  privateKey;
	private String  status;
	private String  authLevel;
	private String  version;
	private Date    modifiedDate;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private List<EphemeralKey> ephemeralKeys;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Map<String, EphemeralKey> mapEphemeralKeys;
	
	public Client() {
		ephemeralKeys = new ArrayList<EphemeralKey>();
	}
	
	public EphemeralKey getEphemeralKey(String keyId) {
		if ( mapEphemeralKeys == null) {
			return null;
		} else {
			return mapEphemeralKeys.get(keyId);
		}
	}

	public List<EphemeralKey> getEphemeralKeys() {
		return new ArrayList<EphemeralKey>(ephemeralKeys);
	}

	public void setEphemeralKeys(List<EphemeralKey> ekeys) {
		this.ephemeralKeys = ekeys;
		
		mapEphemeralKeys = new HashMap<String, EphemeralKey>();
		Iterator<EphemeralKey> iter = this.ephemeralKeys.listIterator();
		while ( iter.hasNext() ) {
			EphemeralKey key = iter.next();
			mapEphemeralKeys.put(key.getKeyId(), key);
		}
	}

	@Data
	public static class EphemeralKey {
		
		private String KeyId;
		private String PublicKey;
		private String PrivateKey;
		private String status;
		private Date   modifiedDate;
	}

}
