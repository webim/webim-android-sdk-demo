package com.webimapp.android.sdk.impl.backend;

import com.webimapp.android.sdk.impl.items.responses.DefaultResponse;
import com.webimapp.android.sdk.impl.items.responses.DeltaResponse;
import com.webimapp.android.sdk.impl.items.responses.HistoryBeforeResponse;
import com.webimapp.android.sdk.impl.items.responses.HistorySinceResponse;
import com.webimapp.android.sdk.impl.items.responses.UploadResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface WebimService {

    String PARAMETER_ACTION = "action";
    String PARAMETER_APP_VERSION = "app-version";
    String PARAMETER_AUTHORIZATION_TOKEN = "auth-token";
    String PARAMETER_BUTTON_ID = "button-id";
    String PARAMETER_CHAT_DEPARTMENT_KEY = "department-key";
    String PARAMETER_CHAT_FIRST_QUESTION = "first-question";
    String PARAMETER_CHAT_FORCE_ONLINE = "force-online";
    String PARAMETER_CHAT_MODE = "chat-mode";
    String PARAMETER_CLIENT_SIDE_ID = "client-side-id";
    String PARAMETER_CUSTOM_FIELDS = "custom_fields";
    String PARAMETER_DATA = "data";
    String PARAMETER_DEVICE_ID = "device-id";
    String PARAMETER_EVENT = "event";
    String PARAMETER_FILE_UPLOAD = "webim_upload_file";
    String PARAMETER_LOCATION = "location";
    String PARAMETER_MESSAGE = "message";
    String PARAMETER_MESSAGE_DRAFT = "message-draft";
    String PARAMETER_MESSAGE_DRAFT_DELETE = "del-message-draft";
    String PARAMETER_MESSAGE_HINT_QUESTION = "hint_question";
    String PARAMETER_OPERATOR_ID = "operator_id";
    String PARAMETER_OPERATOR_RATING = "rate";
    String PARAMETER_PAGE_ID = "page-id";
    String PARAMETER_PLATFORM = "platform";
    String PARAMETER_PRECHAT_KEY_INDEPENDENT_FIELDS = "prechat-key-independent-fields";
    String PARAMETER_PROVIDED_AUTHORIZATION_TOKEN = "provided_auth_token";
    String PARAMETER_PUSH_SERVICE = "push-service";
    String PARAMETER_PUSH_TOKEN = "push-token";
    String PARAMETER_RESPOND_IMMEDIATELY = "respond-immediately";
    String PARAMETER_REQUEST_MESSAGE_ID = "request-message-id";
    String PARAMETER_SINCE = "since";
    String PARAMETER_TIMESTAMP = "ts";
    String PARAMETER_TIMESTAMP_BEFORE = "before-ts";
    String PARAMETER_TITLE = "title";
    String PARAMETER_VISIT_SESSION_ID = "visit-session-id";
    String PARAMETER_VISITOR_EXT = "visitor-ext";
    String PARAMETER_VISITOR_FIELDS = "visitor";
    String PARAMETER_VISITOR_TYPING = "typing";
    String URL_SUFFIX_ACTION = "/l/v/m/action";
    String URL_SUFFIX_DELTA = "/l/v/m/delta";
    String URL_SUFFIX_FILE_UPLOAD = "/l/v/m/upload";
    String URL_SUFFIX_HISTORY = "/l/v/m/history";

    @Multipart
    @POST(URL_SUFFIX_FILE_UPLOAD)
    Call<UploadResponse> uploadFile(
            @Part(/*PARAMETER_FILE_UPLOAD*/) MultipartBody.Part file,
            @Part(PARAMETER_CHAT_MODE) RequestBody chatMode,
            @Part(PARAMETER_CLIENT_SIDE_ID) RequestBody clientSideId,
            @Part(PARAMETER_PAGE_ID) RequestBody pageId,
            @Part(PARAMETER_AUTHORIZATION_TOKEN) RequestBody authorizationToken
    );

    @GET(URL_SUFFIX_DELTA)
    Call<DeltaResponse> getDelta(
            @Query(PARAMETER_SINCE) long since,
            @Query(PARAMETER_PAGE_ID) String pageId,
            @Query(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Query(PARAMETER_TIMESTAMP) long timestamp
    );

    @GET(URL_SUFFIX_DELTA)
    Call<DeltaResponse> getLogin(
            @Query(PARAMETER_EVENT) String event,
            @Query(PARAMETER_PUSH_SERVICE) String pushService,
            @Query(PARAMETER_PUSH_TOKEN) String pushToken,
            @Query(PARAMETER_PLATFORM) String platform,
            @Query(PARAMETER_VISITOR_EXT) String visitorExtJsonString,
            @Query(PARAMETER_VISITOR_FIELDS) String visitorFieldsJsonString,
            @Query(PARAMETER_PROVIDED_AUTHORIZATION_TOKEN) String providedAuthorizationToken,
            @Query(PARAMETER_LOCATION) String location,
            @Query(PARAMETER_APP_VERSION) String appVersion,
            @Query(PARAMETER_VISIT_SESSION_ID) String visitSessionId,
            @Query(PARAMETER_TITLE) String title,
            @Query(PARAMETER_SINCE) long since,
            @Query(PARAMETER_RESPOND_IMMEDIATELY) boolean isToRespondImmediately,
            @Query(PARAMETER_DEVICE_ID) String deviceId,
            @Query(PARAMETER_PRECHAT_KEY_INDEPENDENT_FIELDS) String prechatFields
    );

    @GET(URL_SUFFIX_HISTORY)
    Call<HistoryBeforeResponse> getHistoryBefore(
            @Query(PARAMETER_PAGE_ID) String pageId,
            @Query(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Query(PARAMETER_TIMESTAMP_BEFORE) long timestampBefore // ms
    );

    @GET(URL_SUFFIX_HISTORY)
    Call<HistorySinceResponse> getHistorySince(
            @Query(PARAMETER_PAGE_ID) String pageId,
            @Query(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Query(PARAMETER_SINCE) String since
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> sendMessage(
            @Field(PARAMETER_ACTION) String action,
            @Field(value = PARAMETER_MESSAGE, encoded = true) String message,
            @Field(PARAMETER_CLIENT_SIDE_ID) String clientSideId,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Field(PARAMETER_MESSAGE_HINT_QUESTION) Boolean isHintQuestion,
            @Field(PARAMETER_DATA) String dataJsonString
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> sendKeyboardResponse(
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_REQUEST_MESSAGE_ID) String requestMessageId,
            @Field(PARAMETER_BUTTON_ID) String buttonId
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> deleteMessage(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_CLIENT_SIDE_ID) String clientSideId,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> closeChat(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> startChat(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_CHAT_FORCE_ONLINE) Boolean isForceOnline,
            @Field(PARAMETER_CLIENT_SIDE_ID) String clientSideId,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Field(PARAMETER_CHAT_DEPARTMENT_KEY) String departmentKey,
            @Field(PARAMETER_CHAT_FIRST_QUESTION) String firstQuestion,
            @Field(PARAMETER_CUSTOM_FIELDS) String customFields
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> setChatRead(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> rateOperator(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_OPERATOR_ID) String operatorId,
            @Field(PARAMETER_OPERATOR_RATING) int rating,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> respondSentryCall(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken,
            @Field(PARAMETER_CLIENT_SIDE_ID) String clientSideId
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> setPrechatFields(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_PRECHAT_KEY_INDEPENDENT_FIELDS) String prechatFields,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> setVisitorTyping(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_VISITOR_TYPING) boolean isVisitorTyping,
            @Field(PARAMETER_MESSAGE_DRAFT) String messageDraft,
            @Field(PARAMETER_MESSAGE_DRAFT_DELETE) boolean isToDeleteDraft,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> updatePushToken(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_PUSH_TOKEN) String pushToken,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_ACTION)
    Call<DefaultResponse> updateWidgetStatus(
            @Field(PARAMETER_ACTION) String action,
            @Field(PARAMETER_DATA) String dataJsonString,
            @Field(PARAMETER_PAGE_ID) String pageId,
            @Field(PARAMETER_AUTHORIZATION_TOKEN) String authorizationToken
    );
}
