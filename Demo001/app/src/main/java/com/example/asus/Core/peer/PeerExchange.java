package com.example.asus.Core.peer;

import android.util.Log;

import com.example.asus.Core.client.SharedTorrent;
import com.example.asus.Core.messages.PeerMessage;

import org.apache.commons.io.IOUtils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.text.ParseException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PeerExchange {


    private static final int KEEP_ALIVE_IDLE_MINUTES = 2;
    private static  PeerMessage STOP = PeerMessage.KeepAliveMessage.craft();
    //
    private SharingPeer peer;
    private SharedTorrent torrent;
    private SocketChannel channel;
    //
    private Set<MessageListener> listeners;
    //
    private IncomingThread in;
    private OutgoingThread out;
    private BlockingQueue<PeerMessage> sendQueue;
    private volatile boolean stop;

    public PeerExchange(SharingPeer peer, SharedTorrent torrent, SocketChannel channel)
    {
        this.peer = peer;
        this.torrent = torrent;
        this.channel = channel;

        this.listeners = new HashSet<MessageListener>();
        this.sendQueue = new LinkedBlockingQueue<>();

        if(!this.peer.hasPeerId())
        {
            throw new IllegalStateException("Peer does not have a " +
                    "peer ID. Was the handshake made properly?");
        }

        this.in = new IncomingThread();
        this.in.setName("bt-peer(" +
                this.peer.getShortHexPeerId() + ")-recv");

        this.out = new OutgoingThread();
        this.out.setName("bt-peer(" +
                this.peer.getShortHexPeerId() + ")-send");
        this.out.setDaemon(true);

        this.stop = false;


        // If we have pieces, start by sending a BITFIELD message to the peer.
        BitSet pieces = this.torrent.getCompletedPieces();
        if (pieces.cardinality() > 0) {
            this.send(PeerMessage.BitfieldMessage.craft(pieces, torrent.getPieceCount()));
        }
    }

    public void register(MessageListener listener) {
        this.listeners.add(listener);
    }

    public boolean isConnected() {
        return this.channel.isConnected();
    }

    public void send(PeerMessage message) {
        try {
            this.sendQueue.put(message);
        } catch (InterruptedException ie) {
            // Ignore, our send queue will only block if it contains
            // MAX_INTEGER messages, in which case we're already in big
            // trouble, and we'd have to be interrupted, too.
        }
    }

    public void start() {
        this.in.start();
        this.out.start();
    }
    public void stop()
    {
        this.stop = true;

        try {
            // Wake-up and shutdown out-going thread immediately
            this.sendQueue.put(STOP);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (this.channel.isConnected()) {
            IOUtils.closeQuietly(this.channel);
        }
    }

    private abstract class RateLimitThread extends Thread {

        protected final Rate rate = new Rate();
        protected long sleep = 1000;

        /**
         * Dynamically determines an amount of time to sleep, based on the
         * average read/write throughput.
         *
         * <p>
         * The algorithm is functional, but could certainly be improved upon.
         * One obvious drawback is that with large changes in
         * <code>maxRate</code>, it will take a while for the sleep time to
         * adjust and the throttled rate to "smooth out."
         * </p>
         *
         * <p>
         * Ideally, it would calculate the optimal sleep time necessary to hit
         * a desired throughput rather than continuously adjust toward a goal.
         * </p>
         *
         * @param maxRate the target rate in kB/second.
         * @param messageSize the size, in bytes, of the last message read/written.
         * @param message the last <code>PeerMessage</code> read/written.
         */
        protected void rateLimit(double maxRate, long messageSize, PeerMessage message) {
            if (message.getType() != PeerMessage.Type.PIECE || maxRate <= 0) {
                return;
            }

            try {
                this.rate.add(messageSize);

                // Continuously adjust the sleep time to try to hit our target
                // rate limit.
                if (rate.get() > (maxRate * 1024)) {
                    Thread.sleep(this.sleep);
                    this.sleep += 50;
                } else {
                    this.sleep = this.sleep > 50
                            ? this.sleep - 50
                            : 0;
                }
            } catch (InterruptedException e) {
                // Not critical, eat it.
            }
        }

    }
    private class IncomingThread extends RateLimitThread {

        /**
         * Read data from the incoming channel of the socket using a {@link
         * Selector}.
         *
         * @param selector The socket selector into which the peer socket has
         *	been inserted.
         * @param buffer A {@link ByteBuffer} to put the read data into.
         * @return The number of bytes read.
         */
        private long read(Selector selector, ByteBuffer buffer) throws IOException {
            if (selector.select() == 0 || !buffer.hasRemaining()) {
                return 0;
            }

            long size = 0;
            Iterator it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = (SelectionKey) it.next();
                if (key.isValid() && key.isReadable()) {
                    int read = ((SocketChannel) key.channel()).read(buffer);
                    if (read < 0) {
                        throw new IOException("Unexpected end-of-stream while reading");
                    }
                    size += read;
                }
                it.remove();
            }

            return size;
        }

        private void handleIOE(IOException ioe) {
            peer.unbind(true);
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1*1024*1024);
            Selector selector = null;

            try {
                selector = Selector.open();
                channel.register(selector, SelectionKey.OP_READ);

                while (!stop) {
                    buffer.rewind();
                    buffer.limit(PeerMessage.MESSAGE_LENGTH_FIELD_SIZE);

                    // Keep reading bytes until the length field has been read
                    // entirely.
                    while (!stop && buffer.hasRemaining()) {
                        this.read(selector, buffer);
                    }

                    // Reset the buffer limit to the expected message size.
                    int pstrlen = buffer.getInt(0);

                    buffer.limit(PeerMessage.MESSAGE_LENGTH_FIELD_SIZE + pstrlen);

                    long size = 0;
                    while (!stop && buffer.hasRemaining()) {
                        size += this.read(selector, buffer);
                    }

                    buffer.rewind();

                    if (stop) {
                        // The buffer may contain the type from the last message
                        // if we were stopped before reading the payload and cause
                        // BufferUnderflowException in parsing.
                        break;
                    }

                    try {
                        PeerMessage message = PeerMessage.parse(buffer, torrent);


                        // Wait if needed to reach configured download rate.
                        this.rateLimit(
                                PeerExchange.this.torrent.getMaxDownloadRate(),
                                size, message);

                        Log.i("info" , "message sent :" + message.toString() + "by:" + peer.toString());


                        for (MessageListener listener : listeners)
                            listener.handleMessage(message);
                    } catch (ParseException pe) {
                        pe.printStackTrace();
                    }
                    catch (IllegalStateException ise){
                        //do nothing
                    }

                }
            } catch (IOException ioe) {
                this.handleIOE(ioe);
            } finally {
                try {
                    if (selector != null) {
                        selector.close();
                    }
                } catch (IOException ioe) {
                    this.handleIOE(ioe);
                }
            }
        }
    }
    private class OutgoingThread extends RateLimitThread {

        @Override
        public void run() {
            try {
                // Loop until told to stop. When stop was requested, loop until
                // the queue is served.
                while (!stop || (stop && sendQueue.size() > 0)) {
                    try {
                        // Wait for two minutes for a message to send
                        PeerMessage message = sendQueue.poll(
                                PeerExchange.KEEP_ALIVE_IDLE_MINUTES,
                                TimeUnit.MINUTES);

                        if (message == STOP) {
                            return;
                        }

                        if (message == null) {
                            message = PeerMessage.KeepAliveMessage.craft();
                        }

                        ByteBuffer data = message.getData();
                        long size = 0;
                        while (!stop && data.hasRemaining()) {
                            int written = channel.write(data);
                            size += written;
                            if (written < 0) {
                                throw new EOFException(
                                        "Reached end of stream while writing");
                            }
                        }

                        if (message instanceof PeerMessage.PieceMessage){
                            Log.i("uploading:","i am sameh and i am dick lover !!!!!!!!!!1<3 d ");
                             for (MessageListener listener : listeners)
                                  listener.handleuploadRate(size);

                        }

                        // Wait if needed to reach configured upload rate.
                        this.rateLimit(PeerExchange.this.torrent.getMaxUploadRate(),
                                size, message);
                    } catch (InterruptedException ie) {
                        // Ignore and potentially terminate
                    }
                }
            } catch (IOException ioe) {
                peer.unbind(true);
            }
        }
    }
}
