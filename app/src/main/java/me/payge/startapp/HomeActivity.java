package me.payge.startapp;

import android.os.Bundle;
import android.util.Log;

import com.trello.rxlifecycle2.components.support.RxAppCompatActivity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.payge.startapp.execute.AsyncExecutor;

public class HomeActivity extends RxAppCompatActivity {

    private static final String TAG = "home";
    private List<AsyncExecutor.AsyncCallback> callbacks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        createTask();
    }

    private void createTask() {
        for (int i = 0; i < 9; i++) {
            final int temp = i;
            AsyncExecutor.AsyncCallback<String> callback = new AsyncExecutor.AsyncCallback<String>() {
                @Override
                public void runBefore() {
                    Log.i(TAG, "runBefore: " + temp);
                }

                @Override
                public String running() {
                    for (int j = 0; j < temp + 1; j++) {
                        if (stop) return "stop";
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.i(TAG, "running over: " + temp);
                    return String.valueOf(temp);
                }

                @Override
                public void runAfter(String s) {
                    Log.i(TAG, "runAfter: " + s);
                }
            };
            callbacks.add(callback);
            AsyncExecutor.getInstance().execute(this, callback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Iterator<AsyncExecutor.AsyncCallback> iterator = callbacks.iterator();
        while (iterator.hasNext()) {
            AsyncExecutor.AsyncCallback callback = iterator.next();
            AsyncExecutor.getInstance().cancel(callback);
            iterator.remove();
        }
        Log.i(TAG, "onDestroy: ");
    }
}
