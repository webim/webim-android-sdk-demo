package ru.webim.android.sdk.impl;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.gson.reflect.TypeToken;

import net.sqlcipher.Cursor;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteConstraintException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;
import net.sqlcipher.database.SQLiteStatement;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ru.webim.android.sdk.Message;
import ru.webim.android.sdk.MessageTracker;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.WebimLogEntity;
import ru.webim.android.sdk.impl.backend.WebimInternalLog;
import ru.webim.android.sdk.impl.items.KeyboardItem;
import ru.webim.android.sdk.impl.items.KeyboardRequestItem;
import ru.webim.android.sdk.impl.items.MessageGroup;
import ru.webim.android.sdk.impl.items.StickerItem;

public class SQLiteHistoryStorage implements HistoryStorage {
    private static final Executor executor = new ThreadPoolExecutor(0, 1, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final Message.Type[] MESSAGE_TYPES = Message.Type.values();
    private static final String INSERT_HISTORY_STATEMENT = "INSERT OR FAIL INTO history " +
            "(client_side_id, " +
            "msg_id, " +
            "ts, " +
            "sender_id, " +
            "sender_name, " +
            "avatar, " +
            "type, " +
            "text, " +
            "data, " +
            "quote," +
            "can_be_replied," +
            "reaction," +
            "status)" +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_HISTORY_STATEMENT = "UPDATE history " +
            "SET " +
            "msg_id=?, " +
            "ts=?, " +
            "sender_id=?, " +
            "sender_name=?, " +
            "avatar=?, " +
            "type=?, " +
            "text=?, " +
            "data=?, " +
            "quote=?, " +
            "can_be_replied=?, " +
            "reaction=?," +
            "status=?" +
            "WHERE client_side_id=?";
    private static final String DELETE_HISTORY_STATEMENT = "DELETE FROM history " +
            "WHERE client_side_id=?";
    private static final String GET_SENDING_MESSAGES_QUERY = "SELECT * FROM history " +
        "WHERE status='SENDING' ORDER BY ts";
    private static final int VERSION = 14;

    private final MyDBHelper dbHelper;
    private final Handler handler;
    private final String serverUrl;
    private boolean prepared;
    private boolean isReachedEndOfRemoteHistory;
    private long firstKnownTs = -1;
    private FileUrlCreator fileUrlCreator;
    private long readBeforeTimestamp;
    private String databasePassword;
    private ReadBeforeTimestampListener readBeforeTimestampListener
            = new ReadBeforeTimestampListener() {
        @Override
        public void onTimestampChanged(long timestamp) {
            if (readBeforeTimestamp < timestamp) {
                readBeforeTimestamp = timestamp;
            }
        }
    };
    private final WebimInternalLog logger = WebimInternalLog.getInstance();

    public SQLiteHistoryStorage(Context context,
                                Handler handler,
                                String dbName,
                                String serverUrl,
                                boolean isReachedEndOfRemoteHistory,
                                FileUrlCreator fileUrlCreator,
                                long readBeforeTimestamp,
                                String databasePassword) {
        this.dbHelper = new MyDBHelper(context, dbName);
        this.handler = handler;
        this.serverUrl = serverUrl;
        this.isReachedEndOfRemoteHistory = isReachedEndOfRemoteHistory;
        this.fileUrlCreator = fileUrlCreator;
        this.readBeforeTimestamp = readBeforeTimestamp;
        this.databasePassword = databasePassword;

        SQLiteDatabase.loadLibs(context);
    }

    private void prepare() throws SQLiteException {
        if (!prepared) {
            prepared = true;
            SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword);
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
        executor.execute(() -> {
            SQLiteDatabase db;
            try {
                prepare();
                db = dbHelper.getWritableDatabase(databasePassword);
            } catch (SQLiteException exception) {
                logger.log(
                    "Unable open database. " + exception,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING,
                    WebimLogEntity.DATABASE);
                return;
            }

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
                        insertStatement.bindString(1, message.getClientSideId().toString());
                        bindMessageFields(insertStatement, 2, message);
                        insertStatement.executeInsert();

                        String beforeId = cursor.moveToNext() ? createMessage(cursor).getServerSideId() : null;
                        runMessageAdded(callback, beforeId, message);
                    } catch (SQLiteConstraintException ignored) {
                        bindMessageFields(updateStatement, 1, message);
                        updateStatement.bindString(MyDBHelper.COLUMN_COUNT, message.getClientSideId().toString());
                        updateStatement.executeUpdateDelete();
                        runMessageChanged(callback, message);
                    } catch (SQLException e) {
                        WebimInternalLog.getInstance().log(
                            "Insert failed. " + e,
                                Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING,
                            WebimLogEntity.DATABASE);
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

            handler.post(callback::endOfBatch);
            db.close();
        });
    }

    private static void bindMessageFields(SQLiteStatement statement,
                                          int index,
                                          MessageImpl message) {
        // Binding to serverSideId
        if (message.getServerSideId() != null) {
            statement.bindString(index, message.getServerSideId());
        } else {
            statement.bindNull(index);
        }

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
        Message.Attachment attachment = message.getAttachment();
        Message.Sticker sticker = message.getSticker();
        String data = message.getData();
        if (attachment != null) {
            data = InternalUtils.convertToString(attachment);
            statement.bindString((index + 7), data);
        } else if (sticker != null) {
            data = InternalUtils.convertToString(sticker);
            statement.bindString((index + 7), data);
        } else if (data != null) {
            statement.bindString((index + 7), data);
        } else {
            statement.bindNull(index + 7);
        }

        // Binding to quote
        Message.Quote quote = message.getQuote();
        if (quote != null) {
            statement.bindString((index + 8), InternalUtils.convertToString(quote));
        }

        // Binding to can_be_replied
        statement.bindLong(index + 9, message.canBeReplied() ? 1 : 0);

        // Binding reaction
        MessageReaction reaction = message.getReaction();
        if (reaction == null) {
            statement.bindNull(index + 10);
        } else {
            statement.bindString(index + 10, reaction.value);
        }

        // Binding send status
        Message.SendStatus sendStatus = message.getSendStatus();
        String statusName = sendStatus.name();
        statement.bindString(index + 11, statusName);
        System.out.println();
    }

    private static int messageTypeToId(Message.Type type) {
        return type.ordinal();
    }

    private static Message.Type idToMessageType(int id) {
        return MESSAGE_TYPES[id];
    }

    private MessageImpl createMessage(Cursor cursor) {
        String clientSideId = cursor.getString(0);
        String serverSideId = cursor.getString(1);
        long timestamp = cursor.getLong(2);
        String avatar = cursor.getString(5);
        Message.Type type = idToMessageType(cursor.getInt(6));
        String text = cursor.getString(7);
        String rawText = cursor.getString(8);
        String rawQuote = cursor.getString(9);
        boolean canBeReplied = cursor.getLong(10) == 1; // this boolean field save in db as 1 or 0
        MessageReaction reaction = cursor.isNull(11) ? null : valueToMessageReaction(cursor.getString(11));
        String status = cursor.getString(12);

        Message.Attachment attachment = null;
        if (type == Message.Type.FILE_FROM_OPERATOR || type == Message.Type.FILE_FROM_VISITOR) {
            try {
                attachment = InternalUtils.fromJson(rawText, MessageImpl.AttachmentImpl.class);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse file params for message: " + serverSideId + ", text: " + text + ". " + e,
                        Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
            }
        }

        MessageGroup.GroupData groupData = null;
        try {
            MessageGroup messageGroup = InternalUtils.fromJson(rawText, MessageGroup.class);
            if (messageGroup != null) {
                groupData = messageGroup.getGroupData();
            }
        } catch (Throwable throwable) {
            WebimInternalLog.getInstance().log(
                "Failed to parse file params for message: " + serverSideId + ", text: " + text + ". " + throwable,
                Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                WebimLogEntity.DATABASE);
        }

        Message.Quote quote = null;
        if (rawQuote != null) {
            try {
                quote = InternalUtils.fromJson(rawQuote, MessageImpl.QuoteImpl.class);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse quote params for message: " + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
            }
        }

        Message.Keyboard keyboardButton = null;
        if (type == Message.Type.KEYBOARD) {
            try {
                Type mapType = new TypeToken<KeyboardItem>() {}.getType();
                KeyboardItem keyboard = InternalUtils.getItem(rawText, true, mapType);
                keyboardButton = InternalUtils.getKeyboardButton(keyboard);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse keyboard params for message: " + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
            }
        }

        Message.KeyboardRequest keyboardRequest = null;
        if (type == Message.Type.KEYBOARD_RESPONSE) {
            try {
                Type mapType = new TypeToken<KeyboardRequestItem>() {}.getType();
                KeyboardRequestItem keyboard = InternalUtils.getItem(rawText, true, mapType);
                keyboardRequest = InternalUtils.getKeyboardRequest(keyboard);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse keyboardRequest params for message: " + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
            }
        }

        Message.Sticker sticker = null;
        if (type == Message.Type.STICKER_VISITOR) {
            try {
                Type mapType = new TypeToken<StickerItem>(){}.getType();
                StickerItem stickerItem = InternalUtils.getItem(rawText, true, mapType);
                sticker = InternalUtils.getSticker(stickerItem);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse sticker params for message: " + serverSideId + ", text: " + text + ". " + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
            }
        }

        boolean isRead = timestamp <= readBeforeTimestamp || readBeforeTimestamp == -1;

        Message.SendStatus sendStatus;
        try {
            sendStatus = Message.SendStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            sendStatus = Message.SendStatus.SENT;
        }

        switch (sendStatus) {
            case SENDING: {
                return createSendingMessage(cursor, clientSideId, timestamp, type, text, attachment, quote, sticker);
            }
            case FAILED: {
                return createFailedMessage(cursor, clientSideId, timestamp, type, text, attachment, quote, sticker);
            }
            default: {
                return createSentMessage(cursor, clientSideId, serverSideId, timestamp, avatar, type, text, rawText, canBeReplied, reaction, attachment, quote, keyboardButton, keyboardRequest, sticker, isRead, groupData, sendStatus);
            }
        }
    }

    @NonNull
    private MessageSending createSendingMessage(Cursor cursor, String clientSideId, long timestamp, Message.Type type, String text, Message.Attachment attachment, Message.Quote quote, Message.Sticker sticker) {
        return new MessageSending(
            serverUrl,
            StringId.forMessage(clientSideId),
            cursor.getString(4),
            type,
            text,
            timestamp,
            quote,
            sticker,
            attachment
        );
    }

    @NonNull
    private MessageFailed createFailedMessage(Cursor cursor, String clientSideId, long timestamp, Message.Type type, String text, Message.Attachment attachment, Message.Quote quote, Message.Sticker sticker) {
        return new MessageFailed(
            serverUrl,
            StringId.forMessage(clientSideId),
            cursor.getString(4),
            type,
            text,
            timestamp,
            quote,
            sticker,
            attachment
        );
    }

    @NonNull
    private MessageImpl createSentMessage(Cursor cursor, String clientSideId, String serverSideId, long timestamp, String avatar, Message.Type type, String text, String rawText, boolean canBeReplied, MessageReaction reaction, Message.Attachment attachment, Message.Quote quote, Message.Keyboard keyboardButton, Message.KeyboardRequest keyboardRequest, Message.Sticker sticker, boolean isRead, MessageGroup.GroupData groupData, Message.SendStatus sendStatus) {
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
            sticker,
            reaction,
            false,
            false,
            groupData,
            sendStatus
        );
    }

