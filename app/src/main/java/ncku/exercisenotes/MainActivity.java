package ncku.exercisenotes;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.LocationManager;
import android.media.audiofx.BassBoost;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;

public class MainActivity extends AppCompatActivity
implements DialogInterface.OnClickListener{
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.miSetup) {
            Intent it = new Intent();
            it.setClass(this, SettingsActivity.class);
            startActivity(it);
        }
        else if(item.getItemId() == R.id.miSetGPS) {
            Intent it = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(it);
        }
        else if(item.getItemId() == R.id.miVersion) {
            AlertDialog.Builder bdr = new AlertDialog.Builder(this);
            bdr.setIcon(R.drawable.foot);
            bdr.setTitle("版本資訊");
            bdr.setMessage("Version : 1.0\n"
                    + "Developer : \nNCKU.GEO Team No.1\n"
                    + "Email :\n e14066282@gs.ncku.edu.tw\n");
            bdr.setCancelable(false);
            bdr.setPositiveButton("Rate 5 stars", this);      // 系統已經自動綁定 OnClickListener 了
            bdr.show();
        }
        return super.onOptionsItemSelected(item);
    }

    Toast tos;
    SQLiteDatabase db;      Cursor cur;
    static final String db_name = "DB", tbSET_name = "Settings", tbREC_name = "RecordLocation";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tos = Toast.makeText(this, "", Toast.LENGTH_LONG);
        this.setTitle("行動追蹤紀錄器");
        DBIntitialize();
        UpdateInfo();
        checkPermission();
    }

    boolean isGranted;
    private void checkPermission() {
        isGranted = !(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED);
        if(!isGranted) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 200);   // 向使用者要求定位權限
        }
    }

    boolean isGPSEnabled, isNETEnabled;
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode == 200) {
            isGranted = !(grantResults.length >= 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED);
            findViewById(R.id.buttonREC).setEnabled(isGranted);
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNETEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if(!isGranted) {        // 如果使用者拒絕位置權限
                TextView tv = findViewById(R.id.textViewInfo);
                tv.setText("尚未取得位置權限");
                tv.setTextColor(Color.rgb(255,0,0));
                tos.setText("程式需要定位權限才能運作");
                tos.show();
            }
            else {
                ((TextView)findViewById(R.id.textViewInfo)).setText("GPS：" + (isGPSEnabled? "可用":"不可用") + "\n" + "NET：" + (isNETEnabled? "可用":"不可用"));
            }
        }
    }

    public void DBIntitialize() {
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        /***********SETTINGS***********/
        String sql = "CREATE TABLE IF NOT EXISTS " + tbSET_name +
                " (HomeLocation VARCHAR(32), " +
                " Goal VARINT(32),  " +
                " MIN_DIS VARINT(32), " +
                " MIN_TIME VARINT(32), " +
                " UserID VARCHAR(32))";
        db.execSQL(sql);

        sql = "SELECT * FROM " + tbSET_name;
        cur = db.rawQuery(sql, null);
        if(cur.getCount() == 0) {
            ContentValues cv_setting = new ContentValues(5);
            cv_setting.put("HomeLocation", "22.998642,120.2199000,成功大學");
            cv_setting.put("Goal", 150);
            cv_setting.put("MIN_DIS", 5);
            cv_setting.put("MIN_TIME", 5000);
            cv_setting.put("UserID", "user");
            db.insert(tbSET_name, null, cv_setting);
            sql = "SELECT * FROM " + tbSET_name;
            cur = db.rawQuery(sql, null);
        }

        /***********REC***********/
        sql = "CREATE TABLE IF NOT EXISTS " + tbREC_name +
                " (Date VARCHAR(32), " +
                " Lat TEXT, " +
                " Lng TEXT," +
                " Step VARCHAR(16), " +
                " Count VARINT(16), " +
                " StatLat TEXT, " +
                " StatLng TEXT) ";
        db.execSQL(sql);
        sql = "SELECT * FROM " + tbREC_name;
        cur = db.rawQuery(sql, null);
//        Toast.makeText(this, "總共有" + cur.getCount() + "筆資料\n路徑：" + db.getPath(), Toast.LENGTH_LONG).show();
        /*if(cur.getCount() == 0) {
            ContentValues cv_rec = new ContentValues(7);
            cv_rec.put("Date", getDateStr());
            cv_rec.put("Lat", "22.998642000000,22.998653000000,22.998664000000,22.998675000000,22.998686000000,22.998697000000,22.998708000000,22.998719000000,22.998730000000,22.998741000000");
            cv_rec.put("Lng", "120.21990000,120.22000000,120.22010000,120.22020000,120.22030000,120.22040000,120.22050000,120.22060000,120.22070000,120.22080000");
            cv_rec.put("Step", "510,7.23");
            cv_rec.put("Count", 10);
            cv_rec.put("StatLat", "22.998741000000,22.998642000000,22.998686000000");
            cv_rec.put("StatLng", "120.22080000,120.21990000,120.22040000");
            db.insert(tbREC_name, null, cv_rec);

            ContentValues cv_rec1 = new ContentValues(7);
            cv_rec1.put("Date", getDateStr());
            cv_rec1.put("Lat", "22.998642000000,22.998641000000,22.998640000000,22.998639000000,22.998638000000,22.998637000000,22.998636000000,22.998635000000,22.998634000000,22.998633000000");
            cv_rec1.put("Lng", "120.21990000,120.21980000,120.21970000,120.21960000,120.21950000,120.21940000,120.21930000,120.21920000,120.21910000,120.21900000");
            cv_rec1.put("Step", "520,6.8");
            cv_rec1.put("Count", 10);
            cv_rec1.put("StatLat", "22.998741000000,22.998642000000,22.998686000000");
            cv_rec1.put("StatLng", "120.22080000,120.21990000,120.22040000");
            db.insert(tbREC_name, null, cv_rec1);

            sql = "SELECT * FROM " + tbREC_name;
            cur = db.rawQuery(sql, null);
            Toast.makeText(this, "REC資料庫初始化...", Toast.LENGTH_SHORT).show();
        }*/
    }

    public void UpdateInfo() {
        String sql = "SELECT * FROM " + tbSET_name;
        cur = db.rawQuery(sql, null);
        cur.moveToFirst();
        String UserID = cur.getString(4);
        ((TextView) findViewById(R.id.textViewInfo)).setText("使用者：" + UserID);
        double Goal = (double)cur.getInt(1);
        sql = "SELECT * FROM " + tbREC_name;
        cur = db.rawQuery(sql, null);
        if(cur.moveToFirst()) {
            // [ 本月, 昨日, 今日]
            int[] count = new int[]{0,0,0};
            int[] step = new int[]{0,0,0};
            double[] distance = new double[]{0.0, 0.0, 0.0};
            do {
                boolean[] find = new boolean[]{false, false, false};
                GregorianCalendar g1 = getDateGre(false);
                g1.add(GregorianCalendar.DAY_OF_MONTH, -1);
                find[0] = strToGre(cur.getString(0), true).equals(getDateGre(true));
                find[1] = strToGre(cur.getString(0), false).equals(g1);
                find[2] = strToGre(cur.getString(0), false).equals(getDateGre(false));
                for (int i = 0; i < 3; i++) {
                    if (find[i]) {
                        String[] strArr = cur.getString(3).split(",");
                        step[i] += Integer.parseInt(strArr[0]);
                        distance[i] += Double.parseDouble(strArr[1]);
                    } else if (count[i] != 0) break;     // 已讀完該日期
                }
            } while (cur.moveToNext());
            ((TextView)findViewById(R.id.textView1)).setText(step[0] + " 步");
            ((TextView)findViewById(R.id.textView2)).setText(String.format("%.1f km", distance[0]));
            ((TextView)findViewById(R.id.textView3)).setText(step[1] + " 步");
            ((TextView)findViewById(R.id.textView4)).setText(String.format("%.1f km", distance[1]));
            ((TextView)findViewById(R.id.textView5)).setText(step[2] + " 步");
            ((TextView)findViewById(R.id.textView6)).setText(String.format("%.1f km", distance[2]));
            ((TextView)findViewById(R.id.textView5)).setText(step[2] + " 步");
            if(distance[0] < Goal) {
                ((TextView) findViewById(R.id.textViewT4)).setText("距離本月目標還有");
                ((TextView) findViewById(R.id.textView7)).setText(String.format("%.1f km", Goal-distance[2]));
            }
            else {
                ((TextView) findViewById(R.id.textViewT4)).setText("本月目標已達成！");
                ((TextView) findViewById(R.id.textView7)).setText(String.format(">%.1f km", distance[2]-Goal));
            }
        }
    }

    GregorianCalendar getDateGre(boolean isMonth) {
        GregorianCalendar g1 = new GregorianCalendar();
        int y = g1.get(GregorianCalendar.YEAR);
        int m = g1.get(GregorianCalendar.MONTH);
        int d = g1.get(GregorianCalendar.DAY_OF_MONTH);
        return new GregorianCalendar(y, m, (isMonth? 1:d));
    }

    public GregorianCalendar strToGre(String str, boolean isMonth) {
        String[] arr = str.split(",");
        int y = Integer.parseInt(arr[0]);
        int m = Integer.parseInt(arr[1]);
        int d = Integer.parseInt(arr[2]);
        return new GregorianCalendar(y, m, (isMonth? 1:d));
    }

    public void Notes(View v) {
        Intent it_Nt = new Intent();
        it_Nt.setClass(this, NotesActivity.class);
        startActivityForResult(it_Nt, 4);
    }

    public void REC(View v) {
        Intent it_REC = new Intent();
        it_REC.setClass(this, RecordActivity.class);
        db.close();
        startActivity(it_REC);
    }

    public void Monitor(View v) {
        Intent it_Mt = new Intent();
        it_Mt.setClass(this, MonitorActivity.class);
        startActivityForResult(it_Mt, 2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DBIntitialize();
        UpdateInfo();
//        tos.setText("更新");
//        tos.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) { }

    public String getDateStr() {
        GregorianCalendar g1 = new GregorianCalendar();
        int y = g1.get(GregorianCalendar.YEAR);
        int m = g1.get(GregorianCalendar.MONTH);
        int d = g1.get(GregorianCalendar.DAY_OF_MONTH);
        String date = y+","+m+","+d;
        return date;
    }
}
