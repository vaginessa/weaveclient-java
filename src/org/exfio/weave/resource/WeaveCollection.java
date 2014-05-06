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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import lombok.Cleanup;
import lombok.Getter;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import android.util.Log;
import at.bitfire.davdroid.resource.InvalidResourceException;
import at.bitfire.davdroid.resource.RemoteResourceException;
import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.webdav.HttpException;

import org.exfio.weave.WeaveException;
import org.exfio.weave.client.WeaveClient;

public abstract class WeaveCollection<T extends Resource> {
	private final static String TAG = "exfio.WeaveCollection"; 

	protected WeaveClient weaveClient;
	protected String collection;
	protected Float modifiedTime;
	protected List<T> vobjResources;
	protected List<WeaveBasicObject> weaveResources;

	public WeaveCollection(WeaveClient weaveClient, String collection) {
		this.weaveClient = weaveClient;
		this.collection = collection;
	}
	
	abstract protected String memberContentType();	
	
	/* collection operations */
	
	public Float getModifiedTime() {
		//TODO GET info/collections
		return modifiedTime;
	}

	public String[] getObjectIds() {
		//TODO GET storage/collection
		return new String[0];
	}

	public String[] getObjectIdsModifiedSince(Float modifedDate) {
		//TODO GET storage/collection?newer=modifiedDate	
		return new String[0];
	}

	public abstract T[] multiGet(String[] ids) throws WeaveException;

	public T[] multiGet(Resource[] resources) throws WeaveException {
		String[] ids = new String[resources.length];
		for (int i = 0; i < resources.length; i++) {
			ids[i] = resources[i].getUid();
		}
		return multiGet(ids);
	}

	public abstract T get(String id) throws WeaveException;

	public T get(Resource res) throws WeaveException {
		return get(res.getUid());
	}

	public abstract void add(Resource res) throws WeaveException;
	
	public abstract void update(Resource res) throws WeaveException;

	public abstract void delete(String id) throws WeaveException;
	
	public void delete(Resource res) throws WeaveException {
		delete(res.getUid());
	}
}
