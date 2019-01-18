package com.t090.miband2remote;

import java.sql.Time;

/**
 * Created by HUYVINH on 3/1/2017.
 */

public class HeartRate {
    public int HR;
    public String TIME;

    public HeartRate() {
    }

    public HeartRate(int HR, String TIME) {
        this.HR = HR;
        this.TIME = TIME;
    }
}
