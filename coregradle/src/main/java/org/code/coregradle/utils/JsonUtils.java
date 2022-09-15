package org.code.coregradle.utils;

import org.json.JSONArray;

import java.util.Collection;

public class JsonUtils {

    public static <T> JSONArray toJSONArray(Collection<T> list) {
        JSONArray collection = new JSONArray();
        for (T o : list) {
            collection.put(o);
        }
        return collection;
    }
}

