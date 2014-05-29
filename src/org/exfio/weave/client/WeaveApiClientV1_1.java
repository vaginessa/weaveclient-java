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
package org.exfio.weave.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.NameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;  
import org.json.simple.parser.ParseException;
import org.exfio.weave.Log;
import org.exfio.weave.URIUtils;
import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpClient;


public class WeaveApiClientV1_1 extends WeaveApiClient {
		
	private URI baseURL;	
	private String user;
	private String password;
	
	private URI storageURL;
	
	private HttpClient httpClient = HttpClient.getInstance();
	
	public void init(String baseURL, String user, String password) throws WeaveException {

		this.user     = user;
		this.password = password;

		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}

		//Get storageURL
		URI location = this.baseURL.resolve(URIUtils.sanitize(String.format("/user/1.1/%s/node/weave", this.user)));		
		try {
			HttpEntity entity = httpClient.get(location);
			storageURL = new URI(EntityUtils.toString(entity)); 
		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (URISyntaxException e) {  
			throw new WeaveException(e);  
		}
		
		//Set HTTP Auth credentials
		HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(new BasicCredentialsProvider());				
		context.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(this.user, this.password));
		
		//initialise pre-emptive authentication
		HttpHost host = new HttpHost(storageURL.getHost(), storageURL.getPort(), storageURL.getScheme());
		AuthCache authCache = context.getAuthCache();
		if (authCache == null)
			authCache = new BasicAuthCache();
		authCache.put(host, new BasicScheme());
		context.setAuthCache(authCache);
		
