package com.example.asus.Core.connection;


import android.util.Log;

import com.example.asus.Core.base.Torrent;
import com.example.asus.Core.client.SharedTorrent;
import com.example.asus.Core.peer.SharingPeer;
import com.example.asus.Core.util.Utils;

import org.apache.commons.io.IOUtils;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Incoming peer connections service.

 * Every BitTorrent client, BitTorrent being a peer-to-peer protocol, listens
 * on a port for incoming connections from other peers sharing the same
 * torrent.

 * This ConnectionHandler implements this service and starts a listening socket
 * in the first available port in the default BitTorrent client port range
 * 6881-6889. When a peer connects to it, it expects the BitTorrent handshake
 * message, parses it and replies with our own handshake.

 * Outgoing connections to other peers are also made through this service,
 * which handles the handshake procedure with the remote peer. Regardless of
 * the direction of the connection, once this handshake is successful, all
 * { IncomingConnectionListener}s are notified and passed the connected
 * socket and the remote peer ID.

 * This class does nothing more. All further peer-to-peer communication happens
 * in the PeerExchange class.

*/
public class ConnectionHandler implements Runnable{

   public static final int PORT_RANGE_START = 49152;
   public static final int PORT_RANGE_END = 65534;


   private static final int OUTBOUND_CONNECTIONS_POOL_SIZE = 20;
   private static final int OUTBOUND_CONNECTIONS_THREAD_KEEP_ALIVE_SECS = 10;

   private static final int CLIENT_KEEP_ALIVE_MINUTES = 3;


   private SharedTorrent torrent;
   private String id;
   private ServerSocketChannel channel;
   private InetSocketAddress address;

   private Set<IncomingConnectionListener> listeners;
   private ExecutorService executor;
   private Thread thread;
   private boolean stop;



   public ConnectionHandler(SharedTorrent torrent, String id, InetAddress address) throws IOException{
      this.torrent=torrent;
      this.id = id;


      for(int port =ConnectionHandler.PORT_RANGE_START; port <= ConnectionHandler.PORT_RANGE_END ; port++){


         InetSocketAddress tryAdress = new InetSocketAddress(address,port);
         try{
            this.channel=ServerSocketChannel.open();
            this.channel.socket().bind(tryAdress);
            this.channel.configureBlocking(false);
            this.address=tryAdress;
             Log.i("info" ,"connection handler: connected!");
             break;



         }catch (IOException ioe){
            //ignore try next port
         }

      }

      if(this.channel==null  || !this.channel.socket().isBound()){

         throw new IOException("No available port for the bittorent client!");
      }

      this.listeners= new HashSet<IncomingConnectionListener>();
      this.executor=null;
      this.thread=null;
   }

   /**
    * Return the full socket address this service is bound to.
    */

   public InetSocketAddress getSocketAddress() {
      return this.address;
   }


   public void register(IncomingConnectionListener listener) {
      this.listeners.add(listener);
   }


