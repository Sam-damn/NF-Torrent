package com.example.ichigo.Gui;

import com.example.asus.Core.client.Client;

/**
 * Created by Asus on 7/16/2018.
 */

public interface ClientActivityListener {



   //  public int handlepiecespercentage();

   //  public void handleMessage(String text);

    public void handleRateChange(float upload_rate, float download_rate, int id);
     public void handleStateChange(int Id, Client.ClientState newState);

    public  void handlePieceCompletion(int Id,int precenetage);


}
