package ru.webim.android.demo.util.service;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DemoWebimService {
    String URL_SUFFIX_DEMO_VISITOR = "l/v/m/demo-visitor";
    String PARAMETER_VISITOR = "webim-visitor";

    @GET(URL_SUFFIX_DEMO_VISITOR)
    Call<ResponseBody> getTestVisitor(
        @Query(PARAMETER_VISITOR) int visitor
    );
}
