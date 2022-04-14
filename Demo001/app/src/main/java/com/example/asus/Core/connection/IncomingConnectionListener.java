package com.example.asus.Core.connection;


import com.example.asus.Core.peer.SharingPeer;

import java.nio.channels.SocketChannel;

public interface IncomingConnectionListener {

   public void handleNewPeerConnection(SocketChannel channel, byte[] peerId);

   public void handleFailedConnection(SharingPeer peer, Throwable cause);

}
