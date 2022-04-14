package com.example.asus.Core.announce;



import com.example.asus.Core.base.Peer;

import java.util.EventListener;
import java.util.List;


/**
 * EventListener interface for objects that want to receive tracker responses.
 *
 * @author mpetazzoni
 */
public interface AnnounceResponseListener extends EventListener {

	/**
	 * Handle an announce response event.
	 *
	 * @param interval The announce interval requested by the tracker.
	 * @param complete The number of seeders on this torrent.
	 * @param incomplete The number of leechers on this torrent.
	 */
	public void handleAnnounceResponse(int interval, int complete,
                                       int incomplete);

	/**
	 * Handle the discovery of new peers.
	 *
	 * @param peers The list of peers discovered (from the announce response or
	 * any other means like DHT/PEX, etc.).
	 */
	public void handleDiscoveredPeers(List<Peer> peers);
}
