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

import com.google.gson.Gson;
import com.webimapp.android.sdk.FAQ;
import com.webimapp.android.sdk.FAQCategory;
import com.webimapp.android.sdk.impl.items.FAQCategoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FAQSQLiteHistoryStorage {
    private static final Executor executor = new ThreadPoolExecutor(0, 1, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final String INSERT_HISTORY_STATEMENT = "INSERT OR FAIL INTO categories" +
            "(id, " +
            "data) " +
            "VALUES (?,?)";
    private static final String UPDATE_HISTORY_STATEMENT = "UPDATE categories " +
            "SET " +
            "data=? " +
            "WHERE id=?";
    private static final int VERSION = 1;

    private final FAQSQLiteHistoryStorage.MyDBHelper dbHelper;
    private final Handler handler;

    public FAQSQLiteHistoryStorage(Context context,
                                   Handler handler,
                                   String dbName) {
        this.dbHelper = new FAQSQLiteHistoryStorage.MyDBHelper(context, dbName);
        this.handler = handler;
    }

    public void insertStructure(final int id, final FAQCategoryItem category) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String data = new Gson().toJson(category);
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                SQLiteStatement insertStatement = db.compileStatement(INSERT_HISTORY_STATEMENT);
                SQLiteStatement updateStatement = db.compileStatement(UPDATE_HISTORY_STATEMENT);
                try {
                    insertStatement.bindLong(1, (long) id);
                    insertStatement.bindString(2, data);
                    insertStatement.executeInsert();

                } catch (SQLiteConstraintException ignored) {
                    updateStatement.bindLong(1, (long) id);
                    updateStatement.bindString(2, data);
                    updateStatement.executeUpdateDelete();
                } catch (SQLException ignored) {
                } finally {
                    insertStatement.clearBindings();
                    updateStatement.clearBindings();
                }

                insertStatement.close();
                updateStatement.close();

                db.close();
            }
        });
    }

    public void getCategory(final int id,
                            @NonNull final FAQ.GetCallback<FAQCategory> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Cursor cursor
                        = db.rawQuery("SELECT data FROM categories WHERE id == " + id + " LIMIT 1",
                        new String[0]);
                List<FAQCategory> list = new ArrayList<>();
                try {
                    while (cursor.moveToNext()) {
                        list.add(toCategory(cursor));
                    }
                } finally {
                    cursor.close();
                    db.close();
                }
                runCallback(callback, list.get(0));
            }
        });
    }

    private void runCallback(final FAQ.GetCallback<FAQCategory> callback,
                             final FAQCategory category) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.receive(category);
            }
        });
    }

    private FAQCategory toCategory(Cursor cursor) {
        String data = cursor.getString(0);
        return new Gson().fromJson(data, FAQCategoryItem.class);
    }

    private static class MyDBHelper extends SQLiteOpenHelper {

        private static final String CREATE_TABLE = "CREATE TABLE categories\n"
                + "(\n"
                + "    id INT PRIMARY KEY NOT NULL,\n"
                + "    data TEXT\n"
                + ")";

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
