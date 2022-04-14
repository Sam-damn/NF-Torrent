package com.example.asus.Core.announce;


/**
 * Exception thrown when an announce request failed.
 *
 * @author mpetazzoni
 */
public class AnnounceException extends Exception {

	private static final long serialVersionUID = -1;

	public AnnounceException(String message) {
		super(message);
	}

	public AnnounceException(Throwable cause) {
		super(cause);
	}

	public AnnounceException(String message, Throwable cause) {
		super(message, cause);
	}
}
