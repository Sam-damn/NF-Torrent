package com.example.asus.Core.peer;



import com.example.asus.Core.base.Peer;
import com.example.asus.Core.client.Piece;
import com.example.asus.Core.client.SharedTorrent;
import com.example.asus.Core.messages.PeerMessage;

import java.io.IOException;
import java.io.Serializable;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A peer exchanging on a torrent with the BitTorrent client.


 * A SharingPeer extends the base Peer class with all the data and logic needed
 * by the BitTorrent client to interact with a peer exchanging on the same
 * torrent.

 * Peers are defined by their peer ID, IP address and port number, just like
 * base peers. Peers we exchange with also contain four crucial attributes:

 *   choking :  which means we are choking this peer and we're
 *   not willing to send him anything for now.

 *   interesting : which means we are interested in a piece
 *   this peer has.

 *   choked :  if this peer is choking and won't send us
 *   anything right now.

 *   interested : if this peer is interested in something we
 *   have.


 * Peers start choked and uninterested.

 */


public class SharingPeer extends Peer implements MessageListener{


   private static final int MAX_PIPELINED_REQUESTS = 5;



   private boolean choking;
   private boolean interesting;

   private boolean choked;
   private boolean interested;


   private SharedTorrent torrent;
   private BitSet availablePieces;


   private Piece requestedPiece;
   private int lastRequestedOffset;

   private BlockingQueue<PeerMessage.RequestMessage> requests;
   private volatile boolean downloading;


   private PeerExchange exchange;

   private Rate download;
   private Rate upload;


   private Set<PeerActivityListener> listeners;

   private Object requestsLock, exchangeLock;


   public SharingPeer(String ip, int port, ByteBuffer peerId,
                      SharedTorrent torrent) {
      super(ip, port, peerId);

      this.torrent = torrent;
      this.listeners = new HashSet<PeerActivityListener>();
      this.availablePieces = new BitSet(this.torrent.getPieceCount());

      this.requestsLock = new Object();
      this.exchangeLock = new Object();

      this.reset();
      this.requestedPiece = null;
   }


   public void register(PeerActivityListener listener) {
      this.listeners.add(listener);
   }

   public Rate getDLRate() {
      return this.download;
   }

   public Rate getULRate() {
      return this.upload;
   }



   /**
    * Reset the peer state.

    * Initially, peers are considered choked, choking, and neither interested
    * nor interesting.
    */

   public synchronized void reset() {
      this.choking = true;
      this.interesting = false;
      this.choked = true;
      this.interested = false;

      this.exchange = null;

      this.requests = null;
      this.lastRequestedOffset = 0;
      this.downloading = false;
   }

   /**
    * Choke this peer.
    *
    * <p>
    * We don't want to upload to this peer anymore, so mark that we're choking
    * from this peer.
    * </p>
    */
   public void choke() {

      if (!this.choking) {
         this.send(PeerMessage.ChokeMessage.craft());
         this.choking = true;
      }
   }

   /**
    * Unchoke this peer.
    *
    * <p>
    * Mark that we are no longer choking from this peer and can resume
    * uploading to it.
    * </p>
    */
   public void unchoke() {

      if (this.choking) {
         this.send(PeerMessage.UnchokeMessage.craft());
         this.choking = false;
      }
   }
   public boolean isChoking() {
      return this.choking;
   }


   public void interesting() {
      if (!this.interesting) {
         this.send(PeerMessage.InterestedMessage.craft());
         this.interesting = true;
      }
   }

   public void notInteresting() {
      if (this.interesting) {
         this.send(PeerMessage.NotInterestedMessage.craft());
         this.interesting = false;
      }
   }

   public boolean isInteresting() {
      return this.interesting;
   }


   public boolean isChoked() {
      return this.choked;
   }

   public boolean isInterested() {
      return this.interested;
   }


   public BitSet getAvailablePieces() {
      synchronized (this.availablePieces) {
         return (BitSet)this.availablePieces.clone();
      }
   }

   public Piece getRequestedPiece() {
      return this.requestedPiece;
   }

