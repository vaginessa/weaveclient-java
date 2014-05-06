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
package org.exfio.weave.resource;

import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.resource.Contact;

import org.exfio.weave.client.WeaveClient;
import org.exfio.weave.Constants;
import org.exfio.weave.WeaveException;


public class WeaveAddressBook extends WeaveCollection<Contact> {
	private final static String TAG = "exfio.WeaveAddressBook";
	
	public WeaveAddressBook(WeaveClient weaveClient, String collection) {
		super(weaveClient, collection);
	}
	protected String memberContentType() {
		return "application/vcard+json";
	}
	
	/* internal member operations */
	public Contact[] multiGet(String[] ids) {		
		//TODO GET storage/collection?id=[id, id,...]&full=1
		
		//TODO set RemoteName to ID
		return new Contact[0];
	}

	public Contact get(String id) throws WeaveException {
		//GET storage/collection/id
		WeaveBasicObject wbo = this.weaveClient.get(this.collection, id);		
		
		//TODO - parse jcard
		return new Contact("foo", "bar");
	}

	public void add(Resource res) {
		add((Contact)res);	
	}

	public void add(Contact res) {
		//TODO PUT storage/collection/id		
	}
	
	public void update(Resource res) {
		update((Contact)res);	
	}

	public void update(Contact res) {
		//TODO confirm resource exists
		//TODO PUT storage/collection/id
	}
	
	public void delete(String id) {
		//TODO DELETE storage/collection/id		
	}
}
