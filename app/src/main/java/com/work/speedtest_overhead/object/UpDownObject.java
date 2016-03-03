package com.work.speedtest_overhead.object;

/**
 * Created by ngodi on 2/26/2016.
 */
public class UpDownObject {
    float max;
    float wifi;
    float lte;

    public UpDownObject(float max, float wifi, float lte) {
        this.max = max;
        this.wifi = wifi;
        this.lte = lte;
    }

    public UpDownObject(float wifi, float lte) {
        this.wifi = wifi;
        this.lte = lte;
    }

    public float getMax() {
        return this.max;
    }

    public float getWifi() {
        return this.wifi;
    }

    public float getLte() {
        return this.lte;
    }

    public float getAvg() {
        return (this.wifi + this.lte);
    }
}
