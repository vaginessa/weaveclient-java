/*******************************************************************************
 * Copyright (c) 2014 Gerry Healy <nickel_chrome@mac.com>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Based on DavDroid:
 *     Richard Hirner (bitfire web engineering)
 * 
 * Contributors:
 *     Gerry Healy <nickel_chrome@mac.com> - Initial implementation
 ******************************************************************************/
package org.exfio.weave.net;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest; 
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.exfio.weave.Constants;
import org.exfio.weave.client.PreconditionFailedException;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpRequestRetryHandler;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.util.Log;



public class HttpClient {

	private static HttpClient INSTANCE = null;
	
	private static HttpClientContext context = null;
	@SuppressWarnings("unused")
	private static ConnectionKeepAliveStrategy keepAlive;
	@SuppressWarnings("unused")
	private static ConnectionSocketFactory sslSocketFactory = null;
	private static CloseableHttpClient httpClient = null;
	private static ReentrantReadWriteLock httpClientLock = null;

	private static String userAgent = "eXfio Weave/" + Constants.APP_VERSION;

	//---------------------------------
	// Static (initialisation) methods
	//---------------------------------
	
	//Initialise httpClient
	public static void init() throws IOException {

		if ( httpClient != null ) {
			httpClientLock.writeLock().lock();
			httpClient.close();
			httpClient = null;
			httpClientLock.writeLock().unlock();
		}

		httpClient = HttpClients.custom()
				.setRetryHandler(HttpRequestRetryHandler.INSTANCE)
				.setUserAgent(userAgent)
				.build();
		
		httpClientLock = new ReentrantReadWriteLock();

		context = HttpClientContext.create();
		
		INSTANCE = new HttpClient();		
	}

	public static HttpClient getInstance() throws IOException {
		if ( INSTANCE == null || httpClient == null) {
			init();
		}
		return INSTANCE;
	}

	public static void setSSLSocketFactory(ConnectionSocketFactory factory) {
		sslSocketFactory = factory;
	}

	public static void setUserAgent(String userAgent) {
		HttpClient.userAgent = userAgent;
	}

	
	//---------------------------------
	// Static (helper) methods
	//---------------------------------

	public static void closeResponse(CloseableHttpResponse response) {
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
	
	public static void checkResponse(HttpResponse response) throws HttpException {
		checkResponse(response.getStatusLine());
	}
	
	public static void checkResponse(StatusLine statusLine) throws HttpException {
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
	
	//---------------------------------
	// Instance (post-initialisation) methods
	//---------------------------------

	public void setContext(HttpClientContext clientContext) {
		context = clientContext;
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

	public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
		return httpClient.execute(request, context);
	}

}