		httpClient.setContext(context);
	}
	
	public Map<String, WeaveCollectionInfo> getInfoCollections(boolean getcount, boolean getusage) throws WeaveException {
		Log.getInstance().debug( "getInfoCollections()");
		
		Map<String, WeaveCollectionInfo> wcols = new HashMap<String, WeaveCollectionInfo>();
		URI location = null;
		JSONObject jsonObject = null;
		
		//Always get info/collections
		location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/info/collections", this.user)));			
		jsonObject = getJSONPayload(location);

		@SuppressWarnings("unchecked")
		Iterator<String> itCol = jsonObject.keySet().iterator();
		while ( itCol.hasNext() ) {
			String collection = itCol.next();
			WeaveCollectionInfo wcolInfo = new WeaveCollectionInfo(collection);
			wcolInfo.modified = (Double)jsonObject.get(collection);
			wcols.put(collection, wcolInfo);
		}

		//Optionally get info/collection_counts
		if ( getcount ) {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/info/collection_counts", this.user)));			
			jsonObject = getJSONPayload(location);
	
			@SuppressWarnings("unchecked")
			Iterator<String> itQuota = jsonObject.keySet().iterator();
			while ( itQuota.hasNext() ) {
				String collection = itQuota.next();
				if ( wcols.containsKey(collection) ) {
					wcols.get(collection).count = (Long)jsonObject.get(collection);
				} else {
					//quietly do nothing
					//throw new WeaveException(String.format("Collection '%s' not in info/collections", collection));
				}
			}
		}
		
		//Optionally get info/collection_usage
		if ( getusage ) {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/info/collection_usage", this.user)));			
			jsonObject = getJSONPayload(location);
	
			@SuppressWarnings("unchecked")
			Iterator<String> itUsage = jsonObject.keySet().iterator();
			while ( itUsage.hasNext() ) {
				String collection = itUsage.next();
				if ( wcols.containsKey(collection) ) {
					wcols.get(collection).usage = (Double)jsonObject.get(collection);
				} else {
					//quietly do nothing
					//throw new WeaveException(String.format("Collection '%s' not in info/collections", collection));
				}
			}
		}
		
		return wcols;
	}
	
	public WeaveBasicObject get(String collection, String id) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s/%s", this.user, collection, id)));			
		return this.get(location);
	}
	
	public WeaveBasicObject get(String path) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s", this.user, path)));
		return this.get(location);
	}

	public WeaveBasicObject get(URI location) throws WeaveException {
		Log.getInstance().debug( "get()");
		
		JSONObject jsonObject = getJSONPayload(location);

		//parse request content to extract JSON encoded WeaveBasicObject
		try {
			String id         = (String)jsonObject.get("id");
			Double modified   = (Double)jsonObject.get("modified");
			Long sortindex    = (Long)jsonObject.get("sortindex");
			String payload    = (String)jsonObject.get("payload");
			Long ttl          = (Long)jsonObject.get("ttl");
			
			return new WeaveBasicObject(id, modified, sortindex, ttl, payload);
			
		} catch (ClassCastException e) {
			throw new WeaveException(e);
		}
	}

	public JSONObject getJSONPayload(URI location) throws WeaveException {
		return getJSONPayload(location, false);
	}

	@SuppressWarnings("unchecked")
	public JSONObject getJSONPayload(URI location, boolean isArray) throws WeaveException {
		Log.getInstance().debug( "getJSONPayload()");
		
		JSONObject jsonObject = null;

		//parse request content to extract JSON encoded object
		try {

			HttpEntity entity = httpClient.get(location);

			JSONParser parser = new JSONParser();  
			BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
			if ( isArray ) {
				JSONArray jsonArray = (JSONArray)parser.parse(br);
				jsonObject = new JSONObject();
				jsonObject.put(null, jsonArray);
			} else {
				jsonObject = (JSONObject)parser.parse(br);
			}

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (ParseException e) {  
			throw new WeaveException(e);  
		}
		
		return jsonObject;
	}

	private URI buildCollectionUri(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean full) throws WeaveException {

		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s", this.user, collection)));
		
		//Build list of URL querystring parameters
		List<NameValuePair> params = new LinkedList<NameValuePair>();
		
		if ( ids != null && ids.length > 0 ) {
			for (int i = 0; i < ids.length; i++) {
				params.add(new BasicNameValuePair("ids[]", ids[i]));
			}
		}
		if (older != null) {
			params.add(new BasicNameValuePair("older", older.toString()));
		}
		if (newer != null) {
			params.add(new BasicNameValuePair("newer", newer.toString()));
		}
		if (index_above != null) {
			params.add(new BasicNameValuePair("index_above", index_above.toString()));			
		}
		if (index_below!= null) {
			params.add(new BasicNameValuePair("index_below", index_below.toString()));			
		}
		if (limit != null) {
			params.add(new BasicNameValuePair("limit", limit.toString()));			
		}
		if (offset != null) {
			params.add(new BasicNameValuePair("offset", offset.toString()));			
		}
		if (sort != null) {
			sort = sort.toLowerCase();
			if ( sort.matches("oldest|newest|index") ) {
				params.add(new BasicNameValuePair("sort", sort.toString()));
			} else {
				throw new WeaveException(String.format("getCollection() sort parameter value of '%s' not recognised", sort));
			}
		}
		if (format != null) {
			//Only default format supported
			throw new WeaveException(String.format("getCollection() format parameter value of '%s' not supported", format));
		}
		if ( full ) {
			//returns entire WBO
			params.add(new BasicNameValuePair("full", "1"));
		}

		try {
			location = new URI(
					location.getScheme(),
					location.getUserInfo(),
					location.getHost(),
					location.getPort(),
					location.getPath(),
					URLEncodedUtils.format(params, "UTF-8"),
					location.getFragment()
			);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		
		return location;
	}

	public String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException {
		URI location = buildCollectionUri(collection, ids, older, newer, index_above, index_below, limit, offset, sort, null, false);
		return getCollectionIds(location);	
	}

	public String[] getCollectionIds(URI location) throws WeaveException {
		Log.getInstance().debug( "getCollectionIds()");
		
		List<String> ids = new LinkedList<String>();
		
		//Get JSON payload and extract JSON array
		JSONObject jsonTmp = getJSONPayload(location, true);
		JSONArray jsonArray = (JSONArray)jsonTmp.get(null);

		//Iterate through jsonArray and build list of ids
		try {
			@SuppressWarnings("unchecked")
			Iterator<String> iterator = jsonArray.iterator();
			while ( iterator.hasNext() ) {
				ids.add((String)iterator.next());
			}	
		} catch (ClassCastException e) {
			throw new WeaveException(e);
		}
		
		return ids.toArray(new String[0]);
	}

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException {
		URI location = buildCollectionUri(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format, true);
		return getCollection(location);	
	}
		
	public WeaveBasicObject[] getCollection(URI location) throws WeaveException {
		Log.getInstance().debug( "getCollection()");
		
		List<WeaveBasicObject> listWbo = new LinkedList<WeaveBasicObject>();

		//Get JSON payload and extract JSON array
		JSONObject jsonTmp = getJSONPayload(location, true);
		JSONArray jsonArray = (JSONArray)jsonTmp.get(null);

		//Iterate through jsonArray and build WBOs
		try {
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> iterator = jsonArray.iterator();
			while ( iterator.hasNext() ) {
				JSONObject jsonObject = (JSONObject)iterator.next();
				
				String id         = (String)jsonObject.get("id");
				Double modified   = (Double)jsonObject.get("modified");
				Long sortindex    = (Long)jsonObject.get("sortindex");
				String payload    = (String)jsonObject.get("payload");
				Long ttl          = (Long)jsonObject.get("ttl");
				
				listWbo.add(new WeaveBasicObject(id, modified, sortindex, ttl, payload));
			}	
		} catch (ClassCastException e) {
			throw new WeaveException(e);
		}
		
		return listWbo.toArray(new WeaveBasicObject[0]);
	}

	@SuppressWarnings("unchecked")
	private String encodeWeaveBasicObject(WeaveBasicObject wbo) {
		JSONObject jobj = new JSONObject();
		
		jobj.put("id", wbo.id);
		jobj.put("payload", wbo.payload);
		if ( wbo.modified != null ) {
			jobj.put("modified", wbo.modified);
		}
		if ( wbo.sortindex != null ) {
			jobj.put("sortindex", wbo.sortindex);
		}
		if ( wbo.ttl != null ) {
			jobj.put("ttl", wbo.ttl);
		}
		
		return jobj.toJSONString();
	}

	public Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s/%s", this.user, collection, id)));
		return put(location, wbo);
	}
	
	public Double put(URI location, WeaveBasicObject wbo) throws WeaveException {
		Log.getInstance().debug("put()");
		
		String content = encodeWeaveBasicObject(wbo);
		
		//parse request content to extract server modified time
		Double modified = null;
		try {

			HttpEntity entity = httpClient.put(location, content);
			modified = Double.parseDouble(EntityUtils.toString(entity)); 

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		}
		
		return modified;
	}

	public void delete(String collection, String id) throws WeaveException {
		URI location = null;
		if (id == null) {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s", this.user, collection)));
		} else {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s/%s", this.user, collection, id)));			
		}
		this.delete(location);
	}
	
	public void delete(URI location) throws WeaveException {
		Log.getInstance().debug( "get()");
		
		try {

			@SuppressWarnings("unused")
			HttpEntity entity = httpClient.delete(location);

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		}	
	}

}
