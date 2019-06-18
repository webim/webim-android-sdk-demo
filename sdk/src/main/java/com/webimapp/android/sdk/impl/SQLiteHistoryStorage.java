package com.webimapp.android.sdk.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.google.gson.reflect.TypeToken;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.impl.backend.WebimClient;
import com.webimapp.android.sdk.impl.backend.WebimInternalLog;
import com.webimapp.android.sdk.impl.items.KeyboardItem;
import com.webimapp.android.sdk.impl.items.KeyboardRequestItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SQLiteHistoryStorage implements HistoryStorage {
    private static final Executor executor = new ThreadPoolExecutor(0, 1, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final Message.Type[] MESSAGE_TYPES = Message.Type.values();
    private static final String INSERT_HISTORY_STATEMENT = "INSERT OR FAIL INTO history " +
            "(msg_id, " +
            "client_side_id, " +
            "ts, " +
            "sender_id, " +
            "sender_name, " +
            "avatar, " +
            "type, " +
            "text, " +
            "data) " +
            "VALUES (?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_HISTORY_STATEMENT = "UPDATE history " +
            "SET " +
            "client_side_id=?, " +
            "ts=?, " +
            "sender_id=?, " +
            "sender_name=?, " +
            "avatar=?, " +
            "type=?, " +
            "text=?, " +
            "data=? " +
            "WHERE msg_id=?";
    private static final String DELETE_HISTORY_STATEMENT = "DELETE FROM history " +
            "WHERE msg_id=?";
    private static final int VERSION = 4;

    private final MyDBHelper dbHelper;
    private final Handler handler;
    private final String serverUrl;
    private boolean prepared;
    private boolean isReachedEndOfRemoteHistory;
    private long firstKnownTs = -1;
    private long readBeforeTimestamp;
    private WebimClient client;
    private ReadBeforeTimestampListener readBeforeTimestampListener
            = new ReadBeforeTimestampListener() {
        @Override
        public void onTimestampChanged(long timestamp) {
            if (readBeforeTimestamp < timestamp) {
                readBeforeTimestamp = timestamp;
            }
        }
    };

    public SQLiteHistoryStorage(Context context,
                                Handler handler,
                                String dbName,
                                String serverUrl,
                                boolean isReachedEndOfRemoteHistory,
                                WebimClient client,
                                long readBeforeTimestamp) {
        this.dbHelper = new MyDBHelper(context, dbName);
        this.handler = handler;
        this.serverUrl = serverUrl;
        this.isReachedEndOfRemoteHistory = isReachedEndOfRemoteHistory;
        this.client = client;
        this.readBeforeTimestamp = readBeforeTimestamp;
    }

    private void prepare() {
        if (!prepared) {
            prepared = true;
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            Cursor c = db.rawQuery(
                    "SELECT ts FROM HISTORY ORDER BY ts ASC LIMIT 1",
                    new String[0]);
            try {
                if (c.moveToNext()) {
                    firstKnownTs = c.getLong(c.getColumnIndex("ts"));
                }
            } finally {
                c.close();
                db.close();
            }
        }
    }

    @Override
    public int getMajorVersion() {
        return VERSION;
    }

    @Override
    public void setReachedEndOfRemoteHistory(boolean isReachedEndOfRemoteHistory) {
        this.isReachedEndOfRemoteHistory = isReachedEndOfRemoteHistory;
    }

    @Override
    public void receiveHistoryUpdate(@NonNull final List<? extends MessageImpl> messages,
                                     @NonNull final Set<String> deleted,
                                     @NonNull final UpdateHistoryCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                prepare();
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                SQLiteStatement insertStatement = db.compileStatement(INSERT_HISTORY_STATEMENT);
                SQLiteStatement updateStatement = db.compileStatement(UPDATE_HISTORY_STATEMENT);
                long newFirstKnownTs = Long.MAX_VALUE;
                for (MessageImpl message : messages) {
                    if (message != null) {
                        HistoryId historyId = message.getHistoryId();
                        if (firstKnownTs != -1
                                && historyId.getTimeMicros() < firstKnownTs
                                && !isReachedEndOfRemoteHistory) {
                            continue;
                        }

                        newFirstKnownTs = Math.min(newFirstKnownTs, historyId.getTimeMicros());
                        Cursor cursor = db.rawQuery(
                                "SELECT * FROM history WHERE ts > ? ORDER BY ts ASC LIMIT 1",
                                new String[]{Long.toString(message.getTimeMicros())});
                        try {
                            insertStatement.bindString(1, message.getId().toString());
                            bindMessageFields(insertStatement, 2, message);
                            insertStatement.executeInsert();

                            runMessageAdded(callback,
                                    cursor.moveToNext() ? createMessage(cursor).getHistoryId() : null,
                                    message);
                        } catch (SQLiteConstraintException ignored) {
                            bindMessageFields(updateStatement, 1, message);
                            updateStatement.bindString(9, message.getId().toString()/*historyId.getDbId()*/);
                            updateStatement.executeUpdateDelete();
                            runMessageChanged(callback, message);
                        } catch (SQLException e) {
                            WebimInternalLog.getInstance().log("Insert failed. " + e,
                                    Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING);
                        } finally {
                            insertStatement.clearBindings();
                            updateStatement.clearBindings();
                            cursor.close();
                        }
                    }
                }
                insertStatement.close();
                updateStatement.close();

                SQLiteStatement deleteStatement = db.compileStatement(DELETE_HISTORY_STATEMENT);
                for (String id : deleted) {
                    deleteStatement.bindString(1, id);
                    deleteStatement.executeUpdateDelete();
                    runMessageDeleted(callback, id);
                    deleteStatement.clearBindings();
                }
                deleteStatement.close();

                if (firstKnownTs == -1 && newFirstKnownTs != Long.MAX_VALUE) {
                    firstKnownTs = newFirstKnownTs;
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.endOfBatch();
                    }
                });
                db.close();
            }
        });
    }

    private static void bindMessageFields(SQLiteStatement statement,
                                          int index,
                                          MessageImpl message) {
        // Binding to msg_id / client_side_id
        statement.bindString(index, message.getId().toString());

        // Binding to ts
        statement.bindLong((index + 1), message.getHistoryId().getTimeMicros());

        // Binding to sender_id
        if ((message.getOperatorId() == null) || (message.getOperatorId().toString() == null)) {
            statement.bindNull(index + 2);
        } else {
            statement.bindString((index + 2), message.getOperatorId().toString());
        }

        // Binding to sender_name
        statement.bindString((index + 3), message.getSenderName());

        // Binding to avatar
        if (message.getSenderAvatarUrl() == null) {
            statement.bindNull(index + 4);
        } else {
            statement.bindString((index + 4), message.getAvatarUrlLastPart());
        }

        // Binding to type
        statement.bindLong((index + 5), messageTypeToId(message.getType()));

        // Binding to text
        statement.bindString((index + 6),
                (message.getRawText() != null) ? message.getRawText() : message.getText());

        // Binding to data
        String data = message.getData();
        if (data == null) {
            statement.bindNull(index + 7);
        } else {
            statement.bindString((index + 7), data);
        }
    }

    private static int messageTypeToId(Message.Type type) {
        return type.ordinal();
    }

    private static Message.Type idToMessageType(int id) {
        return MESSAGE_TYPES[id];
    }

    private MessageImpl createMessage(Cursor cursor) {
        String id = cursor.getString(0);
        String clientSideId = cursor.getString(1);
        long ts = cursor.getLong(2);
        String avatar = cursor.getString(5);
        Message.Type type = idToMessageType(cursor.getInt(6));
        String text = cursor.getString(7);

        String data = cursor.getString(8);

        Message.Attachment attachment = null;
        String rawText;

        if ((type == Message.Type.FILE_FROM_OPERATOR)
                || (type == Message.Type.FILE_FROM_VISITOR)) {
            rawText = text;
            text = "";
        } else {
            rawText = null;
        }

        if (rawText != null) {
            try {
                attachment = InternalUtils.getAttachment(serverUrl, rawText, client);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse file params for message: "
                        + id + ", text: " + text + ". " + e,
                        Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        Message.Keyboard keyboardButton = null;
        if (type == Message.Type.KEYBOARD) {
            Type mapType = new TypeToken<KeyboardItem>() {}.getType();
            KeyboardItem keyboard = InternalUtils.getKeyboard(data, true, mapType);
            keyboardButton = InternalUtils.getKeyboardButton(keyboard);
        }

        Message.KeyboardRequest keyboardRequest = null;
        if (type == Message.Type.KEYBOARD_RESPONCE) {
            Type mapType = new TypeToken<KeyboardRequestItem>() {}.getType();
            KeyboardRequestItem keyboard = InternalUtils.getKeyboard(data, true, mapType);
            keyboardRequest = InternalUtils.getKeyboardRequest(keyboard);
        }

        boolean isRead = ts <= readBeforeTimestamp || readBeforeTimestamp == -1;

        return new MessageImpl(serverUrl,
                StringId.forMessage(clientSideId != null
                        ? clientSideId
                        : id),
                cursor.isNull(3)
                        ? null
                        : StringId.forOperator(Long.toString(cursor.getLong(3))),
                avatar,
                cursor.getString(4),
                type,
                text,
                ts,
                attachment,
                id,
                rawText,
                true,
                data,
                isRead,
                false,
                keyboardButton,
                keyboardRequest);
    }

    private void runMessageAdded(final UpdateHistoryCallback callback, final HistoryId before, final MessageImpl msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onHistoryAdded(before, msg);
            }
        });
    }

    private void runMessageChanged(final UpdateHistoryCallback callback, final MessageImpl to) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onHistoryChanged(to);
            }
        });
    }

    private void runMessageDeleted(final UpdateHistoryCallback callback, final String msgID) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onHistoryDeleted(msgID);
            }
        });
    }

    private void runMessageList(final MessageTracker.GetMessagesCallback callback, final List<Message> messages) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.receive(messages);
            }
        });
    }

    @Override
    public void receiveHistoryBefore(@NonNull final List<? extends MessageImpl> messages,
                                     final boolean hasMore) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                prepare();

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                SQLiteStatement insertStatement = db.compileStatement("INSERT OR FAIL " +
                        "INTO history " +
                        "(msg_id, " +
                        "ts, " +
                        "sender_id, " +
                        "sender_name, " +
                        "avatar, " +
                        "type, " +
                        "text, " +
                        "data) " +
                        "VALUES " +
                        "(?, ?, ?, ?, ?, ?, ?, ?)");

                long newFirstKnownTs = Long.MAX_VALUE;
                for (MessageImpl message : messages) {
                    if (message != null) {
                        newFirstKnownTs = Math.min(newFirstKnownTs,
                                message.getHistoryId().getTimeMicros());
                        bindMessageFields(insertStatement, 1, message);
                        try {
                            insertStatement.executeInsert();
                        } catch (SQLException e) {
                            WebimInternalLog.getInstance().log("Insert failed. " + e,
                                    Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING);
                        }
                    }
                }

                insertStatement.close();

                if (newFirstKnownTs != Long.MAX_VALUE) {
                    firstKnownTs = newFirstKnownTs;
                }
                db.close();
            }
        });
    }

    @Override
    public void getLatest(final int limit,
                          @NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor c = db.rawQuery("SELECT * FROM history ORDER BY ts DESC LIMIT ?",
                        new String[]{Integer.toString(limit)});
                List<Message> list = new ArrayList<>();
                try {
                    while (c.moveToNext()) {
                        list.add(createMessage(c));
                    }
                } finally {
                    c.close();
                    db.close();
                }
                Collections.reverse(list);
                runMessageList(callback, list);
            }
        });
    }

    @Override
    public void getFull(@NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor cursor = db.rawQuery(
                        "SELECT * FROM history ORDER BY ts ASC",
                        new String[]{});
                List<Message> messageList = new ArrayList<>();
                try {
                    while (cursor.moveToNext()) {
                        messageList.add(createMessage(cursor));
                    }
                } finally {
                    cursor.close();
                    db.close();
                }
                runMessageList(callback, messageList);
            }
        });
    }

    @Override
    public void getBefore(@NonNull final HistoryId before,
                          final int limit,
                          @NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor c = db.rawQuery("SELECT * FROM history WHERE ts < ? ORDER BY ts DESC LIMIT ?",
                        new String[]{Long.toString(before.getTimeMicros()), Integer.toString(limit)});
                List<Message> list = new ArrayList<>();
                try {
                    while (c.moveToNext()) {
                        list.add(createMessage(c));
                    }
                } finally {
                    c.close();
                    db.close();
                }
                Collections.reverse(list);
                runMessageList(callback, list);
            }
        });
    }

    @Override
    public ReadBeforeTimestampListener getReadBeforeTimestampListener() {
        return readBeforeTimestampListener;
    }

    private static class MyDBHelper extends SQLiteOpenHelper {

        private static final String CREATE_TABLE = "CREATE TABLE history\n"
                + "(\n"
                + "    msg_id VARCHAR(64) PRIMARY KEY NOT NULL,\n"
                + "    client_side_id VARCHAR(64),\n"
                + "    ts BIGINT NOT NULL,\n"
                + "    sender_id VARCHAR(64),\n"
                + "    sender_name VARCHAR(255) NOT NULL,\n"
                + "    avatar VARCHAR(255),\n"
                + "    type TINYINT NOT NULL,\n"
                + "    text TEXT NOT NULL,\n"
                + "    data TEXT\n"
                + "); CREATE UNIQUE INDEX history_ts ON history (ts)";

        public MyDBHelper(Context context, String dbName) {
            super(context, dbName, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                db.execSQL("DROP TABLE history");
                onCreate(db);
            }
        }
    }
}
