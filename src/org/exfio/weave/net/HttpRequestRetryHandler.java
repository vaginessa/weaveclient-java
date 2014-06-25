package org.exfio.weave.net;

import java.util.Locale;

import org.apache.commons.lang.ArrayUtils;

import org.apache.http.HttpRequest;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

public class HttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {
	final static HttpRequestRetryHandler INSTANCE = new HttpRequestRetryHandler();
	
	// see http://www.iana.org/assignments/http-methods/http-methods.xhtml
	private final static String idempotentMethods[] = {
		"DELETE", "GET", "HEAD", "MKCALENDAR", "MKCOL", "OPTIONS", "PROPFIND", "PROPPATCH",
		"PUT", "REPORT", "SEARCH", "TRACE"
	};

    public HttpRequestRetryHandler() {
        super(/* retry count */ 3, /* retry already sent requests? */ false);
    }

    @Override
    protected boolean handleAsIdempotent(final HttpRequest request) {
        final String method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        return ArrayUtils.contains(idempotentMethods, method);
    }
}
