package com.example.asus.Core.peer;

/**
 * Created by Asus on 7/21/2018.
 */

public class RatePerSec {

    private long bytes = 0;



    public synchronized float get() {

        float temp = this.bytes;
        this.bytes=0;
        return temp;

    }



    public synchronized void add(long count) {
        this.bytes += count;
    }
}
