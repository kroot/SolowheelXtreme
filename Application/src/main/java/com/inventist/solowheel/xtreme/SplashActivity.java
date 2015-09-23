/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inventist.solowheel.xtreme;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;


public class SplashActivity extends Activity {

    public static DeviceScanActivity deviceScanActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getEualAccepted()) {
            skipEula();
        }
        else {
            RelativeLayout splash = (RelativeLayout) findViewById(R.id.splash_activity);
            splash.setVisibility(View.VISIBLE);

            Button btnApprove = (Button) findViewById(R.id.buttonAgree);
            btnApprove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    overridePendingTransition(R.anim.slide_in_right,
                            R.anim.slide_out_right);
                    setEualAccepted();
                    skipEula();
                }
            });
        }
    }

    public void skipEula() {
        Intent i = new Intent(SplashActivity.this, DeviceScanActivity.class);
        startActivity(i);
        finish();
    }

    public final static String EULA_ACCEPTED_KEY = "eulaAccepted";

    public void setEualAccepted() {
        SharedPreferences settings = getSharedPreferences(DeviceScanActivity.SHARED_PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(EULA_ACCEPTED_KEY, true);
        editor.commit();
    }

    public Boolean getEualAccepted() {
        SharedPreferences settings = getSharedPreferences(DeviceScanActivity.SHARED_PREF_NAME, 0);
        return settings.getBoolean(EULA_ACCEPTED_KEY, false);
    }
}
