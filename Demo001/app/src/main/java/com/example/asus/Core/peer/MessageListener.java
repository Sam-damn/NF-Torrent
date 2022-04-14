package com.example.asus.Core.peer;



import com.example.asus.Core.messages.PeerMessage;

import java.util.EventListener;

public interface MessageListener extends EventListener {

   public void handleMessage(PeerMessage msg);

   public void handleuploadRate(long bytes);
}
