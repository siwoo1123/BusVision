package com.example.busvision;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    public ArrayList<Item> items = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView busNumber, nextStop;

        public ViewHolder(View view) {
            super(view);
            busNumber = view.findViewById(R.id.textView);
            nextStop = view.findViewById(R.id.textView2);
        }
        /*
        -Routetype-
        1: 일반
        2: 간선/심야
        3: 지선/맞춤
        4: 광역/직좌
        5: 마을/순환
        6: 시외/공항
        7~: 기타
         */

//        색상
//        간선/심야: #3B80AE
//        지선: #598526
//        순환/마을: #C68400
//        광역: #FF0000
//        일반: #009775
//        시외/공항: #AB22B5

        public void setItem(Item item) {
            busNumber.setText(item.bn);
            nextStop.setText(item.ns);

            nextStop.setTextColor(Color.WHITE);

            if(item.routetype == 1) {
                busNumber.setBackgroundColor(Color.parseColor("#009775"));
            } else if (item.routetype == 2) {
                busNumber.setBackgroundColor(Color.parseColor("#3B80AE"));
            } else if (item.routetype == 3) {
                busNumber.setBackgroundColor(Color.parseColor("#598526"));
            } else if (item.routetype == 4) {
                busNumber.setBackgroundColor(Color.parseColor("#FF0000"));
            } else if (item.routetype == 5) {
                busNumber.setBackgroundColor(Color.parseColor("#C68400"));
            } else if (item.routetype == 6) {
                busNumber.setBackgroundColor(Color.parseColor("#AB22B5"));
            } else {
                busNumber.setBackgroundColor(Color.BLACK);
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.bus_item, viewGroup, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Item item = items.get(position);
        viewHolder.setItem(item);
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public static class Item {
        String bn, ns;
        int routetype;

        public Item(Item item){
            this(item.bn, item.ns, item.routetype);
        }

        public Item(String bn, String ns, int routetype) {
            this.bn = bn;
            this.ns = ns;
            this.routetype = routetype;
        }
    }
}