package org.exfio.fxa;

import java.util.concurrent.CountDownLatch;

import org.mozilla.gecko.background.fxa.FxAccountClient10.RequestDelegate;
import org.mozilla.gecko.background.fxa.FxAccountClientException;
import org.mozilla.gecko.background.fxa.FxAccountClientException.FxAccountClientRemoteException;


public class BlockingRequestDelegate<T> implements RequestDelegate<T> {
	private CountDownLatch latch;
	private T result;
	private Exception error;
	
	public BlockingRequestDelegate() {
		this.latch  = new CountDownLatch(1);
		this.result = null;
		this.error  = null;
	}
	
	public T getResult() throws FxAccountClientException {
		if ( this.error != null ) {
			throw new FxAccountClientException(this.error);
		}
		return this.result;
	}
	
	public CountDownLatch getLatch() {
		return latch;
	}
	
	@Override
	public void handleSuccess(T result) {
		this.result = result;
		latch.countDown();
	}

	@Override
	public void handleError(Exception e) {
		this.error = e;
		latch.countDown();
	}

	@Override
	public void handleFailure(FxAccountClientRemoteException e) {
		this.error = e;
		latch.countDown();
	}
}
