package org.exfio.weave.client;

import java.util.Locale;

import org.apache.commons.lang.ArrayUtils;

import ch.boye.httpclientandroidlib.HttpRequest;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpRequestRetryHandler;

public class WeaveHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {
	
	private final static WeaveHttpRequestRetryHandler INSTANCE = new WeaveHttpRequestRetryHandler();
	
	public static WeaveHttpRequestRetryHandler getInstance() {
		return INSTANCE;
	}
	
	// see http://www.iana.org/assignments/http-methods/http-methods.xhtml
	private final static String idempotentMethods[] = {
		"DELETE", "GET", "HEAD", "MKCALENDAR", "MKCOL", "OPTIONS", "PROPFIND", "PROPPATCH",
		"PUT", "REPORT", "SEARCH", "TRACE"
	};

    public WeaveHttpRequestRetryHandler() {
        super(/* retry count */ 3, /* retry already sent requests? */ false);
    }

    @Override
    protected boolean handleAsIdempotent(final HttpRequest request) {
        final String method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        return ArrayUtils.contains(idempotentMethods, method);
    }
}
