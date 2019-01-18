package com.t090.miband2remote;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by HUYVINH on 3/1/2017.
 */

public class AdapterHeartRate extends BaseAdapter {

    Context context;
    ArrayList<HeartRate> list;

    public AdapterHeartRate(Context context, ArrayList<HeartRate> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View row = inflater.inflate(R.layout.listview_row, null);

        TextView txtTIME = (TextView) row.findViewById(R.id.txtTIME);
        TextView txtHR = (TextView) row.findViewById(R.id.txtHR);

        HeartRate heartRate = list.get(position);

        txtTIME.setText(heartRate.TIME + "");
        txtHR.setText(heartRate.HR + "");

        return row;
    }
}
