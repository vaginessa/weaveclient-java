package org.exfio.weave.ext.crypto;

import lombok.Data;

@Data
public class ECKeyPair {
	private String PublicKey;
	private String PrivateKey;
}
