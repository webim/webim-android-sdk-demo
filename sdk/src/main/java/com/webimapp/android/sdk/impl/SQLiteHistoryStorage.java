package com.webimapp.android.sdk.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import androidx.annotation.NonNull;

import com.google.gson.reflect.TypeToken;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.impl.backend.WebimClient;
import com.webimapp.android.sdk.impl.backend.WebimInternalLog;
import com.webimapp.android.sdk.impl.items.KeyboardItem;
import com.webimapp.android.sdk.impl.items.KeyboardRequestItem;
import com.webimapp.android.sdk.impl.items.MessageItem;
import com.webimapp.android.sdk.impl.items.StickerItem;

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
            "data, " +
            "quote," +
            "can_be_replied) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_HISTORY_STATEMENT = "UPDATE history " +
            "SET " +
            "client_side_id=?, " +
            "ts=?, " +
            "sender_id=?, " +
            "sender_name=?, " +
            "avatar=?, " +
            "type=?, " +
            "text=?, " +
            "data=?, " +
            "quote=?, " +
            "can_be_replied=? " +
            "WHERE msg_id=?";
    private static final String DELETE_HISTORY_STATEMENT = "DELETE FROM history " +
            "WHERE msg_id=?";
    private static final int VERSION = 11;

    private final MyDBHelper dbHelper;
    private final Handler handler;
    private final String serverUrl;
    private boolean prepared;
    private boolean isReachedEndOfRemoteHistory;
    private long firstKnownTs = -1;
    private long readBeforeTimestamp;
    private FileUrlCreator fileUrlCreator;
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
                                FileUrlCreator fileUrlCreator,
                                long readBeforeTimestamp) {
        this.dbHelper = new MyDBHelper(context, dbName);
        this.handler = handler;
        this.serverUrl = serverUrl;
        this.isReachedEndOfRemoteHistory = isReachedEndOfRemoteHistory;
        this.fileUrlCreator = fileUrlCreator;
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
                        if (firstKnownTs != -1
                                && message.getTimeMicros() < firstKnownTs
                                && !isReachedEndOfRemoteHistory) {
                            continue;
                        }

                        newFirstKnownTs = Math.min(newFirstKnownTs, message.getTimeMicros());
                        Cursor cursor = db.rawQuery(
                                "SELECT * FROM history WHERE ts > ? ORDER BY ts ASC LIMIT 1",
                                new String[]{Long.toString(message.getTimeMicros())});
                        try {
                            insertStatement.bindString(1, message.getServerSideId());
                            bindMessageFields(insertStatement, 2, message);
                            insertStatement.executeInsert();

                            String beforeId = cursor.moveToNext() ? createMessage(cursor).getServerSideId() : null;
                            runMessageAdded(callback, beforeId, message);
                        } catch (SQLiteConstraintException ignored) {
                            bindMessageFields(updateStatement, 1, message);
                            updateStatement.bindString(MyDBHelper.COLUMN_COUNT, message.getServerSideId());
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
        // Binding to client_side_id
        statement.bindString(index, message.getClientSideId().toString());

        // Binding to ts
        statement.bindLong((index + 1), message.getTimeMicros());

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
        statement.bindString((index + 6), message.getText());

        // Binding to data
        String data = message.getData();
        if (data == null) {
            statement.bindNull(index + 7);
        } else {
            statement.bindString((index + 7), data);
        }

        // Binding to quote
        Message.Quote quote = message.getQuote();
        if (quote != null) {
            statement.bindString((index + 8), data);
        }

        // Binding to can_be_replied
        statement.bindLong(index + 9, message.canBeReplied() ? 1 : 0);
    }

    private static int messageTypeToId(Message.Type type) {
        return type.ordinal();
    }

    private static Message.Type idToMessageType(int id) {
        return MESSAGE_TYPES[id];
    }

    private MessageImpl createMessage(Cursor cursor) {
        String serverSideId = cursor.getString(0);
        String clientSideId = cursor.getString(1);
        long timestamp = cursor.getLong(2);
        String avatar = cursor.getString(5);
        Message.Type type = idToMessageType(cursor.getInt(6));
        String text = cursor.getString(7);
        String rawText = cursor.getString(8);
        String rawQuote = cursor.getString(9);
        boolean canBeReplied = cursor.getLong(10) == 1; // this boolean field save in db as 1 or 0

        Message.Attachment attachment = null;
        if (rawText != null) {
            try {
                MessageItem messageItem = InternalUtils.fromJson(rawText, MessageItem.class);
                attachment = InternalUtils.getAttachment(messageItem, fileUrlCreator);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse file params for message: "
                        + serverSideId + ", text: " + text + ". " + e,
                        Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        Message.Quote quote = null;
        if (rawQuote != null) {
            try {
                MessageItem.Quote quoteParams = InternalUtils.fromJson(rawQuote, MessageItem.Quote.class);
                quote = InternalUtils.getQuote(quoteParams, fileUrlCreator);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse quote params for message: "
                        + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        Message.Keyboard keyboardButton = null;
        if (type == Message.Type.KEYBOARD) {
            try {
                Type mapType = new TypeToken<KeyboardItem>() {}.getType();
                KeyboardItem keyboard = InternalUtils.getItem(rawText, true, mapType);
                keyboardButton = InternalUtils.getKeyboardButton(keyboard);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse keyboard params for message: "
                        + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        Message.KeyboardRequest keyboardRequest = null;
        if (type == Message.Type.KEYBOARD_RESPONSE) {
            try {
                Type mapType = new TypeToken<KeyboardRequestItem>() {}.getType();
                KeyboardRequestItem keyboard = InternalUtils.getItem(rawText, true, mapType);
                keyboardRequest = InternalUtils.getKeyboardRequest(keyboard);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse keyboardRequest params for message: "
                        + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        Message.Sticker sticker = null;
        if (type == Message.Type.STICKER_VISITOR) {
            try {
                Type mapType = new TypeToken<StickerItem>(){}.getType();
                StickerItem stickerItem = InternalUtils.getItem(rawText, true, mapType);
                sticker = InternalUtils.getSticker(stickerItem);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log("Failed to parse sticker params for message: "
                        + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
            }
        }

        boolean isRead = timestamp <= readBeforeTimestamp || readBeforeTimestamp == -1;

        return new MessageImpl(
                serverUrl,
                StringId.forMessage(clientSideId),
                null,
                cursor.isNull(3)
                        ? null
                        : StringId.forOperator(Long.toString(cursor.getLong(3))),
                avatar,
                cursor.getString(4),
                type,
                text,
                timestamp,
                serverSideId,
                rawText,
                true,
                attachment,
                isRead,
                false,
                canBeReplied,
                false,
                quote,
                keyboardButton,
                keyboardRequest,
                sticker);
    }

    private void runMessageAdded(final UpdateHistoryCallback callback, final String beforeId, final MessageImpl msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onHistoryAdded(beforeId, msg);
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
                SQLiteStatement insertStatement = db.compileStatement(INSERT_HISTORY_STATEMENT);

                long newFirstKnownTs = Long.MAX_VALUE;
                for (MessageImpl message : messages) {
                    if (message != null) {
                        newFirstKnownTs = Math.min(newFirstKnownTs,
                                message.getTimeMicros());
                        try {
                            insertStatement.bindString(1, message.getServerSideId());
                            bindMessageFields(insertStatement, 2, message);
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
    public void getBefore(@NonNull MessageImpl msg,
                          final int limit,
                          @NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor c = db.rawQuery("SELECT * FROM history WHERE ts < ? ORDER BY ts DESC LIMIT ?",
                        new String[]{Long.toString(msg.timeMicros), Integer.toString(limit)});
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
        private static final int COLUMN_COUNT = 11;
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
                + "    data TEXT,\n"
                + "    quote TEXT,\n"
                + "    can_be_replied TINYINT NOT NULL\n"
                + "); CREATE UNIQUE INDEX history_ts ON history (ts)";

        public MyDBHelper(Context context, String dbName) {
            super(context, dbName, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            db.enableWriteAheadLogging();
            super.onOpen(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                recreateTable(db);
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                recreateTable(db);
            }
        }

        private void recreateTable(SQLiteDatabase db) {
            db.execSQL("DROP TABLE history");
            onCreate(db);
        }
    }
}