    private MessageReaction valueToMessageReaction(String value) {
        try {
            return MessageReaction.valueOf(value);
        } catch (IllegalArgumentException exception) {
            WebimInternalLog.getInstance().log(
                exception.getMessage(),
                Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                WebimLogEntity.DATABASE);
            return null;
        }
    }

    private void runMessageAdded(final UpdateHistoryCallback callback, final String beforeId, final MessageImpl msg) {
        handler.post(() -> callback.onHistoryAdded(beforeId, msg));
    }

    private void runMessageChanged(final UpdateHistoryCallback callback, final MessageImpl to) {
        handler.post(() -> callback.onHistoryChanged(to));
    }

    private void runMessageDeleted(final UpdateHistoryCallback callback, final String msgID) {
        handler.post(() -> callback.onHistoryDeleted(msgID));
    }

    private void runMessageList(final MessageTracker.GetMessagesCallback callback, final List<Message> messages) {
        handler.post(() -> callback.receive(messages));
    }

    @Override
    public void receiveHistoryBefore(@NonNull final List<? extends MessageImpl> messages,
                                     final boolean hasMore) {
        executor.execute(() -> {
            SQLiteDatabase db;
            try {
                prepare();
                db = dbHelper.getWritableDatabase(databasePassword);
            } catch (SQLiteException exception) {
                logger.log(
                    "Unable open database. " + exception,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR,
                    WebimLogEntity.DATABASE);
                return;
            }

            SQLiteStatement insertStatement = db.compileStatement(INSERT_HISTORY_STATEMENT);
            long newFirstKnownTs = Long.MAX_VALUE;
            for (MessageImpl message : messages) {
                if (message != null) {
                    newFirstKnownTs = Math.min(newFirstKnownTs,
                        message.getTimeMicros());
                    try {
                        insertStatement.bindString(1, message.getClientSideId().toString());
                        bindMessageFields(insertStatement, 2, message);
                        insertStatement.executeInsert();
                    } catch (SQLException e) {
                        WebimInternalLog.getInstance().log("Insert failed. " + e,
                            Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING,
                            WebimLogEntity.DATABASE);
                    }
                }
            }

            insertStatement.close();

            if (newFirstKnownTs != Long.MAX_VALUE) {
                firstKnownTs = newFirstKnownTs;
            }
            db.close();
        });
    }

