package ru.webim.android.demo.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import ru.webim.android.sdk.WebimLog;

public class FileLogger implements WebimLog {
    private final BufferedWriter bufferedWriter;
    private final Handler handler;

    private static FileLogger instance;

    private FileLogger(BufferedWriter bufferedWriter) {
        this.bufferedWriter = bufferedWriter;
        HandlerThread handlerThread = new HandlerThread(this.getClass().getSimpleName());
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void log(String message) {
        handler.post(() -> {
            try {
                bufferedWriter.append(message);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public synchronized static FileLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("File logger not configured");
        }
        return instance;
    }


    @Nullable
    public synchronized static FileLogger getAppSpecificDirLogger(Context context, String filename)  {
        if (instance != null) {
            return instance;
        }
        checkExternalStorageAvailable();

        File directory = context.getExternalFilesDir(null);
        File file = new File(directory, filename);

        try {
            instance = new FileLogger(new BufferedWriter(new FileWriter(file, true)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    private static void checkExternalStorageAvailable() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IllegalStateException("External storage not available");
        }
    }

    @Nullable
    public synchronized static FileLogger loadLogsToDownloads(Context context, String dir, String filename) {
        if (instance != null) {
            return instance;
        }
        checkExternalStorageAvailable();

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + dir);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");

            Uri baseUri;
            ContentResolver contentResolver = context.getContentResolver();
            try {
                baseUri = getUriFromPath(filename, contentResolver);
                if (baseUri == null) {
                    throw new IllegalStateException("Uri not exists");
                }
            } catch (Exception e) {
                baseUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values);
            }

            if (baseUri == null) {
                throw new IllegalStateException("Unable to create file");
            }
            OutputStream outputStream = context.getContentResolver().openOutputStream(baseUri);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));

            instance = new FileLogger(bufferedWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    private static Uri getUriFromPath(String displayName, ContentResolver contentResolver) {
        Uri fileUri = MediaStore.Files.getContentUri("external");
        String[] projection = new String[]{MediaStore.Files.FileColumns._ID};
        Cursor cursor = contentResolver.query(
            fileUri,
            projection,
            MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?",
            new String[]{displayName},
            null
        );
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(projection[0]);
        long photoId = cursor.getLong(columnIndex);
        cursor.close();
        return Uri.parse(fileUri + "/" + photoId);
    }
}
