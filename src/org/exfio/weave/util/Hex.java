package org.exfio.weave.util;

import org.apache.commons.codec.DecoderException;

public final class Hex {
	
    /**
     * Hex-encode the given data and return a newly allocated
     * String with the result.
     *
     * <p>Compatible with org.apache.commons.codec.binary.Hex.
     * 
     * @param input  the data to encode
     */
	public static String encodeHexString(byte[] data) {
        return new String(org.apache.commons.codec.binary.Hex.encodeHex(data));
	}
	
	public static byte[] decodeHexString(String encoded) {
		try {
			return org.apache.commons.codec.binary.Hex.decodeHex(encoded.toCharArray());
		} catch (DecoderException e) {
			throw new AssertionError(e);
		}
	}

}