   /**
    * Tells whether this peer is a seed.

    * Returns true  if the peer has all of the torrent's pieces
    * available.
    */
   public synchronized boolean isSeed() {
      return this.torrent.getPieceCount() > 0 &&
         this.getAvailablePieces().cardinality() ==
            this.torrent.getPieceCount();
   }
   /**
    * Bind a connected socket to this peer.
    *
    * <p>
    * This will create a new peer exchange with this peer using the given
    * socket, and register the peer as a message listener.
    * </p>
    *
    * @param channel The connected socket channel for this peer.
    */
   public synchronized void bind(SocketChannel channel) throws SocketException {
      this.unbind(true);

      this.exchange = new PeerExchange(this, this.torrent, channel);
      this.exchange.register(this);
      this.exchange.start();

      this.download = new Rate();
      this.download.reset();

      this.upload = new Rate();
      this.upload.reset();

   }

   /**
    * Tells whether this peer as an active connection through a peer exchange.
    */
   public boolean isConnected() {
      synchronized (this.exchangeLock) {
         return this.exchange != null && this.exchange.isConnected();
      }
   }

   /**
    * Unbind and disconnect this peer.


    * This terminates the eventually present and/or connected peer exchange
    * with the peer and fires the peer disconnected event to any peer activity
    * listeners registered on this peer.

    * force Force unbind without sending cancel requests.
    */

   public void unbind(boolean force) {
      if (!force) {
         // Cancel all outgoing requests, and send a NOT_INTERESTED message to
         // the peer.
         this.cancelPendingRequests();
         this.send(PeerMessage.NotInterestedMessage.craft());
      }

      synchronized (this.exchangeLock) {
         if (this.exchange != null) {
            this.exchange.stop();
            this.exchange = null;
         }
      }

      this.firePeerDisconnected();
      this.requestedPiece = null;
   }





   public void send(PeerMessage message) throws IllegalStateException{
      if (this.isConnected()) {
         this.exchange.send(message);
      }


   }

   /**
    * Download the given piece from this peer.
    *

    * Starts a block request queue and pre-fill it with MAX_PIPELINED_REQUESTS
    * block requests.

    * Further requests will be added, one by one, every time a block is
    * returned.

    * piece The piece chosen to be downloaded from this peer.
    */
   public synchronized void downloadPiece(Piece piece)
      throws IllegalStateException {
      if (this.isDownloading()) {
         IllegalStateException up = new IllegalStateException(
            "Trying to download a piece while previous " +
               "download not completed!");

         throw up; // ah ah.
      }

      this.requests = new LinkedBlockingQueue<PeerMessage.RequestMessage>(
         SharingPeer.MAX_PIPELINED_REQUESTS);
      this.requestedPiece = piece;
      this.lastRequestedOffset = 0;
      this.requestNextBlocks();
   }

   public boolean isDownloading() {
      return this.downloading;
   }

   /**
    * Request some more blocks from this peer.
    *
    * <p>
    * Re-fill the pipeline to get download the next blocks from the peer.
    * </p>
    */
   private void requestNextBlocks() {
      synchronized (this.requestsLock) {
         if (this.requests == null || this.requestedPiece == null) {
            // If we've been taken out of a piece download context it means our
            // outgoing requests have been cancelled. Don't enqueue new
            // requests until a proper piece download context is
            // re-established.
            return;
         }

         while (this.requests.remainingCapacity() > 0 &&
            this.lastRequestedOffset < this.requestedPiece.size()) {
            PeerMessage.RequestMessage request = PeerMessage.RequestMessage
               .craft(
                  this.requestedPiece.getIndex(),
                  this.lastRequestedOffset,
                  Math.min(
                     (int)(this.requestedPiece.size() -
                        this.lastRequestedOffset),
                     PeerMessage.RequestMessage.DEFAULT_REQUEST_SIZE));
            this.requests.add(request);
            this.send(request);
            this.lastRequestedOffset += request.getLength();
         }

         this.downloading = this.requests.size() > 0;
      }
   }

