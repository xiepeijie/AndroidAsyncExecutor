package me.payge.startapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import me.payge.startapp.execute.AsyncExecutor;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "home";

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
            AsyncExecutor.getInstance().execute(this, callback);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }
}
