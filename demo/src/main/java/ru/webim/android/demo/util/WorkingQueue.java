package ru.webim.android.demo.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkingQueue {
    private final ExecutorService executor;
    private final Handler mainHandler;
    private static WorkingQueue INSTANCE;

    private WorkingQueue(ExecutorService executor, Handler mainHandler) {
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    public interface RequestCallback<T> {
        T onWork() throws Throwable;
        void onResult(T result);
        void onFailure(Throwable throwable);
    }

    public void sendTask(Runnable runnable) {
        executor.submit(runnable);
    }

    public <T> void sendTaskAndListenResultOnMainThread(RequestCallback<T> callback) {
        executor.submit(() -> {
            try {
                T result = callback.onWork();
                mainHandler.post(() -> callback.onResult(result));
            } catch (Throwable throwable) {
                mainHandler.post(() -> callback.onFailure(throwable));
            }
        });
    }

    public static WorkingQueue getInstance() {
        if (INSTANCE == null) {
            synchronized (WorkingQueue.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WorkingQueue(Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()));
                }
            }
        }
        return INSTANCE;
    }
}
