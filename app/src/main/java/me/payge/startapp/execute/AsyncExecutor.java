package me.payge.startapp.execute;


import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AsyncExecutor implements Handler.Callback, Application.ActivityLifecycleCallbacks {

    public static final int ON_CREATE = 11;
    public static final int ON_START = 12;
    public static final int ON_RESUME = 13;
    public static final int ON_PAUSE = 14;
    public static final int ON_STOP = 15;
    public static final int ON_DESTROY = 16;

    private Handler mainHandler;
    private ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private SparseArray<AsyncCallback> commonCallbackCache = new SparseArray<>();
    private SparseArray<List<AsyncCallback>> activityCallbackCache = new SparseArray<>();
    private boolean isRegisterLifecycle;
    private int stopOnLifecycleEvent = ON_DESTROY;
    private List<Lifecycle> lifecycles = new ArrayList<>();

    private static class InnerHolder {
        static final AsyncExecutor executor = new AsyncExecutor();
    }
    public static AsyncExecutor getInstance() {
        return InnerHolder.executor;
    }
    private AsyncExecutor() {
        mainHandler = new Handler(Looper.getMainLooper(), this);
    }

    public void registerLifecycle(Lifecycle lifecycle) {
        this.lifecycles.add(lifecycle);
    }

    public void unregisterLifecycle(Lifecycle lifecycle) {
        this.lifecycles.remove(lifecycle);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (mainHandler.getLooper().getThread() == Thread.currentThread()) {
            if (msg.obj instanceof AsyncCallback) {
                AsyncCallback callback = (AsyncCallback) msg.obj;
                if (callback.stop) return true;
                callback.runAfter(callback.t);
                commonCallbackCache.remove(msg.what);
                List<AsyncCallback> list = activityCallbackCache.get(msg.what);
                if (list != null) {
                    list.remove(callback);
                    if (list.isEmpty()) {
                        activityCallbackCache.remove(msg.what);
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
            if (!isRegisterLifecycle) {
                if (tag instanceof Activity) {
                    isRegisterLifecycle = true;
                    Activity activity = (Activity) tag;
                    activity.getApplication().registerActivityLifecycleCallbacks(this);
                } else if (tag instanceof Fragment) {
                    isRegisterLifecycle = true;
                    Fragment fragment = (Fragment) tag;
                    Activity activity = fragment.getActivity();
                    if (activity != null) {
                        activity.getApplication().registerActivityLifecycleCallbacks(this);
                    }
                }
            }
            List<AsyncCallback> list = activityCallbackCache.get(hashCode);
            if (list == null) {
                list = new ArrayList<>();
                activityCallbackCache.put(hashCode, list);
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
        List<AsyncCallback> list = activityCallbackCache.get(tag.hashCode());
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

    /**
     * @param onLifecycleEvent {@link AsyncExecutor#ON_START}, {@link AsyncExecutor#ON_RESUME},
     * {@link AsyncExecutor#ON_PAUSE}, {@link AsyncExecutor#ON_STOP}, {@link AsyncExecutor#ON_DESTROY}
     */
    public void setStopOnLifecycleEvent(int onLifecycleEvent) {
        if (onLifecycleEvent == ON_START
                || onLifecycleEvent == ON_RESUME
                || onLifecycleEvent == ON_PAUSE
                || onLifecycleEvent == ON_STOP
                || onLifecycleEvent == ON_DESTROY) {
            this.stopOnLifecycleEvent = onLifecycleEvent;
        }
    }

    private void checkStopOnLifecycleEvent(Activity activity, int onLifecycleEvent) {
        if (stopOnLifecycleEvent == onLifecycleEvent) {
            List<AsyncCallback> list = activityCallbackCache.get(activity.hashCode());
            if (list != null) {
                mainHandler.removeMessages(activity.hashCode());
                for (AsyncCallback callback: list) {
                    callback.stop = true;
                }
                activityCallbackCache.remove(activity.hashCode());
            }
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onCreated(activity, savedInstanceState);
        }
    }
    @Override
    public void onActivityStarted(Activity activity) {
        checkStopOnLifecycleEvent(activity, ON_START);
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onStarted(activity);
        }
    }
    @Override
    public void onActivityResumed(Activity activity) {
        checkStopOnLifecycleEvent(activity, ON_RESUME);
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onResumed(activity);
        }
    }
    @Override
    public void onActivityPaused(Activity activity) {
        checkStopOnLifecycleEvent(activity, ON_PAUSE);
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onPaused(activity);
        }
    }
    @Override
    public void onActivityStopped(Activity activity) {
        checkStopOnLifecycleEvent(activity, ON_STOP);
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onStopped(activity);
        }
    }
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onSaveInstanceState(activity, outState);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        checkStopOnLifecycleEvent(activity, ON_DESTROY);
        for (Lifecycle lifecycle :lifecycles) {
            lifecycle.onDestroyed(activity);
        }
    }

    public static abstract class AsyncCallback<T> {
        protected boolean stop = false;
        T t;
        protected void runBefore(){}
        protected abstract T running();
        protected abstract void runAfter(T t);
    }

    public static abstract class Lifecycle {
        protected void onCreated(Activity activity, Bundle bundle){}
        protected void onStarted(Activity activity){}
        protected void onResumed(Activity activity){}
        protected void onPaused(Activity activity){}
        protected void onStopped(Activity activity){}
        protected void onSaveInstanceState(Activity activity, Bundle outState){}
        protected void onDestroyed(Activity activity){}
    }
}
