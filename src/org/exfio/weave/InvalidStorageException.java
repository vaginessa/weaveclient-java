package org.exfio.weave;

public class InvalidStorageException extends WeaveException {
	private static final long serialVersionUID = -5024378974572842838L;
	
	public InvalidStorageException(String message) {
		super(message);
	}

	public InvalidStorageException(Throwable throwable) {
		super(throwable);
	}

	public InvalidStorageException(String message, Throwable throwable) {
		super(message, throwable);
	}
}
