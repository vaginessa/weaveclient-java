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

import java.security.GeneralSecurityException;
import java.io.IOException;

import lombok.Getter;
import lombok.Setter;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest; 
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.exfio.weave.Constants;
import org.exfio.weave.client.PreconditionFailedException;
import org.exfio.weave.net.HttpException;
import org.exfio.weave.net.HttpRequestRetryHandler;
import org.exfio.weave.storage.NotFoundException;
import org.exfio.weave.util.Log;

public class HttpClient {

	public final static String DEFAULT_USER_AGENT = "eXfio Weave/" + Constants.APP_VERSION;
	
	private CloseableHttpClient httpClient = null;
	
	@Getter private String userAgent = null;
	@Setter private HttpClientContext context = null;
	@Setter private AuthHeaderProvider authHeaderProvider = null;

	//Initialise httpClient
	public HttpClient() {
		this(DEFAULT_USER_AGENT);
	}
	
	public HttpClient(String userAgent) {

		this.userAgent = userAgent;
		this.context = HttpClientContext.create();
		
		httpClient = HttpClients.custom()
			.setRetryHandler(HttpRequestRetryHandler.INSTANCE)
			.setUserAgent(userAgent)
			.build();
	}

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
	
	public CloseableHttpResponse execute(HttpUriRequest request) throws IOException, GeneralSecurityException {
		if ( authHeaderProvider != null ) {
			request.addHeader(authHeaderProvider.getAuthHeader(request, null, null));
		}		
		return httpClient.execute(request, context);
	}
	
	public void close() throws IOException {
		if ( httpClient != null ) {
			httpClient.close();
		}
	}

}
