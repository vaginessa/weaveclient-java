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

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import android.util.Log;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.StatusLine;
import ch.boye.httpclientandroidlib.auth.AuthScope;
import ch.boye.httpclientandroidlib.auth.UsernamePasswordCredentials;
import ch.boye.httpclientandroidlib.client.AuthCache;
import ch.boye.httpclientandroidlib.client.config.RequestConfig;
import ch.boye.httpclientandroidlib.client.methods.CloseableHttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.protocol.HttpClientContext;
import ch.boye.httpclientandroidlib.config.Registry;
import ch.boye.httpclientandroidlib.config.RegistryBuilder;
import ch.boye.httpclientandroidlib.conn.socket.ConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.socket.PlainConnectionSocketFactory;
import ch.boye.httpclientandroidlib.impl.auth.BasicScheme;
import ch.boye.httpclientandroidlib.impl.client.BasicAuthCache;
import ch.boye.httpclientandroidlib.impl.client.BasicCredentialsProvider;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.impl.conn.ManagedHttpClientConnectionFactory;
import ch.boye.httpclientandroidlib.impl.conn.PoolingHttpClientConnectionManager;
import ch.boye.httpclientandroidlib.util.EntityUtils;

import org.json.simple.JSONArray; 
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;  
import org.json.simple.parser.ParseException;	

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.URIUtils;
import at.bitfire.davdroid.webdav.HttpException;
import at.bitfire.davdroid.webdav.NotFoundException;
import at.bitfire.davdroid.webdav.PreconditionFailedException;
import at.bitfire.davdroid.webdav.TlsSniSocketFactory;

import org.exfio.weave.WeaveException;
import org.exfio.weave.resource.WeaveBasicObject;

public class WeaveHttpClient {
	private static final String TAG = "exfio.WeaveHttpClient";
	
	private static CloseableHttpClient httpClient;
	private final ReentrantReadWriteLock httpClientLock = new ReentrantReadWriteLock();

	private HttpClientContext context;
	
	private URI baseURL;	
	private String user;
	private String password;
	
	private URI storageURL;
	
	//Instansiate httpClient
	static {
        
		RequestConfig defaultRqConfig;
		Registry<ConnectionSocketFactory> socketFactoryRegistry;

		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.getInstance())
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(20*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
		
		// enable logging
		ManagedHttpClientConnectionFactory.INSTANCE.wirelog.enableDebug(true);
		ManagedHttpClientConnectionFactory.INSTANCE.log.enableDebug(true);

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(2);				// max.  2 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host
		
		httpClient = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				.setRetryHandler(WeaveHttpRequestRetryHandler.getInstance())
				.setUserAgent("DAVdroid/" + Constants.APP_VERSION)
				.disableCookieManagement()
				.build();
	}

	public WeaveHttpClient(String baseURL, String user, String password) throws WeaveException {
		try {
			this.baseURL  = new URI(baseURL);
		} catch (URISyntaxException e) {
			throw new WeaveException(e);
		}

		//TODO - get storageURL
		
		this.user     = user;
		this.password = password;
		
		//Set HTTP Auth credentials
		this.context = HttpClientContext.create();
		context.setCredentialsProvider(new BasicCredentialsProvider());				
		context.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
		
		//initialise pre-emptive authentication
		HttpHost host = new HttpHost(storageURL.getHost(), storageURL.getPort(), storageURL.getScheme());
		AuthCache authCache = context.getAuthCache();
		if (authCache == null)
			authCache = new BasicAuthCache();
		authCache.put(host, new BasicScheme());
		context.setAuthCache(authCache);
	}
	
	public void lock() {
		httpClientLock.readLock().lock();
	}
	
	public void unlock() {
		httpClientLock.readLock().unlock();
	}
	
	public void close() throws IOException {
		httpClientLock.writeLock().lock();
		httpClient.close();
		httpClient = null;
		httpClientLock.writeLock().unlock();
	}
	
	private static void closeResponse(CloseableHttpResponse response) {
		try {
			response.close();
		} catch (Exception e) {
			//fail quietly
			Log.e(TAG, e.getMessage());
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
	
	public WeaveBasicObject get(String collection, String id) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s/%s", this.user, collection, id)));
		return this.get(location);
	}
	
	protected WeaveBasicObject get(String path) throws WeaveException {
		URI location = this.storageURL.resolve(URIUtils.sanitize(String.format("/1.1/%s/storage/%s", this.user, path)));
		return this.get(location);
	}

	protected WeaveBasicObject get(URI location) throws WeaveException {

		HttpGet get = new HttpGet(location);
		CloseableHttpResponse response = null;
		JSONObject jsonObject = null;

		//parse request content to extract JSON encoded WeaveBasicObject
		try {
			response = httpClient.execute(get, context);
			checkResponse(response);
			
			HttpEntity entity = response.getEntity();
			if (entity == null)
				throw new WeaveException("Response was invalid");
			
			//String content = EntityUtils.toString(entity);

			JSONParser parser = new JSONParser();  
			BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));	
			jsonObject = (JSONObject)parser.parse(br);  

		} catch (IOException e) {
			throw new WeaveException(e);
		} catch (HttpException e) {
			throw new WeaveException(e);
		} catch (ParseException e) {  
			throw new WeaveException(e);  
		} finally {
			if ( response != null )
				closeResponse(response);
		}

		String id         = (String)jsonObject.get("id");
		Float modified    = Float.valueOf((String)jsonObject.get("modified"));
		Integer sortindex = Integer.valueOf((String)jsonObject.get("sortindex"));
		String payload    = (String)jsonObject.get("payload");
		Integer ttl       = Integer.valueOf((String)jsonObject.get("ttl"));
		
		return new WeaveBasicObject(id, modified, sortindex, ttl, payload);
	}

}
