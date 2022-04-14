package com.example.asus.Core.messages.http;



import com.example.asus.Core.bcodec.BDecoder;
import com.example.asus.Core.bcodec.BEValue;
import com.example.asus.Core.messages.TrackerMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;


/**
 * Base class for HTTP tracker messages.
 *
 * @author mpetazzoni
 */
public abstract class HTTPTrackerMessage extends TrackerMessage {

	protected HTTPTrackerMessage(Type type, ByteBuffer data) {
		super(type, data);
	}

	public static HTTPTrackerMessage parse(ByteBuffer data)
		throws IOException, MessageValidationException {
		BEValue decoded = BDecoder.bdecode(data);
		if (decoded == null) {
			throw new MessageValidationException(
				"Could not decode tracker message (not B-encoded?)!");
		}

		Map<String, BEValue> params = decoded.getMap();

		if (params.containsKey("info_hash")) {
			return HTTPAnnounceRequestMessage.parse(data);
		} else if (params.containsKey("peers")) {
			return HTTPAnnounceResponseMessage.parse(data);
		} else if (params.containsKey("failure reason")) {
			return HTTPTrackerErrorMessage.parse(data);
		}

		throw new MessageValidationException("Unknown HTTP tracker message!");
	}
}
