package com.cipherfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class DashboardActivity extends Activity {
 @Override protected void onCreate(Bundle b){super.onCreate(b); setContentView(R.layout.activity_dashboard);
  findViewById(R.id.moduleText).setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
  findViewById(R.id.moduleFile).setOnClickListener(v->startActivity(new Intent(this,FileEncryptionActivity.class)));
  findViewById(R.id.moduleMedia).setOnClickListener(v->startActivity(new Intent(this,MediaEncryptionActivity.class)));
  findViewById(R.id.logoutButton).setOnClickListener(v->{getSharedPreferences("session",MODE_PRIVATE).edit().clear().apply(); startActivity(new Intent(this,LoginActivity.class)); finish();});
 }
}