   /**
    * Remove the REQUEST message from the request pipeline matching this
    * PIECE message.

    * Upon reception of a piece block with a PIECE message, remove the
    * corresponding request from the pipeline to make room for the next block
    * requests.

    * message The PIECE message received.
    */
   private void removeBlockRequest(PeerMessage.PieceMessage message) {
      synchronized (this.requestsLock) {
         if (this.requests == null) {
            return;
         }

         for (PeerMessage.RequestMessage request : this.requests) {
            if (request.getPiece() == message.getPiece() &&
               request.getOffset() == message.getOffset()) {
               this.requests.remove(request);
               break;
            }
         }

         this.downloading = this.requests.size() > 0;
      }
   }

   /**
    * Cancel all pending requests.
    *
    * This queues CANCEL messages for all the requests in the queue, and
    * returns the list of requests that were in the queue.


    * If no request queue existed, or if it was empty, an empty set of request
    * messages is returned.

    */


   public Set<PeerMessage.RequestMessage> cancelPendingRequests(){
      synchronized (this.requestsLock) {
         Set<PeerMessage.RequestMessage> requests =
            new HashSet<PeerMessage.RequestMessage>();

         if (this.requests != null) {
            for (PeerMessage.RequestMessage request : this.requests) {
               this.send(PeerMessage.CancelMessage.craft(request.getPiece(),
                  request.getOffset(), request.getLength()));
               requests.add(request);
            }
         }

         this.requests = null;
         this.downloading = false;
         return requests;
      }

   }

   /**
    * Handle an incoming message from this peer.

    *  msg The incoming, parsed message.
    */
   @Override
   public synchronized void handleMessage(PeerMessage msg) {

      switch (msg.getType()){

         case KEEP_ALIVE:
            // do nothing were keeping the connection open anyway
            break;

         case CHOKE:
            this.choked = true;
            this.firePeerChoked();
            this.cancelPendingRequests();
            break;

         case UNCHOKE:
            this.choked = false;
            this.firePeerReady();
            break;


         case INTERESTED:
            this.interested = true;
            break;

         case NOT_INTERESTED:
            this.interested = false;
            break;

         case HAVE:
            // Record this peer has the given piece
            PeerMessage.HaveMessage have = (PeerMessage.HaveMessage)msg;
            Piece havePiece = this.torrent.getPiece(have.getPieceIndex());

            synchronized (this.availablePieces) {
               this.availablePieces.set(havePiece.getIndex());

            }

            this.firePieceAvailabity(havePiece);
            break;

         case BITFIELD:
            // Augment the hasPiece bit field from this BITFIELD message
            PeerMessage.BitfieldMessage bitfield =
               (PeerMessage.BitfieldMessage)msg;

            synchronized (this.availablePieces) {
               this.availablePieces.or(bitfield.getBitfield());

            }

            this.fireBitfieldAvailabity();
            break;

         case REQUEST:
            PeerMessage.RequestMessage request =
               (PeerMessage.RequestMessage)msg;
            Piece rp = this.torrent.getPiece(request.getPiece());

            // If we are choking from this peer and it still sends us
            // requests, it is a violation of the BitTorrent protocol.
            // Similarly, if the peer requests a piece we don't have, it
            // is a violation of the BitTorrent protocol. In these
            // situation, terminate the connection.
            if (this.isChoking() || !rp.isValid()) {

               this.unbind(true);
               break;
            }

            if (request.getLength() >
               PeerMessage.RequestMessage.MAX_REQUEST_SIZE) {
               this.unbind(true);
               break;
            }

            // At this point we agree to send the requested piece block to
            // the remote peer, so let's queue a message with that block
            try {
               ByteBuffer block = rp.read(request.getOffset(),
                  request.getLength());
               this.send(PeerMessage.PieceMessage.craft(request.getPiece(),
                  request.getOffset(), block));
               this.upload.add(block.capacity());

               if (request.getOffset() + request.getLength() == rp.size()) {
                  this.firePieceSent(rp);
               }
            } catch (IOException ioe) {
               this.fireIOException(new IOException("Error while sending piece block request!", ioe));
            }

            break;

         case PIECE:
            // Record the incoming piece block.

            // Should we keep track of the requested pieces and act when we
            // get a piece we didn't ask for, or should we just stay
            // greedy?
            PeerMessage.PieceMessage piece = (PeerMessage.PieceMessage)msg;
            Piece p = this.torrent.getPiece(piece.getPiece());

            // Remove the corresponding request from the request queue to
            // make room for next block requests.
            this.removeBlockRequest(piece);
            this.download.add(piece.getBlock().capacity());

            try {
               synchronized (p) {
                  if (p.isValid()) {
                     this.requestedPiece = null;
                     this.cancelPendingRequests();
                     this.firePeerReady();
                     break;
                  }

                   p.record(piece.getBlock(), piece.getOffset());

                   fireDownRateChange(piece.getBlock().capacity());


                  // If the block offset equals the piece size and the block
                  // length is 0, it means the piece has been entirely
                  // downloaded. In this case, we have nothing to save, but
                  // we should validate the piece.
                  if (piece.getOffset() + piece.getBlock().capacity()
                     == p.size()) {
                     p.validate();
                     this.firePieceCompleted(p);
                     this.requestedPiece = null;
                     this.downloading=false;
                     this.firePeerReady();
                  } else {
                     this.requestNextBlocks();
                  }
               }
            } catch (IOException ioe) {
               this.fireIOException(new IOException("Error while storing received piece block!", ioe));
               break;
            }
            break;


         case CANCEL:
            // No need to support
            break;


      }

   }

