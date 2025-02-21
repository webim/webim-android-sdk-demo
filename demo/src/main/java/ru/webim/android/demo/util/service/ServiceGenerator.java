package ru.webim.android.demo.util.service;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ServiceGenerator {
    public static DemoWebimService createService(String baseDomain) {
        Retrofit.Builder builder = new Retrofit.Builder()
            .baseUrl(baseDomain)
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder().build());

        return builder.build().create(DemoWebimService.class);
    }
}
