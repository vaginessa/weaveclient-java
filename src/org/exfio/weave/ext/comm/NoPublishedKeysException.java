package org.exfio.weave.ext.comm;

import org.exfio.weave.WeaveException;

public class NoPublishedKeysException extends WeaveException {
	private static final long serialVersionUID = -7341938940041294840L;
	
	public NoPublishedKeysException(String message) {
		super(message);
	}

	public NoPublishedKeysException(Throwable throwable) {
		super(throwable);
	}

	public NoPublishedKeysException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
