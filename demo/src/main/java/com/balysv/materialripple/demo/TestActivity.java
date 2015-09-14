package com.balysv.materialripple.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

/**
 * Created by HelloCsl(cslgogogo@gmail.com) on 2015/9/14 0014.
 */
public class TestActivity extends ActionBarActivity {

    public static Intent getIntent(Context con) {
        return new Intent(con, TestActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
    }
}
