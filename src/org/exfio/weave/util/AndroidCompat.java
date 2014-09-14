package org.exfio.weave.util;

import java.lang.reflect.Method;

public class AndroidCompat {

	/**
	 * getPrettyName()
	 * 
	 * BluetoothAdapter device = BluetoothAdapter.getDefaultAdapter();
     * String prettyname = device.getName();
     */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static String getPrettyName() throws NoSuchMethodException {
		String prettyname = null;
		
		try {
			Class c  = Class.forName("android.BluetoothAdapter");
			Method m = c.getDeclaredMethod("getDefaultAdapter", (Class)null);
			Object o = m.invoke(null, (Class)null);
			m = c.getDeclaredMethod("getName", (Class)null);  
		    prettyname = (String)m.invoke(o, (Class)null);
		} catch (Exception e) {
			throw new NoSuchMethodException(e.getMessage());
		}
		
		return prettyname;
	}

}
