package org.exfio.fxa;

import java.util.concurrent.CountDownLatch;

import org.mozilla.gecko.background.fxa.FxAccountClient10.RequestDelegate;
import org.mozilla.gecko.background.fxa.FxAccountClientException.FxAccountClientRemoteException;


public class BlockingDecoratorRequestDelegate<T> implements RequestDelegate<T> {
	private CountDownLatch latch;
	private RequestDelegate<T> delegate;

	public BlockingDecoratorRequestDelegate(RequestDelegate<T> delegate) {
		this.delegate = delegate;
		this.latch    = new CountDownLatch(1);
	}
	
	public RequestDelegate<T> getRequestDelegate() {
		return this.delegate;
	}
	
	public CountDownLatch getLatch() {
		return latch;
	}
	
	@Override
	public void handleError(Exception e) {
		delegate.handleError(e);
		latch.countDown();
	}

	@Override
	public void handleFailure(FxAccountClientRemoteException e) {
		delegate.handleError(e);
		latch.countDown();
	}

	@Override
	public void handleSuccess(T result) {
		delegate.handleSuccess(result);
		latch.countDown();
	}
}
