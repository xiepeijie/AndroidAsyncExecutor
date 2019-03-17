package me.payge.startapp.execute;


import android.arch.lifecycle.GenericLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AsyncExecutor implements Handler.Callback, GenericLifecycleObserver {

    private Handler mainHandler;
    private ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private SparseArray<AsyncCallback> commonCallbackCache = new SparseArray<>();
    private SparseArray<List<AsyncCallback>> lifecycleOwnerCallbackCache = new SparseArray<>();
    private Lifecycle.Event stopOnLifecycleEvent = Lifecycle.Event.ON_DESTROY;

    private static class InnerHolder {
        static final AsyncExecutor executor = new AsyncExecutor();
    }
    public static AsyncExecutor getInstance() {
        return InnerHolder.executor;
    }
    private AsyncExecutor() {
        mainHandler = new Handler(Looper.getMainLooper(), this);
    }

    private void registerLifecycle(LifecycleOwner owner) {
        owner.getLifecycle().addObserver(this);
    }

    private void unregisterLifecycle(LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (mainHandler.getLooper().getThread() == Thread.currentThread()) {
            if (msg.obj instanceof AsyncCallback) {
                AsyncCallback callback = (AsyncCallback) msg.obj;
                if (callback.stop) return true;
                callback.runAfter(callback.t);
                commonCallbackCache.remove(msg.what);
                List<AsyncCallback> list = lifecycleOwnerCallbackCache.get(msg.what);
                if (list != null) {
                    list.remove(callback);
                    if (list.isEmpty()) {
                        lifecycleOwnerCallbackCache.remove(msg.what);
                    }
                }
            }
        }
        return true;
    }

    public <T> void execute(final AsyncCallback<T> callback) {
        execute(null, callback);
    }

    public <T> void execute(final Object tag, final AsyncCallback<T> callback) {
        if (callback == null) return;
        int hashCode;
        if (tag == null) {
            hashCode = callback.hashCode();
            AsyncCallback cacheCallback = commonCallbackCache.get(hashCode);
            if (cacheCallback == null) {
                commonCallbackCache.put(hashCode, callback);
            }
        } else {
            hashCode = tag.hashCode();
            if (tag instanceof LifecycleOwner) {
                LifecycleOwner ui = (LifecycleOwner) tag;
                registerLifecycle(ui);
            }
            List<AsyncCallback> list = lifecycleOwnerCallbackCache.get(hashCode);
            if (list == null) {
                list = new ArrayList<>();
                lifecycleOwnerCallbackCache.put(hashCode, list);
            }
            if (!list.contains(callback)) {
                list.add(callback);
            }
        }
        final int what = hashCode;
        callback.runBefore();
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (callback.stop) {
                    mainHandler.removeMessages(what);
                    return;
                }
                callback.t = callback.running();
                mainHandler.obtainMessage(what, callback).sendToTarget();
            }
        });
    }

    public void cancel(AsyncCallback callback) {
        int hash = callback.hashCode();
        if (commonCallbackCache.get(hash) != null) {
            mainHandler.removeMessages(hash);
            commonCallbackCache.get(hash).stop = true;
            commonCallbackCache.remove(hash);
        }
    }

    public void cancel(Object tag) {
        List<AsyncCallback> list = lifecycleOwnerCallbackCache.get(tag.hashCode());
        if (list != null) {
            mainHandler.removeMessages(tag.hashCode());
            ListIterator<AsyncCallback> iterator = list.listIterator();
            AsyncCallback callback;
            while (iterator.hasNext()) {
                callback = iterator.next();
                callback.stop = true;
                iterator.remove();
            }
        }
    }

    private void setStopOnLifecycleEvent(Lifecycle.Event event) {
        this.stopOnLifecycleEvent = event;
    }

    private boolean whetherStopOnLifecycleEvent(Object tag, Lifecycle.Event event) {
        boolean stopCallback = stopOnLifecycleEvent == event;
        if (stopCallback) {
            List<AsyncCallback> list = lifecycleOwnerCallbackCache.get(tag.hashCode());
            if (list != null) {
                mainHandler.removeMessages(tag.hashCode());
                for (AsyncCallback callback: list) {
                    callback.stop = true;
                }
                lifecycleOwnerCallbackCache.remove(tag.hashCode());
            }
        }
        return stopCallback;
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        Log.i("xxx", "onStateChanged: " + event);
        if (!whetherStopOnLifecycleEvent(source, event) && event == Lifecycle.Event.ON_DESTROY) {
            unregisterLifecycle(source);
        }
    }

    public static abstract class AsyncCallback<T> {
        protected boolean stop = false;
        T t;
        protected void runBefore() {}
        protected abstract T running();
        protected void runAfter(T t) {}
    }

}