   /**
    * Start accepting new connections in a background thread.
    */
   public void start(){

      if (this.channel == null) {
         throw new IllegalStateException(
            "Connection handler cannot be recycled!");
      }

      this.stop = false;


      if (this.executor == null || this.executor.isShutdown()) {
         this.executor = new ThreadPoolExecutor(
            OUTBOUND_CONNECTIONS_POOL_SIZE,
            OUTBOUND_CONNECTIONS_POOL_SIZE,
            OUTBOUND_CONNECTIONS_THREAD_KEEP_ALIVE_SECS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ConnectorThreadFactory());
      }

      if (this.thread == null || !this.thread.isAlive()) {
         this.thread = new Thread(this);
         this.thread.setName("bt-serve");
         this.thread.start();
      }

   }


/**
 * Stop accepting connections.
 * the underlying socket remains open and bound.
*/

public void stop() {
   this.stop = true;

   if (this.thread != null && this.thread.isAlive()) {
      try {
         this.thread.join();
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
   }

   if (this.executor != null && !this.executor.isShutdown()) {
      this.executor.shutdownNow();
   }

   this.executor = null;
   this.thread = null;
}


/**
 * Close this connection handler to release the port it is bound to.
 * throws IOException If the channel could not be closed.
 */

public void close() throws IOException {
   if (this.channel != null) {
      this.channel.close();
      this.channel = null;
   }
}

   /**
    * The main service loop.
    *
    * <p>
    * The service waits for new connections for 250ms, then waits 100ms so it
    * can be interrupted.
    * </p>
    */
   @Override
   public void run() {
      while (!this.stop) {


         try {
            SocketChannel client = this.channel.accept();

            if (client != null) {
               this.accept(client);
            }
         } catch (SocketTimeoutException ste) {
            // Ignore and go back to sleep
         } catch (IOException ioe) {
            this.stop();
         }

         try {
            Thread.sleep(100);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private String socketRepr(SocketChannel channel) {
      Socket s = channel.socket();
      return String.format("%s:%d%s",
         s.getInetAddress().getHostName(),
         s.getPort(),
         channel.isConnected() ? "+" : "-");
   }
   /**
    * Accept the next incoming connection.

    * When a new peer connects to this service, wait for it to send its
    * handshake. We then parse and check that the handshake advertises the
    * torrent hash we expect, then reply with our own handshake.

    * If everything goes according to plan, notify the
    *  with the connected socket and the parsed peer ID.
    *client The accepted client's socket channel.
    */

   private void accept(SocketChannel client)
      throws IOException, SocketTimeoutException {
      try {
         Handshake hs = this.validateHandshake(client, null);
         int sent = this.sendHandshake(client);


         // Go to non-blocking mode for peer interaction
         client.configureBlocking(false);
         client.socket().setSoTimeout(CLIENT_KEEP_ALIVE_MINUTES*60*1000);
         this.fireNewPeerConnection(client, hs.getPeerId());
      } catch (ParseException pe) {

         IOUtils.closeQuietly(client);
      } catch (IOException ioe) {

         if (client.isConnected()) {
            IOUtils.closeQuietly(client);
         }
      }
   }

   /**
    * Tells whether the connection handler is running and can be used to
    * handle new peer connections.
    */
   public boolean isAlive() {
      return this.executor != null &&
         !this.executor.isShutdown() &&
         !this.executor.isTerminated();
   }

   /**
    * Connect to the given peer and perform the BitTorrent handshake.
    * Submits an asynchronous connection task to the outbound connections
    * executor to connect to the given peer.
    * peer The peer to connect to.
    */
   public void connect(SharingPeer peer) {
      if (!this.isAlive()) {
         throw new IllegalStateException(
            "Connection handler is not accepting new peers at this time!");
      }

      this.executor.submit(new ConnectorTask(this, peer));
   }


   /**
    * Validate an expected handshake on a connection.

    * Reads an expected handshake message from the given connected socket,
    * parses it and validates that the torrent hash_info corresponds to the
    * torrent we're sharing, and that the peerId matches the peer ID we expect
    * to see coming from the remote peer.

    *  channel The connected socket channel to the remote peer.
    *  peerId The peer ID we expect in the handshake. If <em>null</em>,
    *  any peer ID is accepted (this is the case for incoming connections).
    * return The validated handshake message object.
    */
   private Handshake validateHandshake(SocketChannel channel, byte[] peerId)
      throws IOException, ParseException {
      ByteBuffer len = ByteBuffer.allocate(1);
      ByteBuffer data;

      // Read the handshake from the wire
      if (channel.read(len) < len.capacity()) {
         throw new IOException("Handshake size read underrrun");
      }

      len.rewind();
      int pstrlen = len.get();

      data = ByteBuffer.allocate(Handshake.BASE_HANDSHAKE_LENGTH + pstrlen);
      data.put((byte)pstrlen);
      int expected = data.remaining();
      int read = channel.read(data);
      if (read < expected) {
         throw new IOException("Handshake data read underrun (" +
            read + " < " + expected + " bytes)");
      }

      // Parse and check the handshake
      data.rewind();
      Handshake hs = Handshake.parse(data);
      if (!Arrays.equals(hs.getInfoHash(), this.torrent.getInfoHash())) {
         throw new ParseException("Handshake for unknow torrent " +
            Utils.bytesToHex(hs.getInfoHash()) +
            " from " + this.socketRepr(channel) + ".", pstrlen + 9);
      }

      if (peerId != null && !Arrays.equals(hs.getPeerId(), peerId)) {
         throw new ParseException("Announced peer ID " +
            Utils.bytesToHex(hs.getPeerId()) +
            " did not match expected peer ID " +
            Utils.bytesToHex(peerId) + ".", pstrlen + 29);
      }

      return hs;
   }


   /**
    * Send our handshake message to the socket.
    * channel The socket channel to the remote peer.
    */
   private int sendHandshake(SocketChannel channel) throws IOException {
      return channel.write(
         Handshake.craft(
            this.torrent.getInfoHash(),
            this.id.getBytes(Torrent.BYTE_ENCODING)).getData());
   }


   private void fireNewPeerConnection(SocketChannel channel, byte[] peerId) {
      for (IncomingConnectionListener listener : this.listeners) {
         listener.handleNewPeerConnection(channel, peerId);
      }
   }

   private void fireFailedConnection(SharingPeer peer, Throwable cause) {
      for (IncomingConnectionListener listener : this.listeners) {
         listener.handleFailedConnection(peer, cause);
      }
   }

   /**
    * A simple thread factory that returns appropriately named threads for
    * outbound connector threads.
    */
   private static class ConnectorThreadFactory implements ThreadFactory {

      private int number = 0;

      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r);
         t.setName("bt-connect-" + ++this.number);
         return t;
      }
   }


   /**
    * An outbound connection task.

    * These tasks are fed to the thread executor in charge of processing
    * outbound connection requests. It attempts to connect to the given peer
    * and proceeds with the BitTorrent handshake. If the handshake is
    * successful, the new peer connection event is fired to all incoming
    * connection listeners. Otherwise, the failed connection event is fired.

    */
   private static class ConnectorTask implements Runnable {

      private final ConnectionHandler handler;
      private final SharingPeer peer;

      private ConnectorTask(ConnectionHandler handler, SharingPeer peer) {
         this.handler = handler;
         this.peer = peer;
      }

      @Override
      public void run() {
         InetSocketAddress address =
            new InetSocketAddress(this.peer.getIp(), this.peer.getPort());
         SocketChannel channel = null;

         try {
            channel = SocketChannel.open(address);
            while (!channel.isConnected()) {
               Thread.sleep(10);
            }

            channel.configureBlocking(true);
            int sent = this.handler.sendHandshake(channel);
            Handshake hs = this.handler.validateHandshake(channel,
               (this.peer.hasPeerId()
                  ? this.peer.getPeerId().array()
                  : null));

            // Go to non-blocking mode for peer interaction
            channel.configureBlocking(false);
            this.handler.fireNewPeerConnection(channel, hs.getPeerId());

            if(hs !=null){
               Log.i("info" , "connection handler: Peer_Connection:" + peer.getIp());
            }

         } catch (Exception e) {
            if (channel != null && channel.isConnected()) {
               IOUtils.closeQuietly(channel);
            }
            this.handler.fireFailedConnection(this.peer, e);
         }
      }
   }




}
