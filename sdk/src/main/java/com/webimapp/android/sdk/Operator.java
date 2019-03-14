package com.webimapp.android.sdk;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Abstracts a chat operator
 * @see MessageStream#getCurrentOperator()
 */
public interface Operator {

    /**
     * @return the unique id of the operator
     * @see MessageStream#rateOperator
     * @see MessageStream#getLastOperatorRating
     */
    @NonNull Operator.Id getId();

    /**
     * @return a display name of the operator
     */
    @NonNull String getName();

    /**
     * @return a URL of the operator’s avatar
     */
    @Nullable String getAvatarUrl();

    /**
     * Abstracts the unique id of the operator. The class was designed only to be compared to 'equals'.
     */
    interface Id {
    }
}
