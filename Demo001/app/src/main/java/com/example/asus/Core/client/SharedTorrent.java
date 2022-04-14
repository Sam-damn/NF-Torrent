package com.example.asus.Core.client;


import android.util.Log;

import com.example.asus.Core.base.Torrent;
import com.example.asus.Core.bcodec.InvalidBEncodingException;
import com.example.asus.Core.peer.PeerActivityListener;
import com.example.asus.Core.peer.SharingPeer;
import com.example.asus.Core.storage.FileStorage;
import com.example.asus.Core.storage.FileStorageCollection;
import com.example.asus.Core.storage.TorrentByteStorage;
import com.example.asus.Core.strategy.RequestStrategy;
import com.example.asus.Core.strategy.RequestStrategyImplRarest;

import org.apache.commons.io.FileUtils;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SharedTorrent extends Torrent implements PeerActivityListener {

   /** End-game trigger ratio.

    * Eng-game behavior (requesting already requested pieces from available
    * and ready peers to try to speed-up the end of the transfer) will only be
    * enabled when the ratio of completed pieces over total pieces in the
    * torrent is over this value.

    */
   private static final float ENG_GAME_COMPLETION_RATIO = 0.95f;


   private static final RequestStrategy DEFAULT_REQUEST_STRATEGY = new RequestStrategyImplRarest();


   private boolean stop;

   private long uploaded;
   private long downloaded;
   private long left;

   private final TorrentByteStorage bucket;

   private final int pieceLength;
   private final ByteBuffer piecesHashes;



   private boolean initialized;
   private Piece[] pieces;
   private SortedSet<Piece> rarest;
   private BitSet completedPieces;
   private BitSet requestedPieces;
   private RequestStrategy requestStrategy;


   private double maxUploadRate = 0.0;
   private double maxDownloadRate = 0.0;


   //multiple constructers


   public SharedTorrent(Torrent torrent, File destDir)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      this(torrent, destDir, false);
   }



   public SharedTorrent(Torrent torrent, File destDir, boolean seeder)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      this(torrent.getEncoded(), destDir, seeder, DEFAULT_REQUEST_STRATEGY);
   }


   public SharedTorrent(Torrent torrent, File destDir, boolean seeder,
                        RequestStrategy requestStrategy)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      this(torrent.getEncoded(), destDir, seeder, requestStrategy);
   }


   public SharedTorrent(byte[] torrent, File destDir)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      this(torrent, destDir, false);
   }

   public SharedTorrent(byte[] torrent, File parent, boolean seeder)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      this(torrent, parent, seeder, DEFAULT_REQUEST_STRATEGY);
   }


   public SharedTorrent(byte[] torrent, File parent, boolean seeder,
                        RequestStrategy requestStrategy)
      throws FileNotFoundException, IOException, NoSuchAlgorithmException {
      super(torrent, seeder);

      if (parent == null || !parent.isDirectory()) {
         throw new IllegalArgumentException("Invalid parent directory!");
      }

      String parentPath = parent.getCanonicalPath();

      try {
         this.pieceLength = this.decoded_info.get("piece length").getInt();
         this.piecesHashes = ByteBuffer.wrap(this.decoded_info.get("pieces")
            .getBytes());

         if (this.piecesHashes.capacity() / Torrent.PIECE_HASH_SIZE *
            (long)this.pieceLength < this.getSize()) {
            throw new IllegalArgumentException("Torrent size does not " +
               "match the number of pieces and the piece size!");
         }
      } catch (InvalidBEncodingException ibee) {
         throw new IllegalArgumentException(
            "Error reading torrent meta-info fields!");
      }

      List<FileStorage> files = new LinkedList<FileStorage>();
      long offset = 0L;
      for (Torrent.TorrentFile file : this.files) {
         File actual = new File(parent, file.file.getPath());

         if (!actual.getCanonicalPath().startsWith(parentPath)) {
            throw new SecurityException("Torrent file path attempted " +
               "to break directory jail!");
         }


         actual.getParentFile().mkdirs();
         files.add(new FileStorage(actual, offset, file.size));
         offset += file.size;
      }
      this.bucket = new FileStorageCollection(files, this.getSize());

      this.stop = false;

      this.uploaded = 0;
      this.downloaded = 0;
      this.left = this.getSize();

      this.initialized = false;
      this.pieces = new Piece[0];
      this.rarest = Collections.synchronizedSortedSet(new TreeSet<Piece>());
      this.completedPieces = new BitSet();
      this.requestedPieces = new BitSet();

      //TODO: should switch to guice
      this.requestStrategy = requestStrategy;
   }

   public static SharedTorrent fromFile(File source, File parent)
      throws IOException, NoSuchAlgorithmException {
      byte[] data = FileUtils.readFileToByteArray(source);
      return new SharedTorrent(data, parent);
   }


   public double getMaxUploadRate() {
      return this.maxUploadRate;
   }

   public void setMaxUploadRate(double rate) {
      this.maxUploadRate = rate;
   }


   public double getMaxDownloadRate() {
      return this.maxDownloadRate;
   }

   public void setMaxDownloadRate(double rate) {
      this.maxDownloadRate = rate;
   }


   public long getUploaded() {
      return this.uploaded;
   }


   public long getDownloaded() {
      return this.downloaded;
   }


   public long getLeft() {
      return this.left;
   }



   public boolean isInitialized() {
      return this.initialized;
   }


   //stop torrent initialization asap
   public void stop() {
      this.stop = true;
   }


   /**
    * Build this torrent's pieces array.

    * Hash and verify any potentially present local data and create this
    * torrent's pieces array from their respective hash provided in the
    * torrent meta-info.

    * This function should be called soon after the constructor to initialize
    * the pieces array.

    */


   public synchronized void init() throws InterruptedException, IOException {

      if (this.isInitialized()) {
         throw new IllegalStateException("Torrent was already initialized!");
      }

      int threads = getHashingThreadsCount();

      int nPieces = (int) (Math.ceil(
         (double)this.getSize() / this.pieceLength));


      this.pieces = new Piece[nPieces];
      this.completedPieces = new BitSet(nPieces);
      this.piecesHashes.clear();


      ExecutorService executor = Executors.newFixedThreadPool(threads);
      List<Future<Piece>> results = new LinkedList<Future<Piece>>();


      try {


         for (int idx=0; idx<nPieces; idx++){

            byte[] hash = new byte[Torrent.PIECE_HASH_SIZE];
            this.piecesHashes.get(hash);

            // The last piece may be shorter than the torrent's global piece
            // length. Let's make sure we get the right piece length in any
            // situation.


            long off = ((long)idx) * this.pieceLength;
            long len = Math.min(
               this.bucket.size() - off,
               this.pieceLength);




            this.pieces[idx] = new Piece(this.bucket, idx, off, len, hash,
               this.isSeeder());

            Callable<Piece> hasher = new Piece.CallableHasher(this.pieces[idx]);
            results.add(executor.submit(hasher));


            if (results.size() >= threads) {
               this.validatePieces(results);
            }

         }

         this.validatePieces(results);

      } finally {
         // Request orderly executor shutdown and wait for hashing tasks to
         // complete.
         executor.shutdown();

         while (!executor.isTerminated()) {

            if (this.stop) {
               throw new InterruptedException("Torrent data analysis " +
                  "interrupted.");
            }

            Thread.sleep(10);
         }
      }

      this.initialized = true;
   }



   /**
    * Process the pieces enqueued for hash validation so far.
    *
    * @param results The list of {@link Future}s of pieces to process.
    */

   private void validatePieces(List<Future<Piece>> results)
      throws IOException {
      try {
         for (Future<Piece> task : results) {
            Piece piece = task.get();
            if (this.pieces[piece.getIndex()].isValid()) {
               this.completedPieces.set(piece.getIndex());
               this.left -= piece.size();
               Log.i("info" , " I HAVE PIECE  !!!!!!!!!!!!!!!!!!!!!!!!!!!" + piece.toString());
            }
         }

         results.clear();
      } catch (Exception e) {
         throw new IOException("Error while hashing a torrent piece!", e);
      }
   }


   public synchronized void close() {
      try {
         this.bucket.close();
      } catch (IOException ioe) {
         //print error message here
      }
   }


   public Piece getPiece(int index) {
      if (this.pieces == null) {
         throw new IllegalStateException("Torrent not initialized yet.");
      }

      if (index >= this.pieces.length) {
         throw new IllegalArgumentException("Invalid piece index!");
      }

      return this.pieces[index];
   }



   public int getPieceCount() {
      if (this.pieces == null) {
         throw new IllegalStateException("Torrent not initialized yet.");
      }

      return this.pieces.length;
   }

   public BitSet getAvailablePieces() {
      if (!this.isInitialized()) {
         throw new IllegalStateException("Torrent not yet initialized!");
      }

      BitSet availablePieces = new BitSet(this.pieces.length);

      synchronized (this.pieces) {
         for (Piece piece : this.pieces) {
            if (piece.available()) {
               availablePieces.set(piece.getIndex());
            }
         }
      }

      return availablePieces;
   }

   public BitSet getCompletedPieces() {
      if (!this.isInitialized()) {
         throw new IllegalStateException("Torrent not yet initialized!");
      }

      synchronized (this.completedPieces) {
         return (BitSet)this.completedPieces.clone();
      }
   }


   public BitSet getRequestedPieces() {
      if (!this.isInitialized()) {
         throw new IllegalStateException("Torrent not yet initialized!");
      }

      synchronized (this.requestedPieces) {
         return (BitSet)this.requestedPieces.clone();
      }
   }


   //Tells whether this torrent has been fully downloaded, or is fully available locally.

   public synchronized boolean isComplete() {
      return this.pieces.length > 0 &&
         this.completedPieces.cardinality() == this.pieces.length;
   }




   /**
    * Finalize the download of this torrent.
    *
    * This realizes the final, pre-seeding phase actions on this torrent,
    * which usually consists in putting the torrent data in their final form
    * and at their target location.
    */


   public synchronized void finish() throws IOException {
      if (!this.isInitialized()) {
         throw new IllegalStateException("Torrent not yet initialized!");
      }

      if (!this.isComplete()) {
         throw new IllegalStateException("Torrent download is not complete!");
      }

      this.bucket.finish();
   }

   public synchronized boolean isFinished() {
      return this.isComplete() && this.bucket.isFinished();
   }



   public float getCompletion() {
      return this.isInitialized()
         ? (float)this.completedPieces.cardinality() /
         (float)this.pieces.length * 100.0f
         : 0.0f;
   }





   public synchronized void markCompleted(Piece piece) {
      if (this.completedPieces.get(piece.getIndex())) {
         return;
      }

      // A completed piece means that's that much data left to download for
      // this torrent.
      this.left -= piece.size();
      this.completedPieces.set(piece.getIndex());
   }

   // PeerActivityListener handler :

   /**
    * Peer choked handler.

    * When a peer chokes, the requests made to it are canceled and we need to
    * mark the eventually piece we requested from it as available again for
    * download tentative from another peer.

    * peer The peer that choked.
    */

   @Override
   public synchronized void handlePeerChoked(SharingPeer peer) {
      Piece piece = peer.getRequestedPiece();

      if(piece!=null){
         this.requestedPieces.set(piece.getIndex(),false);
      }


   }
   /**
    * Peer ready handler.

    * When a peer becomes ready to accept piece block requests, select a piece
    * to download and go for it.

    * peer The peer that became ready.
    */

   @Override
   public synchronized void handlePeerReady(SharingPeer peer) {
      BitSet interesting = peer.getAvailablePieces();
      interesting.andNot(this.completedPieces);
      interesting.andNot(this.requestedPieces);


      // If we didn't find interesting pieces, we need to check if we're in
      // an end-game situation. If yes, we request an already requested piece
      // to try to speed up the end.
      if (interesting.cardinality() == 0) {
         interesting = peer.getAvailablePieces();
         interesting.andNot(this.completedPieces);
         if (interesting.cardinality() == 0) {
            return;
         }

         if (this.completedPieces.cardinality() <
            ENG_GAME_COMPLETION_RATIO * this.pieces.length) {
            return;
         }

      }

      Piece chosen = requestStrategy.choosePiece(rarest, interesting, pieces);
      this.requestedPieces.set(chosen.getIndex());

      peer.downloadPiece(chosen);

   }

   /**
    * Piece availability handler.

    * Handle updates in piece availability from a peer's HAVE message. When
    * this happens, we need to mark that piece as available from the peer.

    *  peer The peer we got the update from.
    *  piece The piece that became available.
    */

   @Override
   public synchronized void handlePieceAvailability(SharingPeer peer, Piece piece) {

      // If we don't have this piece, tell the peer we're interested in
      // getting it from him.
      if (!this.completedPieces.get(piece.getIndex()) &&
         !this.requestedPieces.get(piece.getIndex())) {
         peer.interesting();
      }

      this.rarest.remove(piece);
      piece.seenAt();
      this.rarest.add(piece);


      if (!peer.isChoked() &&
         peer.isInteresting() &&
         !peer.isDownloading()) {
         this.handlePeerReady(peer);
      }


   }
   /**
    * Bit field availability handler.

    * Handle updates in piece availability from a peer's BITFIELD message.
    * When this happens, we need to mark in all the pieces the peer has that
    * they can be reached through this peer, thus augmenting the global
    * availability of pieces.

    *  peer The peer we got the update from.
    *  availablePieces The pieces availability bit field of the peer.
    */

   @Override
   public synchronized void handleBitfieldAvailability(SharingPeer peer, BitSet availablePieces) {
      // Determine if the peer is interesting for us or not, and notify it.
      BitSet interesting = (BitSet)availablePieces.clone();
      interesting.andNot(this.completedPieces);
      interesting.andNot(this.requestedPieces);

      if (interesting.cardinality() == 0) {
         peer.notInteresting();
      } else {
         peer.interesting();
      }

      // Record that the peer has all the pieces it told us it had.
      for (int i = availablePieces.nextSetBit(0); i >= 0;
           i = availablePieces.nextSetBit(i+1)) {
         this.rarest.remove(this.pieces[i]);
         this.pieces[i].seenAt();
         this.rarest.add(this.pieces[i]);
      }
   }
   /**
    * Piece upload completion handler.

    * When a piece has been sent to a peer, we just record that we sent that
    * many bytes. If the piece is valid on the peer's side, it will send us a
    * HAVE message and we'll record that the piece is available on the peer at
    * that moment.

    *  peer The peer we got this piece from.
    *  piece The piece in question.
    */

   @Override
   public synchronized void handlePieceSent(SharingPeer peer, Piece piece) {
      this.uploaded += piece.size();
   }

   /**
    * Piece download completion handler.

    * If the complete piece downloaded is valid, we can record in the torrent
    * completedPieces bit field that we know have this piece.

    *  peer The peer we got this piece from.
    * piece The piece in question.
    */
   @Override
   public synchronized void handlePieceCompleted(SharingPeer peer, Piece piece) throws IOException {
      // Regardless of validity, record the number of bytes downloaded and
      // mark the piece as not requested anymore
      this.downloaded += piece.size();
      this.requestedPieces.set(piece.getIndex(), false);

   }
   /**
    * Peer disconnection handler.

    * When a peer disconnects, we need to mark in all of the pieces it had
    * available that they can't be reached through this peer anymore.

    * peer The peer we got this piece from.
    */
   @Override
   public synchronized void handlePeerDisconnected(SharingPeer peer) {
      BitSet availablePieces = peer.getAvailablePieces();

      for (int i = availablePieces.nextSetBit(0); i >= 0;
           i = availablePieces.nextSetBit(i+1)) {
         this.rarest.remove(this.pieces[i]);
         this.pieces[i].noLongerAt();
         this.rarest.add(this.pieces[i]);
      }

      Piece requested = peer.getRequestedPiece();
      if (requested != null) {
         this.requestedPieces.set(requested.getIndex(), false);
      }

   }

   @Override
   public synchronized void handleIOException(SharingPeer peer, IOException ioe) {
            /* Do nothing */
   }

   @Override
   public void handleDownloadRateChange(long bytes) {

   }

   @Override
   public void handleUploadRateChange(long bytes) {

   }
}