    @Override
    public void handleuploadRate(long bytes) {
        fireUpRateChange(bytes);
    }

    private void fireUpRateChange(long bytes){
       for (PeerActivityListener listener : this.listeners) {
           listener.handleUploadRateChange(bytes);
       }
   }

    private void fireDownRateChange(long bytes){
        for (PeerActivityListener listener : this.listeners) {
            listener.handleDownloadRateChange(bytes);
        }
    }


   private void firePeerChoked() {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePeerChoked(this);
      }
   }

   private void firePeerReady() {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePeerReady(this);
      }
   }


   private void firePieceAvailabity(Piece piece) {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePieceAvailability(this, piece);
      }
   }

   private void fireBitfieldAvailabity() {
      for (PeerActivityListener listener : this.listeners) {
         listener.handleBitfieldAvailability(this,
            this.getAvailablePieces());
      }
   }

   private void firePieceSent(Piece piece) {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePieceSent(this, piece);
      }
   }

   private void firePieceCompleted(Piece piece) throws IOException {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePieceCompleted(this, piece);
      }
   }

   private void firePeerDisconnected() {
      for (PeerActivityListener listener : this.listeners) {
         listener.handlePeerDisconnected(this);
      }
   }

   private void fireIOException(IOException ioe) {
      for (PeerActivityListener listener : this.listeners) {
         listener.handleIOException(this, ioe);
      }
   }


   /**
    * Download rate comparator.

    * Compares sharing peers based on their current download rate.
    */
   public static class DLRateComparator
      implements Comparator<SharingPeer>, Serializable {

      private static final long serialVersionUID = 96307229964730L;

      @Override
      public int compare(SharingPeer a, SharingPeer b) {
         return Rate.RATE_COMPARATOR.compare(a.getDLRate(), b.getDLRate());
      }
   }


   /**
    * Upload rate comparator.

    * Compares sharing peers based on their current upload rate.
    */
   public static class ULRateComparator
      implements Comparator<SharingPeer>, Serializable {

      private static final long serialVersionUID = 38794949747717L;

      @Override
      public int compare(SharingPeer a, SharingPeer b) {
         return Rate.RATE_COMPARATOR.compare(a.getULRate(), b.getULRate());
      }
   }
   public String toString() {
      return new StringBuilder(super.toString())
         .append(" [")
         .append((this.choked ? "C" : "c"))
         .append((this.interested ? "I" : "i"))
         .append("|")
         .append((this.choking ? "C" : "c"))
         .append((this.interesting ? "I" : "i"))
         .append("|")
         .append(this.availablePieces.cardinality())
         .append("]")
         .toString();
   }


}
