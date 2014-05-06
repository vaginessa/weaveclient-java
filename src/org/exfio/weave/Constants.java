/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package org.exfio.weave;

import java.nio.charset.Charset;

public class Constants {
	public static final String
		ACCOUNT_TYPE = "weave.exfio.org",
		ADDRESSBOOK_COLLECTION = "exfiocontacts";
	public static final Charset ASCII = Charset.forName("US-ASCII");
	public static final Charset UTF8 = Charset.forName("UTF-8");
}
