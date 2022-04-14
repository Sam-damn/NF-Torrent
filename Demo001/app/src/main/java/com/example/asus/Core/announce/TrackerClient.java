package com.example.asus.Core.announce;



import android.util.Log;

import com.example.asus.Core.base.Peer;
import com.example.asus.Core.client.SharedTorrent;
import com.example.asus.Core.messages.TrackerMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public  abstract class TrackerClient implements Runnable{

    protected static final Logger logger =
            LoggerFactory.getLogger(TrackerClient.class);


    /** The set of listeners to announce request answers. */
	private final Set<AnnounceResponseListener> listeners;

	protected final SharedTorrent torrent;
	protected final Peer peer;
	protected final URI tracker;
    protected int interval;
    protected boolean stop;
    protected boolean forceStop;
    protected Thread thread;

	protected boolean completed = false;

    public TrackerClient(SharedTorrent torrent, Peer peer, URI tracker) {
		this.listeners = new HashSet<AnnounceResponseListener>();
		this.torrent = torrent;
		this.peer = peer;
		this.tracker = tracker;
        this.interval = 5;
		thread =null;
	}

	/**
	 * Register a new announce response listener.
	 *
	 * @param listener The listener to register on this announcer events.
	 */
	public void register(AnnounceResponseListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Returns the URI this tracker clients connects to.
	 */
	public URI getTrackerURI() {
		return this.tracker;
	}


	public abstract void announce(TrackerMessage.AnnounceRequestMessage.RequestEvent event,
		boolean inhibitEvent) throws AnnounceException;


	protected void close() {
		// Do nothing by default, but can be overloaded.
	}

	/**
	 * Formats an announce event into a usable string.
	 */
	protected String formatAnnounceEvent(
		TrackerMessage.AnnounceRequestMessage.RequestEvent event) {
		return TrackerMessage.AnnounceRequestMessage.RequestEvent.NONE.equals(event)
			? ""
			: String.format(" %s", event.name());
	}

	/**
	 * Handle the announce response from the tracker.
	 *
	 * <p>
	 * Analyzes the response from the tracker and acts on it. If the response
	 * is an error, it is logged. Otherwise, the announce response is used
	 * to fire the corresponding announce and peer events to all announce
	 * listeners.
	 * </p>
	 *
	 * @param message The incoming {@link TrackerMessage}.
	 * @param inhibitEvents Whether or not to prevent events from being fired.
	 */
	protected void handleTrackerAnnounceResponse(TrackerMessage message,
		boolean inhibitEvents) throws AnnounceException {
		if (message instanceof TrackerMessage.ErrorMessage) {
			TrackerMessage.ErrorMessage error = (TrackerMessage.ErrorMessage)message;
			throw new AnnounceException(error.getReason());
		}

		if (! (message instanceof TrackerMessage.AnnounceResponseMessage)) {
			throw new AnnounceException("Unexpected tracker message type " +
				message.getType().name() + "!");
		}

		if (inhibitEvents) {
			return;
		}

		TrackerMessage.AnnounceResponseMessage response =
			(TrackerMessage.AnnounceResponseMessage)message;
		this.fireAnnounceResponseEvent(
			response.getComplete(),
			response.getIncomplete(),
			response.getInterval());
		this.fireDiscoveredPeersEvent(
			response.getPeers());

	}


	protected void fireAnnounceResponseEvent(int complete, int incomplete,
		int interval) {



        Log.wtf("Announce_response" ,"announcing interval:"  + String.valueOf(interval) + "tracker:" + tracker.toString());
        setInterval(interval);
        for (AnnounceResponseListener listener : this.listeners) {
			listener.handleAnnounceResponse(interval, complete, incomplete);
		}
	}


	protected void fireDiscoveredPeersEvent(List<Peer> peers) {
        for(Peer peer : peers){
            Log.wtf("Announce_response" ,"Peer:" + peer.getIp() + "Tracker:" + tracker.toString());
            Log.i("info", "response: peer: "+ peer.getIp() + "Tracker: "+ tracker.toString());
        }

        for (AnnounceResponseListener listener : this.listeners) {
			listener.handleDiscoveredPeers(peers);
		}
	}

	public void start(){
        if ( (this.thread == null || !this.thread.isAlive())) {
            this.thread = new Thread(this);
            this.thread.setName("bt-announce(" +
                    this.peer.getShortHexPeerId() + ")" + tracker.toString());
            this.thread.start();
        }
    }
	@Override
	public void run() {
        Log.i("info","Starting announce loop...");

        // Set an initial announce interval to 5 seconds. This will be updated
        // in real-time by the tracker's responses to our announce requests.
        this.interval = 5;

        TrackerMessage.AnnounceRequestMessage.RequestEvent event =
                TrackerMessage.AnnounceRequestMessage.RequestEvent.STARTED;

        while (!this.stop) {
            try {
                if(completed){

                    this.announce(TrackerMessage
                            .AnnounceRequestMessage
                            .RequestEvent.COMPLETED, true);
                }
                else {
                    this.announce(event, false);
                    event = TrackerMessage.AnnounceRequestMessage.RequestEvent.NONE;
                }
            } catch (AnnounceException ae) {
                logger.warn(ae.getMessage());

            }

            try {
                Thread.sleep(this.interval * 1000);
            } catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
            }
        }

        Log.i("OMFGOMFG!!!!","tracker:" + tracker.toString());

        if (!this.forceStop) {
            // Send the final 'stopped' event to the tracker after a little
            // while.
            event = TrackerMessage.AnnounceRequestMessage.RequestEvent.STOPPED;
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                // Ignore
            }

            try {
                this.announce(event, true);
            } catch (AnnounceException ae) {
                logger.warn(ae.getMessage());
            }
        }
	}

    private void setInterval(int interval) {
        if (interval <= 0) {
            this.stop(true);

            return;
        }

        if (this.interval == interval) {
            return;
        }

        logger.info("Setting announce interval to {}s per tracker request.",
                interval);
        this.interval = interval;
    }

    public void stop() {

		if (this.thread != null && this.thread.isAlive()) {
			try {
				this.thread.join();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

        this.close();
        this.thread = null;
    }


    public void stop(boolean hard) {
        this.forceStop = hard;
        this.stop();
    }

    public void set_Complete(){
        this.completed = true;
    }
}
