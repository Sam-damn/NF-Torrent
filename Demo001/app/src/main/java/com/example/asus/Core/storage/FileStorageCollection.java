package com.example.asus.Core.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class FileStorageCollection implements TorrentByteStorage{



	private final long size;
	private final List<FileStorage> files;


	public FileStorageCollection(List<FileStorage> files , long size){
		this.files=files;
		this.size=size;


	}


	@Override
	public int read(ByteBuffer buffer, long offset) throws IOException {
		int requested = buffer.remaining();
		int bytes = 0;
		for(FileOffset fo: select(offset,requested)){
			buffer.limit((bytes + (int)fo.length));

			bytes+=fo.file.read(buffer,fo.offset);

		}

		if (bytes < requested) {
			throw new IOException("Storage collection read underrun!");
		}

		return bytes;
	}

	@Override
	public int write(ByteBuffer buffer, long offset) throws IOException {
		int requested = buffer.remaining();
		int bytes = 0;
		for(FileOffset fo: select(offset,requested)){
			buffer.limit((bytes + (int)fo.length));

			bytes+=fo.file.write(buffer,fo.offset);

		}

		if (bytes < requested) {
			throw new IOException("Storage collection write underrun!");
		}

		return bytes;
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public void close() throws IOException {

		for (FileStorage file : this.files) {
			file.close();
		}

	}

	@Override
	public void finish() throws IOException {

		for (FileStorage file : this.files) {
			file.finish();
		}


	}

	@Override
	public boolean isFinished() {

		for (FileStorage file : this.files) {
			if (!file.isFinished()) {
				return false;
			}
		}

		return true;





	}



	private static class FileOffset {

		public final FileStorage file;
		public final long offset;
		public final long length;

		FileOffset(FileStorage file, long offset, long length) {
			this.file = file;
			this.offset = offset;
			this.length = length;
		}
	};



	private List<FileOffset> select(long offset , long length){

		if(offset + length > this.size){

			throw new IllegalArgumentException("Operation exceeded the limit of files storage");
		}

		List<FileOffset> selected  = new LinkedList<>();
		long bytes = 0;

		for(FileStorage fileStorage : files){

			if(fileStorage.offset() > offset  +  length){
				break;
			}


			if(fileStorage.offset() + fileStorage.size() < offset){
				continue;
			}



			long position =offset - fileStorage.offset();
			position = position > 0 ? position : 0;
			long size = Math.min(
					fileStorage.size() - position,
					length - bytes);
			selected.add(new FileOffset(fileStorage,position,size));
			bytes +=size;



		}




		if (selected.size() == 0 || bytes < length) {
			throw new IllegalStateException("error selecting files in operation ,only got " +
					bytes + " out of " + length + " byte(s) requested!");
		}

		return selected;


	}
}