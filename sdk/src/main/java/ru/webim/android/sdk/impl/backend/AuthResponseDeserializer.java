package ru.webim.android.sdk.impl.backend;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

import ru.webim.android.sdk.impl.items.responses.AuthResponse;

public class AuthResponseDeserializer implements JsonDeserializer<AuthResponse> {
    @Override
    public AuthResponse deserialize(JsonElement je, Type type, JsonDeserializationContext ctx)
        throws JsonParseException {
        AuthResponse authResponse = new Gson().fromJson(je, type);
        authResponse.setVisitor(je.getAsJsonObject().get("visitor").toString());
        return authResponse;
    }
}