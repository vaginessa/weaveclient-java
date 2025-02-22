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
package org.exfio.weave.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.exfio.weave.WeaveException;
import org.exfio.weave.net.HttpClient;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.util.Base64;
import org.exfio.weave.util.JSONUtils;
import org.exfio.weave.util.Log;
import org.exfio.weave.util.URIUtils;
import org.exfio.weave.client.PreconditionFailedException;
import org.exfio.weave.client.WeaveClientFactory.ApiVersion;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public abstract class StorageContext {
	
	protected HttpClient httpClient = null;
	protected ApiVersion version    = null;
	protected URI storageURL;
	
	public StorageContext() throws WeaveException {
		httpClient = new HttpClient();
	}
	
	public abstract void init(StorageParams storageParams) throws WeaveException;
	
	public ApiVersion getApiVersion() {
		return this.version;
	}
	
	public String generateWeaveID() {
		SecureRandom rnd = new SecureRandom();
		byte[] weaveID = rnd.generateSeed(9);
		return Base64.encodeToString(weaveID, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
	}
			
	public Map<String, WeaveCollectionInfo> getInfoCollections() throws WeaveException { return getInfoCollections(false, false); }
	
	public Map<String, WeaveCollectionInfo> getInfoCollections(boolean getcount, boolean getusage) throws WeaveException {
		Log.getInstance().debug( "getInfoCollections()");
		
		Map<String, WeaveCollectionInfo> wcols = new HashMap<String, WeaveCollectionInfo>();
		URI location = null;
		JSONObject jsonObject = null;
		
		//Always get info/collections
		location = this.storageURL.resolve("info/collections");
		try {
			jsonObject = getJSONPayload(location);
		} catch (NotFoundException e) {
			throw new WeaveException("info/collections record not found - " + e.getMessage());
		}
			
		@SuppressWarnings("unchecked")
		Iterator<String> itCol = jsonObject.keySet().iterator();
		while ( itCol.hasNext() ) {
			String collection = itCol.next();
			WeaveCollectionInfo wcolInfo = new WeaveCollectionInfo(collection);
			wcolInfo.modified = JSONUtils.toDouble(jsonObject.get(collection));
			wcols.put(collection, wcolInfo);
		}

		//Optionally get info/collection_counts
		if ( getcount ) {
			location = this.storageURL.resolve("info/collection_counts");			
			try {
				jsonObject = getJSONPayload(location);
			} catch (NotFoundException e) {
				throw new WeaveException("info/collection_counts record not found - " + e.getMessage());
			}
	
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
			location = this.storageURL.resolve("info/collection_usage");			
			try {
				jsonObject = getJSONPayload(location);
			} catch (NotFoundException e) {
				throw new WeaveException("info/collection_usage record not found - " + e.getMessage());
			}
	
			@SuppressWarnings("unchecked")
			Iterator<String> itUsage = jsonObject.keySet().iterator();
			while ( itUsage.hasNext() ) {
				String collection = itUsage.next();
				if ( wcols.containsKey(collection) ) {
					wcols.get(collection).usage = JSONUtils.toDouble(jsonObject.get(collection));
				} else {
					//quietly do nothing
					//throw new WeaveException(String.format("Collection '%s' not in info/collections", collection));
				}
			}
		}
		
		return wcols;
	}
	
	public WeaveBasicObject get(String collection, String id) throws WeaveException, NotFoundException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s/%s", collection, id)));			
		return this.get(location);
	}
	
	public WeaveBasicObject get(String path) throws WeaveException, NotFoundException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s", path)));
		return this.get(location);
	}

	public WeaveBasicObject get(URI location) throws WeaveException, NotFoundException {
		Log.getInstance().debug( "get()");
		Log.getInstance().debug("StorageURL: " + this.storageURL.toString());

		JSONObject jsonObject = getJSONPayload(location);

		//parse request content to extract JSONUtils encoded WeaveBasicObject
		try {
			String id         = (String)jsonObject.get("id");
			Double modified   = JSONUtils.toDouble(jsonObject.get("modified"));
			Long sortindex    = (Long)jsonObject.get("sortindex");
			String payload    = (String)jsonObject.get("payload");
			Long ttl          = (Long)jsonObject.get("ttl");
			
			return new WeaveBasicObject(id, modified, sortindex, ttl, payload);
			
		} catch (ClassCastException e) {
			throw new WeaveException(e);
		}
	}

	public JSONObject getJSONPayload(URI location) throws WeaveException, NotFoundException {
		return getJSONPayload(location, false);
	}

	@SuppressWarnings("unchecked")
	public JSONObject getJSONPayload(URI location, boolean isArray) throws WeaveException, NotFoundException {
		Log.getInstance().debug( "getJSONPayload()");

		JSONObject jsonObject = null;

		HttpGet get = new HttpGet(location);
		CloseableHttpResponse response = null;

		try {
			response = httpClient.execute(get);
			checkResponse(response);
			
			//parse request content to extract JSONUtils encoded WeaveBasicObject
			JSONParser parser = new JSONParser();  
			BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			if ( isArray ) {
				JSONArray jsonArray = (JSONArray)parser.parse(br);
				jsonObject = new JSONObject();
				jsonObject.put(null, jsonArray);
			} else {
				jsonObject = (JSONObject)parser.parse(br);
			}

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (NotFoundException e) {
			//NotFoundException extends HttpException so we need to catch and re-throw
			throw e;
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (ParseException e) {  
			throw new WeaveException(e);  
		} catch (GeneralSecurityException e) {
			throw new WeaveException(e);
		} finally {
			closeResponse(response);
		}
		
		return jsonObject;
	}

	private URI buildCollectionUri(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format, boolean full) throws WeaveException {

		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s", collection)));
		
		//Build list of URL querystring parameters
		List<NameValuePair> params = new LinkedList<NameValuePair>();
		
		if ( ids != null && ids.length > 0 ) {
			String value = "";
			String delim = "";
			for (int i = 0; i < ids.length; i++) {
				value = value + delim + ids[i];
				delim = ",";
			}
			params.add(new BasicNameValuePair("ids", value));
		}
		if (older != null) {
			params.add(new BasicNameValuePair("older", String.format("%.2f", older.doubleValue())));
		}
		if (newer != null) {
			params.add(new BasicNameValuePair("newer", String.format("%.2f", newer.doubleValue())));
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
			//FIXME - use URI builder for all uri handling
			
			//Use URIBuilder to encode query string parameters. java.util.URI DOES NOT correctly handle commas
			URIBuilder uri = new URIBuilder(location);
			if (params.size() > 0) {
				uri.setParameters(params);
			}
			location = new URI(uri.toString());
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}
		
		return location;
	}

	public String[] getCollectionIds(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		URI location = buildCollectionUri(collection, ids, older, newer, index_above, index_below, limit, offset, sort, null, false);
		return getCollectionIds(location);	
	}

	public String[] getCollectionIds(URI location) throws WeaveException, NotFoundException {
		Log.getInstance().debug( "getCollectionIds()");
		
		List<String> ids = new LinkedList<String>();
		
		//Get JSONUtils payload and extract JSONUtils array
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

	public WeaveBasicObject[] getCollection(String collection, String[] ids, Double older, Double newer, Integer index_above, Integer index_below, Integer limit, Integer offset, String sort, String format) throws WeaveException, NotFoundException {
		URI location = buildCollectionUri(collection, ids, older, newer, index_above, index_below, limit, offset, sort, format, true);
		return getCollection(location);	
	}
		
	public WeaveBasicObject[] getCollection(URI location) throws WeaveException, NotFoundException {
		Log.getInstance().debug( "getCollection()");
		
		List<WeaveBasicObject> listWbo = new LinkedList<WeaveBasicObject>();

		//Get JSONUtils payload and extract JSONUtils array
		JSONObject jsonTmp = getJSONPayload(location, true);
		JSONArray jsonArray = (JSONArray)jsonTmp.get(null);

		//Iterate through jsonArray and build WBOs
		try {
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> iterator = jsonArray.iterator();
			while ( iterator.hasNext() ) {
				JSONObject jsonObject = (JSONObject)iterator.next();
				
				String id         = (String)jsonObject.get("id");
				Double modified   = JSONUtils.toDouble(jsonObject.get("modified"));
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
			jobj.put("modified", String.format("%.2f", wbo.modified));
		}
		if ( wbo.sortindex != null ) {
			jobj.put("sortindex", wbo.sortindex);
		}
		if ( wbo.ttl != null ) {
			jobj.put("ttl", wbo.ttl);
		}
		
		return jobj.toJSONString();
	}

	private Double parseModifiedResponse(String response) throws WeaveException {
		
		//Assume that modified response is JSON encoded
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject)parser.parse(response);
			if ( !jsonObject.containsKey("modified") ) {
				throw new WeaveException("Invalid modified response");
			}			
			return JSONUtils.toDouble(jsonObject.get("modified"));
			
		} catch (ParseException | ClassCastException e) {
			
			//Okay that didn't work how about plain text
			return Double.parseDouble(response);
		}
	}
	
	public Double put(String collection, String id, WeaveBasicObject wbo) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s/%s", collection, id)));
		return put(location, wbo);
	}
	
	public Double put(String path, WeaveBasicObject wbo) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s", path)));
		return this.put(location, wbo);
	}

	public Double put(URI location, WeaveBasicObject wbo) throws WeaveException {
		Log.getInstance().debug("put()");

		Double modified = null;

		HttpPut put = new HttpPut(location);
		CloseableHttpResponse response = null;

		try {
			//Backwards compatible with android version of org.apache.http
			StringEntity entityPut = new StringEntity(encodeWeaveBasicObject(wbo));
			entityPut.setContentType("text/plain");
			entityPut.setContentEncoding("UTF-8");
			
			put.setEntity(entityPut);

			response = httpClient.execute(put);
			checkResponse(response);

			//parse request content to extract server modified time
			modified = parseModifiedResponse(EntityUtils.toString(response.getEntity()));			

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (GeneralSecurityException e) {
			throw new WeaveException(e);
		} finally {
			closeResponse(response);
		} 
		
		return modified;
	}

	public Double delete(String collection, String id) throws WeaveException {
		URI location = null;
		if (id == null) {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s", collection)));
		} else {
			location = this.storageURL.resolve(URIUtils.sanitize(String.format("storage/%s/%s", collection, id)));			
		}
		return this.delete(location);
	}
	
	public Double delete(URI location) throws WeaveException {
		Log.getInstance().debug( "get()");
		
		//parse request content to extract server modified time

		HttpDelete del                 = null;
		CloseableHttpResponse response = null;
		Double modified                = null;
		
		try {

			//@SuppressWarnings("unused")
			//HttpEntity entity = httpClient.delete(location);
			
			del = new HttpDelete(location);
			response = httpClient.execute(del);
			checkResponse(response);
			
			modified = parseModifiedResponse(EntityUtils.toString(response.getEntity()));

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (GeneralSecurityException e) {
			throw new WeaveException(e);
		} finally {
			closeResponse(response);
		}
		
		return modified;
	}

	public Double deleteCollection(String collection, String[] ids, Double older, Double newer, Integer limit, Integer offset, String sort) throws WeaveException, NotFoundException {
		URI location = buildCollectionUri(collection, ids, older, newer, null, null, limit, offset, sort, null, false);
		return delete(location);
	}

	private static void closeResponse(CloseableHttpResponse response) {
		if ( response == null ) {
			return;
		}
		
		try {
			response.close();
		} catch (Exception e) {
			//fail quietly
			Log.getInstance().error("Couldn't close HttpResponse - " + e.getMessage());
		}
	}
	
	private static void checkResponse(HttpResponse response) throws HttpException {
		checkResponse(response.getStatusLine());
	}
	
	private static void checkResponse(StatusLine statusLine) throws HttpException {
		int code = statusLine.getStatusCode();
		
		if (code/100 == 1 || code/100 == 2)		// everything OK
			return;
		
		String reason = code + " " + statusLine.getReasonPhrase();
		switch (code) {
		case HttpStatus.SC_NOT_FOUND:
			throw new NotFoundException(reason);
		case HttpStatus.SC_PRECONDITION_FAILED:
			throw new PreconditionFailedException(reason);
		default:
			throw new HttpException(code, reason);
		}
	}

	public void close() throws IOException {
		httpClient.close();
	}
}
