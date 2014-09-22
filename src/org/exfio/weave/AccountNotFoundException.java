package org.exfio.weave;

public class AccountNotFoundException extends WeaveException {
	private static final long serialVersionUID = -2805378948072817491L;
	
	public AccountNotFoundException(String message) {
		super(message);
	}

	public AccountNotFoundException(Throwable throwable) {
		super(throwable);
	}

	public AccountNotFoundException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
