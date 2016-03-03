package com.work.speedtest_overhead.object;

/**
 * Created by ngodi on 3/1/2016.
 */
public class SpeedUpdateObj {
    float current;
    float max_wifi;
    float avg_wifi;
    float max_lte;
    float max;
    float avg_lte;

    public SpeedUpdateObj(float cur, float m_wifi, float a_wifi, float m_lte, float a_lte) {
        this.current = round(cur);
        this.max_wifi = round(m_wifi);
        this.avg_wifi = round(a_wifi);
        this.max_lte = round(m_lte);
        this.avg_lte = round(a_lte);
    }

    public SpeedUpdateObj(float cur, float max, float m_wifi, float a_wifi, float m_lte, float a_lte) {
        this.current = round(cur);
        this.max = round(max);
        this.max_wifi = round(m_wifi);
        this.avg_wifi = round(a_wifi);
        this.max_lte = round(m_lte);
        this.avg_lte = round(a_lte);
    }

    public float getCurrent() {
        return current;
    }

    public float getMax_wifi() {
        return max_wifi;
    }

    public float getAvg_wifi() {
        return avg_wifi;
    }

    public float getMax_lte() {
        return max_lte;
    }

    public float getAvg_lte() {
        return avg_lte;
    }

    public float getMax() {
        if(max_wifi == 0)
            return max_lte;
        else if(max_lte == 0)
            return max_wifi;
        return max;
    }

    public float getAvg() {
        if(avg_lte == 0)
            return avg_wifi;
        else if(avg_wifi == 0)
            return avg_lte;
        return (avg_wifi + avg_lte);
    }

    public float round(float val) {
        int tmp = (int) (val*10);
        float out = tmp / 10f;
        return out;
    }
}
