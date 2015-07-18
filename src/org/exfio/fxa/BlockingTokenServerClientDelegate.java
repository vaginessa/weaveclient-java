package org.exfio.fxa;

import java.util.concurrent.CountDownLatch;

import org.mozilla.gecko.tokenserver.TokenServerClientDelegate;
import org.mozilla.gecko.tokenserver.TokenServerException;
import org.mozilla.gecko.tokenserver.TokenServerToken;


public class BlockingTokenServerClientDelegate implements TokenServerClientDelegate {
	private CountDownLatch latch;
	private TokenServerToken token;
	private Exception error;

	public BlockingTokenServerClientDelegate() {
		this.latch  = new CountDownLatch(1);
		this.token  = null;
		this.error  = null;
	}

	public TokenServerToken getToken() throws Exception {
		if ( this.error != null ) {
			//throw new Exception(this.error);
			throw this.error;
		}
		return this.token;
	}

	public CountDownLatch getLatch() {
		return latch;
	}

	@Override
	public void handleSuccess(TokenServerToken token) {
		this.token = token;
		latch.countDown();
	}

	@Override
	public void handleError(Exception e) {
		this.error = e;
		latch.countDown();
	}

	@Override
	public void handleFailure(TokenServerException e) {
		this.error = e;
		latch.countDown();
	}

	/**
	 * Might be called multiple times, in addition to the other terminating handler methods.
	 */
	@Override
	public void handleBackoff(int backoffSeconds) {
		throw new AssertionError("Not yet implemented");
	}

	@Override
	public String getUserAgent() {
		return org.exfio.weave.net.HttpClient.userAgent;
	}
}
