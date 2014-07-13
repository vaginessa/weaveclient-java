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
package org.exfio.weave.net;

import java.net.URI;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.IOException;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpUriRequest; 
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
//import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import org.exfio.weave.Constants;
import org.exfio.weave.client.NotFoundException;
import org.exfio.weave.client.PreconditionFailedException;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpRequestRetryHandler;
import org.exfio.weave.util.Log;



public class HttpClient {

	private static HttpClient INSTANCE = null;
	
	private static HttpClientContext context = null;
	private static ConnectionKeepAliveStrategy keepAlive;
	private static ConnectionSocketFactory sslSocketFactory = null;
	private static CloseableHttpClient httpClient = null;
	private static ReentrantReadWriteLock httpClientLock = null;

	private static String userAgent = "eXfio Weave/" + Constants.VERSION;

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

		/*
		//Default to org.apache.http.ssl.SSLConnectionSocketFactory
		if ( sslSocketFactory == null ) {
			sslSocketFactory = SSLConnectionSocketFactory.getSocketFactory();
		}
		
		RequestConfig defaultRqConfig;
		Registry<ConnectionSocketFactory> socketFactoryRegistry;

		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", sslSocketFactory)
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(20*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
		
		// enable logging
		//ManagedHttpClientConnectionFactory.INSTANCE.wirelog.enableDebug(true);
		//ManagedHttpClientConnectionFactory.INSTANCE.log.enableDebug(true);

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(2);				// max.  2 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host
		
		httpClient = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				//.setRetryHandler(WeaveHttpRequestRetryHandler.getInstance())
				.setUserAgent(userAgent)
				.disableCookieManagement()
				.build();
		 */

		httpClient = HttpClients.custom()
				.setRetryHandler(HttpRequestRetryHandler.INSTANCE)
				.setUserAgent(userAgent)
				.build();
		
		//httpClient = HttpClients.createDefault();

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
