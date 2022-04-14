package com.example.asus.Core.messages.http;



import com.example.asus.Core.base.Torrent;
import com.example.asus.Core.bcodec.BDecoder;
import com.example.asus.Core.bcodec.BEValue;
import com.example.asus.Core.bcodec.BEncoder;
import com.example.asus.Core.bcodec.InvalidBEncodingException;
import com.example.asus.Core.messages.TrackerMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


/**
 * An error message from an HTTP tracker.
 *
 * @author mpetazzoni
 */
public class HTTPTrackerErrorMessage extends HTTPTrackerMessage
	implements TrackerMessage.ErrorMessage {

	private final String reason;

	private HTTPTrackerErrorMessage(ByteBuffer data, String reason) {
		super(Type.ERROR, data);
		this.reason = reason;
	}

	@Override
	public String getReason() {
		return this.reason;
	}

	public static HTTPTrackerErrorMessage parse(ByteBuffer data)
		throws IOException, MessageValidationException {
		BEValue decoded = BDecoder.bdecode(data);
		if (decoded == null) {
			throw new MessageValidationException(
				"Could not decode tracker message (not B-encoded?)!");
		}

		Map<String, BEValue> params = decoded.getMap();

		try {
			return new HTTPTrackerErrorMessage(
				data,
				params.get("failure reason")
					.getString(Torrent.BYTE_ENCODING));
		} catch (InvalidBEncodingException ibee) {
			throw new MessageValidationException("Invalid tracker error " +
				"message!", ibee);
		}
	}

	public static HTTPTrackerErrorMessage craft(
		ErrorMessage.FailureReason reason) throws IOException,
		   MessageValidationException {
		return HTTPTrackerErrorMessage.craft(reason.getMessage());
	}

	public static HTTPTrackerErrorMessage craft(String reason)
		throws IOException, MessageValidationException {
		Map<String, BEValue> params = new HashMap<String, BEValue>();
		params.put("failure reason",
			new BEValue(reason, Torrent.BYTE_ENCODING));
		return new HTTPTrackerErrorMessage(
			BEncoder.bencode(params),
			reason);
	}
}
