package com.example.asus.Core.client;



import com.example.asus.Core.base.Torrent;
import com.example.asus.Core.storage.TorrentByteStorage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class Piece implements Comparable<Piece>{


   private final TorrentByteStorage bucket;
   private final int index;
   private final long offset;
   private final long length;
   private final  byte[] hash;
   private final boolean seeder;

   private volatile boolean valid;
   private int seen;
   private ByteBuffer data;




   public Piece(TorrentByteStorage bucket , int index , long offset , long length , byte[] hash, boolean seeder){
      this.bucket=bucket;
      this.index=index;
      this.offset=offset;
      this.length=length;
      this.hash=hash;
      this.seeder=seeder;

      valid = false;

      seen = 0;

      data = null;

   }

   public boolean isValid() {
      return this.valid;
   }

   public int getIndex() {
      return this.index;
   }

   public long size() {
      return this.length;
   }

   public boolean available() {
      return this.seen > 0;
   }

   public void seenAt(){

      this.seen++;

   }

   public void noLongerAt(){
      this.seen--;

   }



   public synchronized boolean validate() throws IOException {
      if(this.seeder){

         this.valid = true;
         return true;

      }
      this.valid = false;
     ByteBuffer buffer = this._read(0,this.length);
     byte[] array  = new byte[(int)this.length];
     buffer.get(array);

      try {
         this.valid= Arrays.equals(Torrent.hash(array),this.hash);
      } catch (NoSuchAlgorithmException e) {
         this.valid = false;
      }

      return isValid();

   }


   private ByteBuffer _read(long offset ,long length) throws IOException {

      if(offset + length > this.length){
         throw new IllegalArgumentException("client.Piece#" + this.index +
            " overrun (" + offset + " + " + length + " > " +
            this.length + ") !");
      }

      ByteBuffer buffer = ByteBuffer.allocate((int)length);
     int bytes =  bucket.read(buffer,this.offset + offset);
     buffer.rewind();
     buffer.limit(bytes >= 0 ? bytes : 0);
     return buffer;

   }

   public ByteBuffer read(long offset, int length)
      throws IllegalArgumentException, IllegalStateException, IOException {
      if (!this.valid) {
         throw new IllegalStateException("Attempting to read an " +
            "known-to-be invalid piece!");
      }

      return this._read(offset, length);
   }

   public synchronized void record(ByteBuffer block , int offset) throws IOException {
      if(data ==null   || offset ==0){

         this.data = ByteBuffer.allocate((int)this.length);
      }

      int pos= block.position();
      this.data.position(offset);
      this.data.put(block);
      block.position(pos);

      if(block.remaining() + offset == this.length){
         data.rewind();
         this.bucket.write(this.data,this.offset);
         this.data =null;




      }


   }

   public String toString() {
      return String.format("piece#%4d%s",
         this.index,
         this.isValid() ? "+" : "-");
   }

   public int compareTo(Piece other) {
      if (this.seen != other.seen) {
         return this.seen < other.seen ? -1 : 1;
      }
      return this.index == other.index ? 0 :
         (this.index < other.index ? -1 : 1);
   }

   public static class CallableHasher implements Callable<Piece> {

      private final Piece piece;

      public CallableHasher(Piece piece) {
         this.piece = piece;
      }

      @Override
      public Piece call() throws IOException {
         this.piece.validate();
         return this.piece;
      }
   }








}
