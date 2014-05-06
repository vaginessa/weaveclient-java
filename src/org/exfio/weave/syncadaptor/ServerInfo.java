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
package org.exfio.weave.syncadaptor;

import java.io.Serializable;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(suppressConstructorProperties=true)
@Data
public class ServerInfo implements Serializable {
	private static final long serialVersionUID = 238330408340527325L;
	
	final private String providedURL;
	final private String userName, password, syncKey;
	final boolean authPreemptive;
	
	private ResourceInfo addressBook;
	
	private String errorMessage;
		
	@RequiredArgsConstructor(suppressConstructorProperties=true)
	@Data
	public static class ResourceInfo implements Serializable {
		private static final long serialVersionUID = 4962153552085743L;
		
		enum Type {
			ADDRESS_BOOK,
			CALENDAR
		}
		
		boolean enabled = false;
		
		final Type type;
		final boolean readOnly;
		final String URL, title, description, color;
		
		String timezone;
	}
}
