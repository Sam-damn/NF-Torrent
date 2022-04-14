package com.example.asus.Core.announce;



import com.example.asus.Core.base.Peer;
import com.example.asus.Core.client.SharedTorrent;

import java.net.URI;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Announce  {

	protected static final Logger logger =
		LoggerFactory.getLogger(Announce.class);

	private final Peer peer;

	private final List<List<TrackerClient>> clients;
	private final Set<TrackerClient> allClients;



	public Announce(SharedTorrent torrent, Peer peer) {
		this.peer = peer;
		this.clients = new ArrayList<List<TrackerClient>>();
		this.allClients = new HashSet<TrackerClient>();

		/**
		 * Build the tiered structure of tracker clients mapping to the
		 * trackers of the torrent.
		 */
		for (List<URI> tier : torrent.getAnnounceList()) {
			ArrayList<TrackerClient> tierClients = new ArrayList<TrackerClient>();
			for (URI tracker : tier) {
				try {
					TrackerClient client = this.createTrackerClient(torrent,
						peer, tracker);

					tierClients.add(client);
					this.allClients.add(client);
				} catch (Exception e) {
					logger.warn("Will not announce on {}: {}!",
						tracker,
						e.getMessage() != null
							? e.getMessage()
							: e.getClass().getSimpleName());
				}
			}

			// Shuffle the list of tracker clients once on creation.
			Collections.shuffle(tierClients);

			// Tier is guaranteed to be non-empty by
			// Torrent#parseAnnounceInformation(), so we can add it safely.
			clients.add(tierClients);
		}


	}


	public void register(AnnounceResponseListener listener) {
		for (TrackerClient client : this.allClients) {
			client.register(listener);
		}
	}

	public void start() {

		for(TrackerClient client: allClients)
		{
			client.start();
		}

	}

    public void stop(){

		for (TrackerClient client: allClients){
			client.close();
		}


        for(TrackerClient client: allClients)
        {
            client.stop(true);
        }

    }

	private TrackerClient createTrackerClient(SharedTorrent torrent, Peer peer,
		URI tracker) throws UnknownHostException, UnknownServiceException {
		String scheme = tracker.getScheme();

		if ("http".equals(scheme) || "https".equals(scheme)) {
			return new HTTPTrackerClient(torrent, peer, tracker);
		} else if ("udp".equals(scheme)) {
			return new UDPTrackerClient(torrent, peer, tracker);
		}

		throw new UnknownServiceException(
			"Unsupported announce scheme: " + scheme + "!");
	}

	public void set_Completed(){
        for(TrackerClient client : this.allClients){


            client.set_Complete();
        }

    }



}
