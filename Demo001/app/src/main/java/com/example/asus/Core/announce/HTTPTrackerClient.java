package com.example.asus.Core.announce;



import com.example.asus.Core.base.Peer;
import com.example.asus.Core.client.SharedTorrent;
import com.example.asus.Core.messages.TrackerMessage;
import com.example.asus.Core.messages.http.HTTPAnnounceRequestMessage;
import com.example.asus.Core.messages.http.HTTPTrackerMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HTTPTrackerClient extends TrackerClient {

	protected static final Logger logger =
		LoggerFactory.getLogger(HTTPTrackerClient.class);

	/**
	 * Create a new HTTP announcer for the given torrent.
	 *
	 * @param torrent The torrent we're announcing about.
	 * @param peer Our own peer specification.
	 */
	protected HTTPTrackerClient(SharedTorrent torrent, Peer peer,
								URI tracker) {
		super(torrent, peer, tracker);
	}


	@Override
	public void announce(TrackerMessage.AnnounceRequestMessage.RequestEvent event,
		boolean inhibitEvents) throws AnnounceException {
		logger.info("Announcing{} to tracker with {}U/{}D/{}L bytes...",
			new Object[] {
				this.formatAnnounceEvent(event),
				this.torrent.getUploaded(),
				this.torrent.getDownloaded(),
				this.torrent.getLeft()
			});

		URL target = null;
		try {
			HTTPAnnounceRequestMessage request =
				this.buildAnnounceRequest(event);
			target = request.buildAnnounceURL(this.tracker.toURL());
		} catch (MalformedURLException mue) {
			throw new AnnounceException("Invalid announce URL (" +
				mue.getMessage() + ")", mue);
		} catch (TrackerMessage.MessageValidationException mve) {
			throw new AnnounceException("Announce request creation violated " +
				"expected protocol (" + mve.getMessage() + ")", mve);
		} catch (IOException ioe) {
			throw new AnnounceException("Error building announce request (" +
				ioe.getMessage() + ")", ioe);
		}

		HttpURLConnection conn = null;
		InputStream in = null;
		try {
			conn = (HttpURLConnection)target.openConnection();
			in = conn.getInputStream();
		} catch (IOException ioe) {
			if (conn != null) {
				in = conn.getErrorStream();
			}
		}

		// At this point if the input stream is null it means we have neither a
		// response body nor an error stream from the server. No point in going
		// any further.
		if (in == null) {
			throw new AnnounceException("No response or unreachable tracker!");
		}

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(in);

			// Parse and handle the response
			HTTPTrackerMessage message =
				HTTPTrackerMessage.parse(ByteBuffer.wrap(baos.toByteArray()));
			this.handleTrackerAnnounceResponse(message, inhibitEvents);
		} catch (IOException ioe) {
			throw new AnnounceException("Error reading tracker response!", ioe);
		} catch (TrackerMessage.MessageValidationException mve) {
			throw new AnnounceException("Tracker message violates expected " +
				"protocol (" + mve.getMessage() + ")", mve);
		} finally {
			// Make sure we close everything down at the end to avoid resource
			// leaks.
			try {
				in.close();
			} catch (IOException ioe) {
				logger.warn("Problem ensuring error stream closed!", ioe);
			}

			// This means trying to close the error stream as well.
			InputStream err = conn.getErrorStream();
			if (err != null) {
				try {
					err.close();
				} catch (IOException ioe) {
					logger.warn("Problem ensuring error stream closed!", ioe);
				}
			}
		}
	}

	@Override
	protected void close() {
		this.stop = true;

	}

	private HTTPAnnounceRequestMessage buildAnnounceRequest(
		TrackerMessage.AnnounceRequestMessage.RequestEvent event)
		throws UnsupportedEncodingException, IOException,
			TrackerMessage.MessageValidationException {
		// Build announce request message
		return HTTPAnnounceRequestMessage.craft(
				this.torrent.getInfoHash(),
				this.peer.getPeerId().array(),
				this.peer.getPort(),
				this.torrent.getUploaded(),
				this.torrent.getDownloaded(),
				this.torrent.getLeft(),
				true, false, event,
				this.peer.getIp(),
				TrackerMessage.AnnounceRequestMessage.DEFAULT_NUM_WANT);
	}
}
