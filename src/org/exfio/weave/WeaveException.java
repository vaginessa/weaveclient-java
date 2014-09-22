package org.exfio.weave;

public class WeaveException extends Exception {
	private static final long serialVersionUID = -4805778945072857401L;
	
	public WeaveException(String message) {
		super(message);
	}

	public WeaveException(Throwable throwable) {
		super(throwable);
	}

	public WeaveException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
