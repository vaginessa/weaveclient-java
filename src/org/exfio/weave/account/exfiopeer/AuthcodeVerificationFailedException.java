package org.exfio.weave.account.exfiopeer;

import org.exfio.weave.WeaveException;

public class AuthcodeVerificationFailedException extends WeaveException {
	private static final long serialVersionUID = -963267894071797004L;
	
	public AuthcodeVerificationFailedException(String message) {
		super(message);
	}

	public AuthcodeVerificationFailedException(Throwable throwable) {
		super(throwable);
	}

	public AuthcodeVerificationFailedException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
