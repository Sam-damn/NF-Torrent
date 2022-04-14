package com.example.asus.Core.peer;




import com.example.asus.Core.client.Piece;

import java.io.IOException;
import java.util.BitSet;

/**
 * EventListener interface for objects that want to handle peer activity
 * events like piece availability, or piece completion events, and more.
 *
 * @author mpetazzoni
 */

public interface PeerActivityListener {
   /**
    * Peer choked handler.
    *
    * <p>
    * This handler is fired when a peer choked and now refuses to send data to
    * us. This means we should not try to request or expect anything from it
    * until it becomes ready again.
    * </p>
    *
    * @param peer The peer that choked.
    */
   public void handlePeerChoked(SharingPeer peer);

   /**
    * Peer ready handler.
    *
    * <p>
    * This handler is fired when a peer notified that it is no longer choked.
    * This means we can send piece block requests to it and start downloading.
    * </p>
    *
    * @param peer The peer that became ready.
    */
   public void handlePeerReady(SharingPeer peer);

   /**
    * Piece availability handler.
    *
    * <p>
    * This handler is fired when an update in piece availability is received
    * from a peer's HAVE message.
    * </p>
    *
    * @param peer The peer we got the update from.
    * @param piece The piece that became available from this peer.
    */
   public void handlePieceAvailability(SharingPeer peer, Piece piece);

   /**
    * Bit field availability handler.
    *
    * <p>
    * This handler is fired when an update in piece availability is received
    * from a peer's BITFIELD message.
    * </p>
    *
    * @param peer The peer we got the update from.
    * @param availablePieces The pieces availability bit field of the peer.
    */
   public void handleBitfieldAvailability(SharingPeer peer,
                                          BitSet availablePieces);

   /**
    * Piece upload completion handler.
    *
    * <p>
    * This handler is fired when a piece has been uploaded entirely to a peer.
    * </p>
    *
    * @param peer The peer the piece was sent to.
    * @param piece The piece in question.
    */
   public void handlePieceSent(SharingPeer peer, Piece piece);

   /**
    * Piece download completion handler.
    *
    * <p>
    * This handler is fired when a piece has been downloaded entirely and the
    * piece data has been revalidated.
    * </p>
    *
    * <p>
    * <b>Note:</b> the piece may <em>not</em> be valid after it has been
    * downloaded, in which case appropriate action should be taken to
    * redownload the piece.
    * </p>
    *
    * @param peer The peer we got this piece from.
    * @param piece The piece in question.
    */
   public void handlePieceCompleted(SharingPeer peer, Piece piece)
      throws IOException;

   /**
    * Peer disconnection handler.
    *
    * <p>
    * This handler is fired when a peer disconnects, or is disconnected due to
    * protocol violation.
    * </p>
    *
    * @param peer The peer we got this piece from.
    */
   public void handlePeerDisconnected(SharingPeer peer);

   /**
    * Handler for IOException during peer operation.
    *
    * @param peer The peer whose activity trigger the exception.
    * @param ioe The IOException object, for reporting.
    */
   public void handleIOException(SharingPeer peer, IOException ioe);


   /*
   handler for change in download speed from across all peers

    */
   public void handleDownloadRateChange(long bytes);

   /*

   handler for change in upload speed from across all peers

    */

   public void handleUploadRateChange(long bytes);


}
