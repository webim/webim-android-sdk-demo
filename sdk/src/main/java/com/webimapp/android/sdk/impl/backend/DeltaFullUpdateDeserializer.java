package com.webimapp.android.sdk.impl.backend;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

import com.webimapp.android.sdk.impl.items.delta.DeltaFullUpdate;

public class DeltaFullUpdateDeserializer implements JsonDeserializer<DeltaFullUpdate> {
    @SuppressWarnings("deprecation")
    @Override
    public DeltaFullUpdate deserialize(JsonElement je, Type type, JsonDeserializationContext ctx)
            throws JsonParseException {
        DeltaFullUpdate fullUpdate = new Gson().fromJson(je, type);
        fullUpdate.setVisitorJson(je.getAsJsonObject().get("visitor").toString());
        return fullUpdate;
    }
}