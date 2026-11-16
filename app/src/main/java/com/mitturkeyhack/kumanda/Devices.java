package com.mitturkeyhack.kumanda;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Kumanda'ya eklenmiş telefonlar. */
public final class Devices {

    public static final class Device {
        public final String name;
        public final String code;

        public Device(String n, String c) {
            name = n;
            code = c;
        }
    }

    private Devices() {}

    public static List<Device> load(Context c) {
        List<Device> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(P.str(c, "devices", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Device(o.getString("name"), o.getString("code")));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context c, List<Device> list) {
        JSONArray a = new JSONArray();
        try {
            for (Device d : list) {
                JSONObject o = new JSONObject();
                o.put("name", d.name);
                o.put("code", d.code);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        P.put(c, "devices", a.toString());
    }
}
