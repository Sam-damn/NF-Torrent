package com.example.asus.Core.storage;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface TorrentByteStorage {


	public static final String  PARTIAL_FILE_NAME_SUFFIX = ".part";




	public int read(ByteBuffer buffer , long offset) throws IOException;




	public int write(ByteBuffer buffer , long offset) throws IOException;




	public long size();


	public void close() throws IOException;


	public void finish() throws IOException;


	public boolean isFinished();


}