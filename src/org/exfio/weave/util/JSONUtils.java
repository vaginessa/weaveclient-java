package org.exfio.weave.util;

public final class JSONUtils {
	
    /**
     * toDouble
     *
     * Convert Double or Long to Double
     * 
     * @param objectVal Object to convert to Double
     */
	public static Double toDouble(Object objectVal) {
		if ( objectVal instanceof Double ) {
			return (Double)objectVal;
		} else if ( objectVal instanceof Long ) {
			return ((Long)objectVal).doubleValue();
		} else {
			throw new AssertionError(String.format("Invalid object type '%s' expected Double or Long", objectVal.getClass()));
		}		
	}
}
