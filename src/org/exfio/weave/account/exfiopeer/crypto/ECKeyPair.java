package org.exfio.weave.account.exfiopeer.crypto;

import lombok.Data;

@Data
public class ECKeyPair {
	private String PublicKey;
	private String PrivateKey;
}
