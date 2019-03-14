package com.webimapp.android.sdk;

import android.support.annotation.NonNull;

public interface WebimSession {

    /**
     * Resumes session networking.
     * Notice that a session is created as a paused, i.e. to start using it
     * the first thing to do is to call this method
     * @throws IllegalStateException if the WebimSession was destroyed
     * @throws RuntimeException if the method was called not from the thread the WebimSession was created in
     */
    void resume();

    /**
     * Pauses session networking.
     * @throws RuntimeException if the method was called not from the thread the WebimSession was created in
     */
    void pause();

    /**
     * Destroys the session. It is impossible to use any session methods after it was destroyed.
     * @throws RuntimeException if the method was called not from the thread the WebimSession was created in
     */
    void destroy();

    /**
     * Destroys the session, performing a cleanup. It is impossible to use any session methods after it was destroyed.
     * @throws RuntimeException if the method was called not from the thread the WebimSession was created in
     */
    void destroyWithClearVisitorData();

    /**
     * @return a {@link MessageStream} object, attached to this session. Each invocation of this method returns the same object
     */
    @NonNull MessageStream getStream();

    /**
     * Changes location without creating a new session.
     * @param location New location name.
     * */
    void changeLocation(@NonNull String location);

    /**
     * This method allows you to manually set the push token. Notice that in most cases you should NOT use this method: push token
     * retrieves automatically if the push system is enabled. This method may be useful for debugging.
     * @param pushToken push token
     * @throws IllegalStateException if the push system was not configured for this session by {@link com.webimapp.android.sdk.Webim.SessionBuilder#setPushSystem}
     * @throws IllegalStateException if the WebimSession was destroyed
     * @throws RuntimeException if the method was called not from the thread the WebimSession was created in
     * @see com.webimapp.android.sdk.Webim.SessionBuilder#setPushToken
     */
    void setPushToken(@NonNull String pushToken);
}