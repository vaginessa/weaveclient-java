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
			Class cBluetoothAdapter  = Class.forName("android.bluetooth.BluetoothAdapter");
			Method mGetDefaultAdapter = cBluetoothAdapter.getDeclaredMethod("getDefaultAdapter");
			Method mGetName = cBluetoothAdapter.getDeclaredMethod("getName"); 

			Object o = mGetDefaultAdapter.invoke(null);
		    prettyname = (String)mGetName.invoke(cBluetoothAdapter.cast(o));
		} catch (Exception e) {
			throw new NoSuchMethodException(e.getMessage());
		}
		
		return prettyname;
	}

}