    @Override
    public void getLatest(final int limit,
                          @NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(() -> {
            List<Message> list = new ArrayList<>();
            try(SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword);
                Cursor c = db.rawQuery(
                    "SELECT * FROM history ORDER BY ts DESC LIMIT ?",
                    new String[]{Integer.toString(limit)})
            ) {
                while (c.moveToNext()) {
                    list.add(createMessage(c));
                }
            }
            Collections.reverse(list);
            runMessageList(callback, list);
        });
    }

    @Override
    public void getFull(@NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(() -> {
            List<Message> messageList = new ArrayList<>();
            try (SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword);
                 Cursor cursor = db.rawQuery("SELECT * FROM history ORDER BY ts ASC", new String[]{})) {
                while (cursor.moveToNext()) {
                    messageList.add(createMessage(cursor));
                }
            }
            runMessageList(callback, messageList);
        });
    }

    @Override
    public void getBefore(long beforeTimestamp,
                          final int limit,
                          @NonNull final MessageTracker.GetMessagesCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<Message> list = new ArrayList<>();
                try (SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword);
                     Cursor c = db.rawQuery(
                         "SELECT * FROM history WHERE ts < ? ORDER BY ts DESC LIMIT ?",
                         new String[]{Long.toString(beforeTimestamp), Integer.toString(limit)})
                ) {
                    while (c.moveToNext()) {
                        list.add(createMessage(c));
                    }
                }
                Collections.reverse(list);
                runMessageList(callback, list);
            }
        });
    }

    @Override
    public void getSending(@NonNull MessageTracker.GetMessagesCallback callback) {
        executor.execute(() -> {
            List<Message> list = new ArrayList<>();
            try (SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword);
                 Cursor cursor = db.rawQuery(
                     GET_SENDING_MESSAGES_QUERY,
                     new String[] {}
                 )
            ) {
                while (cursor.moveToNext()) {
                    list.add(createMessage(cursor));
                }
            }
            runMessageList(callback, list);
        });
    }

    @Override
    public void clearHistory() {
        executor.execute(() -> {
            try (SQLiteDatabase db = dbHelper.getWritableDatabase(databasePassword)) {
                db.execSQL("delete from history");
            } catch (SQLException exception) {
                WebimInternalLog.getInstance().log(
                    "Failed to delete from database " + exception,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING,
                    WebimLogEntity.DATABASE
                );
            }
        });
    }

    @Override
    public ReadBeforeTimestampListener getReadBeforeTimestampListener() {
        return readBeforeTimestampListener;
    }

    private static class MyDBHelper extends SQLiteOpenHelper {
        private static final int COLUMN_COUNT = 13;
        private static final String CREATE_TABLE = "CREATE TABLE history\n"
                + "(\n"
                + "    client_side_id VARCHAR(64) PRIMARY KEY NOT NULL,\n"
                + "    msg_id VARCHAR(64),\n"
                + "    ts BIGINT  NOT NULL,\n"
                + "    sender_id VARCHAR(64),\n"
                + "    sender_name VARCHAR(255) NOT NULL,\n"
                + "    avatar VARCHAR(255),\n"
                + "    type TINYINT NOT NULL,\n"
                + "    text TEXT NOT NULL,\n"
                + "    data TEXT,\n"
                + "    quote TEXT,\n"
                + "    can_be_replied TINYINT NOT NULL,\n"
                + "    reaction VARCHAR(32),\n"
                + "    status VARCHAR(32)"
                + "); CREATE UNIQUE INDEX history_ts ON history (ts)";
        private static final SQLiteDatabaseHook mHook = new SQLiteDatabaseHook() {
            public void preKey(SQLiteDatabase database) {
            }

            public void postKey(SQLiteDatabase database) {
                database.rawExecSQL("PRAGMA cipher_compatibility=3;");
                database.rawExecSQL("PRAGMA kdf_iter=1000;");
                database.rawExecSQL("PRAGMA cipher_default_kdf_iter=1000;");
                database.rawExecSQL("PRAGMA cipher_page_size = 4096;");
            }
        };

        public MyDBHelper(Context context, String dbName) {
            super(context, dbName, null, VERSION, mHook);
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
