package org.exfio.weave.ext.comm;

import org.exfio.weave.WeaveException;

public class StorageNotFoundException extends WeaveException {
	private static final long serialVersionUID = 1503538948076294871L;
	
	public StorageNotFoundException(String message) {
		super(message);
	}

	public StorageNotFoundException(Throwable throwable) {
		super(throwable);
	}

	public StorageNotFoundException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
