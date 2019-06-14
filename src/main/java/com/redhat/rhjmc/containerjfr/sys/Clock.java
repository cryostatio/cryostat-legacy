package com.redhat.rhjmc.containerjfr.sys;

import java.util.concurrent.TimeUnit;

public class Clock {

    public long getWallTime() {
        return System.currentTimeMillis();
    }

    public long getMonotonicTime() {
        return System.nanoTime();
    }

    public void sleep(int millis) throws InterruptedException {
        sleep(TimeUnit.MILLISECONDS, millis);
    }

    public void sleep(TimeUnit unit, int quant) throws InterruptedException {
        Thread.sleep(unit.toMillis(quant));
    }

}