package org.exfio.weave.util;

import java.lang.reflect.Method;

public class AndroidCompat {

	/**
	 * getPrettyName()
	 * 
	 * BluetoothAdapter device = BluetoothAdapter.getDefaultAdapter();
     * String prettyname = device.getName();
     */
	public static String getPrettyName() throws NoSuchMethodException {
		String prettyname = null;
		
		try {
			Class c  = Class.forName("android.BluetoothAdapter");
			Method m = c.getDeclaredMethod("getDefaultAdapter", null);
			Object o = m.invoke(null, null);
			m = c.getDeclaredMethod("getName", null);  
		    prettyname = (String)m.invoke(o, null);
		} catch (Exception e) {
			throw new NoSuchMethodException(e.getMessage());
		}
		
		return prettyname;
	}

}
