package com.example.asus.Core.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.commons.io.FileUtils;

public class FileStorage implements TorrentByteStorage{



	private  File current;
	private final File target;
	private final File partial;
	private final long offset;
	private final long size;


	private RandomAccessFile raf;
	private FileChannel channel;



	public FileStorage(File file ,long size)throws IOException{

		this(file , 0 , size);

	}

	public FileStorage(File file , long offset , long size)throws IOException{

		this.target = file;
		this.offset=offset;
		this.size=size;

		this.partial = new File(this.target.getAbsolutePath() + TorrentByteStorage.PARTIAL_FILE_NAME_SUFFIX);

		if(this.partial.exists()){

			System.out.println("partial download found continuing..");

			this.current = this.partial;
		}
		else if(!this.target.exists()){

			System.out.println("starting new download ..");

			this.current=this.partial;
		}
		else{

			System.out.println("existing file found  lets use it..");

			this.current  = this.target;

		}

		this.raf = new RandomAccessFile(this.current , "rw");

		if(file.length() != this.size){
			this.raf.setLength(this.size);
		}


		this.channel = raf.getChannel();
	}


	protected long offset() {
		return this.offset;
	}

	@Override
	public int read(ByteBuffer buffer, long offset) throws IOException {

		int requested = buffer.remaining();

		if(offset + requested > this.size){
			throw  new IllegalArgumentException("invalid storage read request nigger!");

		}
		int bytes = channel.read(buffer,offset);

		if(bytes < requested){
			throw new IOException("storage underrun!");
		}

		return bytes;
	}

	@Override
	public int write(ByteBuffer buffer, long offset) throws IOException {

		int requested = buffer.remaining();

		if(offset + requested > this.size){

			throw  new IllegalArgumentException("invalid storage write request nigger!");

		}

		return this.channel.write(buffer,offset);
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public synchronized void close() throws IOException {
		if(channel.isOpen()){
			channel.force(true);
		}
		this.raf.close();

	}

	@Override
	public synchronized void finish() throws IOException {

		if(channel.isOpen()){
			channel.force(true);
		}

		if(isFinished()){
			return;
		}

		this.raf.close();

		FileUtils.deleteQuietly(this.target);
		FileUtils.moveFile(this.current,this.target);

		this.raf = new RandomAccessFile(this.target, "rw");
		this.raf.setLength(this.size);
		this.channel = this.raf.getChannel();
		this.current = this.target;

		FileUtils.deleteQuietly(this.partial);

	}

	@Override
	public boolean isFinished() {
		return this.current.equals(this.target);
	}
}