package com.example.asus.Core.client;


import android.os.Handler;
import android.util.Log;

import com.example.asus.Core.announce.Announce;
import com.example.asus.Core.announce.AnnounceResponseListener;
import com.example.asus.Core.base.Peer;
import com.example.asus.Core.base.Torrent;
import com.example.asus.Core.connection.ConnectionHandler;
import com.example.asus.Core.connection.IncomingConnectionListener;
import com.example.asus.Core.messages.PeerMessage;
import com.example.asus.Core.peer.PeerActivityListener;
import com.example.asus.Core.peer.SharingPeer;
import com.example.ichigo.Gui.ClientActivityListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Client  implements Runnable,
        AnnounceResponseListener, IncomingConnectionListener, PeerActivityListener {

    private static final Logger logger =
            LoggerFactory.getLogger(Client.class);


    private static final int OPTIMISTIC_UNCHOKE_ITERATIONS = 3;

    private static final int RATE_COMPUTATION_ITERATIONS = 2;
    private static final int MAX_DOWNLOADERS_UNCHOKE = 4;
    private static final int UNCHOKING_FREQUENCY = 3;






    public enum ClientState {
        WAITING,
        VALIDATING,
        SHARING,
        SEEDING,
        STOPPED,
        ERROR
    };




    Updater updaterThread;

    private static final String BITTORRENT_ID_PREFIX = "-TO0042-";

    private SharedTorrent torrent;
    private ClientState state;
    private Peer self;

    int id ;


    private Thread thread;
    private boolean stop;
    private long seed;


    private ConnectionHandler service;
    private Announce announce;
    private ConcurrentMap<String, SharingPeer> peers;
    private ConcurrentMap<String, SharingPeer> connected;

    private Random random;


    private ClientActivityListener observer;

    public Client(InetAddress address, SharedTorrent torrent,int id , Handler mainHandler,Handler torrentHandler)
            throws UnknownHostException, IOException {


        this.torrent = torrent;
        this.state = ClientState.WAITING;
        this.id=id;

        String Id = Client.BITTORRENT_ID_PREFIX + UUID.randomUUID()
                .toString().split("-")[4];

        // Initialize the incoming connection handler and register ourselves to
        // it.
        this.service = new ConnectionHandler(this.torrent, Id, address);
        this.service.register(this);

        this.self = new Peer(
                this.service.getSocketAddress()
                        .getAddress().getHostAddress(),
                this.service.getSocketAddress().getPort(),
                ByteBuffer.wrap(Id.getBytes(Torrent.BYTE_ENCODING)));

        // Initialize the announce request thread, and register ourselves to it
        // as well.
        this.announce = new Announce(this.torrent, this.self);
        this.announce.register(this);


        this.updaterThread = new Updater(mainHandler,torrentHandler, torrent, this, this.id);


        this.peers = new ConcurrentHashMap<String, SharingPeer>();
        this.connected = new ConcurrentHashMap<String, SharingPeer>();
        this.random = new Random(System.currentTimeMillis());


    }
    public void setMaxDownloadRate(double rate) {
        this.torrent.setMaxDownloadRate(rate);
    }

    public void setMaxUploadRate(double rate) {
        this.torrent.setMaxUploadRate(rate);
    }

    public Peer getPeerSpec() {
        return this.self;
    }

    public SharedTorrent getTorrent() {
        return this.torrent;
    }

    public Set<SharingPeer> getPeers() {
        return new HashSet<SharingPeer>(this.peers.values());
    }

    public void registerObserver(ClientActivityListener observer){
        this.observer=observer;
    }

    private synchronized void setState(ClientState state) {
        if (this.state != state) {
            this.state = state;
            this.observer.handleStateChange(id,state);

        }

    }

    public ClientState getState() {
        return this.state;
    }

    /**
     * Download and share this client's torrent.
     *
     * @param seed Seed time in seconds after the download is complete. Pass
     * 0 to immediately stop after downloading.
     */

    public synchronized void share(int seed) {
        this.seed = seed;
        this.stop = false;

        if (this.thread == null || !this.thread.isAlive()) {
            this.thread = new Thread(this);
            this.thread.setName("bt-client(" +
                    this.self.getShortHexPeerId() + ")");
            this.thread.start();
        }
    }

    /**
     * Download the torrent without seeding after completion.
     */

    public void download() {
        this.share(0);
    }

    /**
     * Download and share this client's torrent until interrupted.
     */

    public void share() {
        this.share(-1);
    }



    /**
     * Wait for downloading (and seeding, if requested) to complete.
     */
    public void waitForCompletion() {
        if (this.thread != null && this.thread.isAlive()) {
            try {
                this.thread.join();
            } catch (InterruptedException ie) {
                logger.error(ie.getMessage(),ie);

            }
        }
    }

    /**
     * Immediately but gracefully stop this client.
     *
     *  wait Whether to wait for the client execution thread to complete
     * or not. This allows for the client's state to be settled down in one of
     * the DONE or ERROR states when this method returns.
     */
    public void stop(boolean wait) {
        this.stop = true;

        if (this.thread != null && this.thread.isAlive()) {
            this.thread.interrupt();
            if (wait) {
                this.waitForCompletion();
            }
        }

        this.thread = null;
    }


    public void stop() {
        this.stop(true);
    }


    public boolean isSeed() {
        return this.torrent.isComplete();
    }





    @Override
    public void run() {
        try {
            this.setState(ClientState.VALIDATING);
            this.torrent.init();
        } catch (IOException ioe) {

            Log.i("info","client: Error while initializing torrent data");

        } catch (InterruptedException ie) {
            Log.wtf("info","client: Client was interrupted during initialization");

        } catch (IllegalStateException ile){

        }

        finally {
            if (!this.torrent.isInitialized()) {
                try {
                    this.service.close();
                } catch (IOException ioe) {
                    Log.i("info","client: Client was interrupted during initialization");
                }

                this.setState(ClientState.ERROR);
                this.torrent.close();
                return;
            }
        }

        // Initial completion test
        if (this.torrent.isComplete()) {

            this.seed();
        } else {
            this.setState(ClientState.SHARING);
        }

        // Detect early stop
        if (this.stop) {
            Log.i("info","Client: Download is complete and no seeding was requested.");

            this.finish();
            return;
        }

        this.announce.start();
        this.service.start();
        this.updaterThread.start();


        int optimisticIterations = 0;
        int rateComputationIterations = 0;

        while (!this.stop) {
            optimisticIterations =
                    (optimisticIterations == 0 ?
                            Client.OPTIMISTIC_UNCHOKE_ITERATIONS :
                            optimisticIterations - 1);

            rateComputationIterations =
                    (rateComputationIterations == 0 ?
                            Client.RATE_COMPUTATION_ITERATIONS :
                            rateComputationIterations - 1);

            try {
                this.unchokePeers(optimisticIterations == 0);
                if (rateComputationIterations == 0) {
                    this.resetPeerRates();
                }
            } catch (Exception e) {
                logger.error("An exception occurred during the BitTorrent " +
                        "client main loop execution!", e);
            }

            try {
                Thread.sleep(Client.UNCHOKING_FREQUENCY*1000);
            } catch (InterruptedException ie) {
                logger.trace("BitTorrent main loop interrupted.");
            }


        }




        this.service.stop();
        try {
            this.service.close();
        } catch (IOException ioe) {
            Log.i("info"," CLient: Error while releasing bound channel");

        }

        for (SharingPeer peer : this.connected.values()) {
            peer.unbind(true);
        }

        this.updaterThread.stop();


        this.announce.stop();


        this.finish();

    }

    private void finish() {
        this.torrent.close();

        this.setState(ClientState.STOPPED);

    }
    private Comparator<SharingPeer> getPeerRateComparator() {
        if (ClientState.SHARING.equals(this.state)) {
            return new SharingPeer.DLRateComparator();
        } else if (ClientState.SEEDING.equals(this.state)) {
            return new SharingPeer.ULRateComparator();
        } else {
            throw new IllegalStateException("Client is neither sharing nor " +
                    "seeding, we shouldn't be comparing peers at this point.");
        }
    }

    private synchronized void resetPeerRates() {
        for (SharingPeer peer : this.connected.values()) {
            peer.getDLRate().reset();
            peer.getULRate().reset();
        }
    }

    private synchronized void unchokePeers(boolean optimistic) {
        // Build a set of all connected peers, we don't care about peers we're
        // not connected to.
        TreeSet<SharingPeer> bound = new TreeSet<SharingPeer>(
                this.getPeerRateComparator());
        bound.addAll(this.connected.values());

        if (bound.size() == 0) {
            logger.trace("No connected peers, skipping unchoking.");
            return;
        } else {
            logger.trace("Running unchokePeers() on {} connected peers.",
                    bound.size());
        }

        int downloaders = 0;
        Set<SharingPeer> choked = new HashSet<SharingPeer>();

        // We're interested in the top downloaders first, so use a descending
        // set.
        for (SharingPeer peer : bound.descendingSet()) {
            if (downloaders < Client.MAX_DOWNLOADERS_UNCHOKE) {
                // Unchoke up to MAX_DOWNLOADERS_UNCHOKE interested peers
                if (peer.isChoking()) {
                    if (peer.isInterested()) {
                        downloaders++;
                    }

                    peer.unchoke();
                }
            } else {
                // Choke everybody else
                choked.add(peer);
            }
        }

        // Actually choke all chosen peers (if any), except the eventual
        // optimistic unchoke.
        if (choked.size() > 0) {
            SharingPeer randomPeer = choked.toArray(
                    new SharingPeer[0])[this.random.nextInt(choked.size())];

            for (SharingPeer peer : choked) {
                if (optimistic && peer == randomPeer) {
                    logger.debug("Optimistic unchoke of {}.", peer);
                    peer.unchoke();
                    continue;
                }

                peer.choke();
            }
        }
    }


    private SharingPeer getOrCreatePeer(Peer search) {
        SharingPeer peer;

        synchronized (this.peers) {
            logger.trace("Searching for {}...", search);
            if (search.hasPeerId()) {
                peer = this.peers.get(search.getHexPeerId());
                if (peer != null) {
                    logger.trace("Found peer (by peer ID): {}.", peer);
                    this.peers.put(peer.getHostIdentifier(), peer);
                    this.peers.put(search.getHostIdentifier(), peer);
                    return peer;
                }
            }

            peer = this.peers.get(search.getHostIdentifier());
            if (peer != null) {
                if (search.hasPeerId()) {
                    logger.trace("Recording peer ID {} for {}.",
                            search.getHexPeerId(), peer);
                    peer.setPeerId(search.getPeerId());
                    this.peers.put(search.getHexPeerId(), peer);
                }

                logger.debug("Found peer (by host ID): {}.", peer);
                return peer;
            }

            peer = new SharingPeer(search.getIp(), search.getPort(),
                    search.getPeerId(), this.torrent);
            logger.trace("Created new peer: {}.", peer);

            this.peers.put(peer.getHostIdentifier(), peer);
            if (peer.hasPeerId()) {
                this.peers.put(peer.getHexPeerId(), peer);
            }

            return peer;
        }
    }

    @Override
    public void handleNewPeerConnection(SocketChannel channel, byte[] peerId) {


        Peer search = new Peer(
                channel.socket().getInetAddress().getHostAddress(),
                channel.socket().getPort(),
                (peerId != null
                        ? ByteBuffer.wrap(peerId)
                        : (ByteBuffer)null));

        SharingPeer peer = this.getOrCreatePeer(search);

        try {
            synchronized (peer) {
                if (peer.isConnected()) {
                    channel.close();
                    return;
                }

                peer.register(this);
                peer.bind(channel);
            }

            this.connected.put(peer.getHexPeerId(), peer);
            peer.register(this.torrent);

        } catch (Exception e) {
            this.connected.remove(peer.getHexPeerId());
            logger.warn("Could not handle new peer connection " +
                    "with {}: {}", peer, e.getMessage());
        }
    }

    @Override
    public void handleFailedConnection(SharingPeer peer, Throwable cause) {
        logger.warn("Could not connect to {}: {}.", peer, cause.getMessage());
        this.peers.remove(peer.getHostIdentifier());
        if (peer.hasPeerId()) {
            this.peers.remove(peer.getHexPeerId());
        }
    }

    @Override
    public void handleAnnounceResponse(int interval, int complete, int incomplete) {



    }

    @Override
    public void handleDiscoveredPeers(List<Peer> peers) {





        if (peers == null || peers.isEmpty()) {
            // No peers returned by the tracker. Apparently we're alone on
            // this one for now.
            logger.info("theres no peers!!!!!!!!!!!!!!!!!");
            return;
        }

        logger.info("Got {} peer(s) in tracker response.", peers.size());

        if (!this.service.isAlive()) {
            logger.warn("Connection handler service is not available.");
            return;
        }

        for (Peer peer : peers) {
            // Attempt to connect to the peer if and only if:
            //   - We're not already connected or connecting to it;
            //   - We're not a seeder (we leave the responsibility
            //	   of connecting to peers that need to download
            //     something).
            SharingPeer match
                    = this.getOrCreatePeer(peer);
            if (this.isSeed()) {
                continue;
            }

            synchronized (match) {
                if (!match.isConnected()) {
                    this.service.connect(match);
                }
            }
        }

    }

    @Override
    public void handlePeerChoked(SharingPeer peer) {

    }

    @Override
    public void handlePeerReady(SharingPeer peer) {

    }

    @Override
    public void handlePieceAvailability(SharingPeer peer, Piece piece) {


    }

    @Override
    public void handleBitfieldAvailability(SharingPeer peer, BitSet availablePieces) {



    }

    @Override
    public void handlePieceSent(SharingPeer peer, Piece piece) {


    }

    @Override
    public void handlePieceCompleted(SharingPeer peer, Piece piece)
            throws IOException {
        synchronized (this.torrent) {
            if (piece.isValid()) {
                // Make sure the piece is marked as completed in the torrent
                // Note: this is required because the order the
                // PeerActivityListeners are called is not defined, and we
                // might be called before the torrent's piece completion
                // handler is.
                this.torrent.markCompleted(piece);
                logger.info("Completed download of {} from {}. " +
                                "We now have {}/{} pieces" );

                this.observer.handlePieceCompletion(id,(int)this.torrent.getCompletion());

                // Send a HAVE message to all connected peers
                PeerMessage have = PeerMessage.HaveMessage.craft(piece.getIndex());
                for (SharingPeer remote : this.connected.values()) {
                    remote.send(have);
                }

                // Force notify after each piece is completed to propagate download
                // completion information (or new seeding state)
                //code here

            } else {
                logger.warn("Downloaded piece#{} from {} was not valid ;-(",
                        piece.getIndex(), peer);
            }

            if (this.torrent.isComplete()) {
                logger.info("Last piece validated and completed, finishing download...!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!/n");

                // Cancel all remaining outstanding requests
                for (SharingPeer remote : this.connected.values()) {
                    if (remote.isDownloading()) {
                        int requests = remote.cancelPendingRequests().size();
                        logger.info("Cancelled {} remaining pending requests on {}.",
                                requests, remote);
                    }
                }

                this.torrent.finish();
                this.announce.set_Completed();

                logger.info("Download is complete and finalized.");
                this.seed();
            }
        }
    }

    @Override
    public void handlePeerDisconnected(SharingPeer peer) {
        if (this.connected.remove(peer.hasPeerId()
                ? peer.getHexPeerId()
                : peer.getHostIdentifier()) != null) {



        }
        peer.reset();
    }

    @Override
    public void handleIOException(SharingPeer peer, IOException ioe) {
        logger.info("I/O error while exchanging data with {}, " +
                "closing connection with it!", peer, ioe.getMessage());
        peer.unbind(true);
    }

    @Override
    public void handleDownloadRateChange(long bytes) {

       updaterThread.updatedownRate(bytes);
    }

    @Override
    public void handleUploadRateChange(long bytes) {

        updaterThread.updateUpRate(bytes);
    }


    private synchronized void seed() {
        // Silently ignore if we're already seeding.
        if (ClientState.SEEDING.equals(this.getState())) {
            return;
        }

        logger.info("Download of {} pieces completed.",
                this.torrent.getPieceCount());

        this.setState(ClientState.SEEDING);
        if (this.seed < 0) {
            logger.info("Seeding indefinetely...");
            return;
        }

        // In case seeding for 0 seconds we still need to schedule the task in
        // order to call stop() from different thread to avoid deadlock
        logger.info("Seeding for {} seconds...", this.seed);
        Timer timer = new Timer();
        timer.schedule(new ClientShutdown(this, timer), this.seed*1000);
    }


    public static class ClientShutdown extends TimerTask {

        private final Client client;
        private final Timer timer;

        public ClientShutdown(Client client, Timer timer) {
            this.client = client;
            this.timer = timer;
        }

        @Override
        public void run() {
            this.client.stop();
            if (this.timer != null) {
                this.timer.cancel();
            }
        }
    };


}
