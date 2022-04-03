package ncku.exercisenotes;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
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
import java.util.List;
import java.util.Locale;
/* 溫馨小提醒：記得展開程式看喔
 * [重點一！] 輸入防呆，目標里程 .....
 * [重點二！] 不使用Intent傳回設定值，改為存在資料庫中
 * */
public class SettingsActivity extends AppCompatActivity
implements OnMapReadyCallback {
    @Override       // 畫面左上角返回功能
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            this.finish(); // back button
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    SQLiteDatabase db;      Cursor cur;
    static final String db_name = "DB", tbSET_name = "Settings";
    int Goal, MIN_DIS, MIN_TIME;
    String HomeLocation, UserID;
    Spinner sp_dis, sp_time;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // 設定功能表之返回紐
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        this.setTitle("設定");
        sp_dis = findViewById(R.id.spinner1);
        sp_time = findViewById(R.id.spinner2);
        InitializeTable();      // 自動填入上次紀錄到表格中
        SupportMapFragment smf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);        // 取得 地圖Fragment 控制權
        smf.getMapAsync(this);      // 綁定監聽器
    }

    LatLng Home;
    double homeLat, homeLng;
    Marker homeMarker;
    private GoogleMap mMap;
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        Home = new LatLng(homeLat, homeLng);
        homeMarker = mMap.addMarker(new MarkerOptions().position(Home).title("家")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(Home));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
    }

    ArrayList<Integer> DistanceArr = new ArrayList<>(Arrays.asList(5, 10, 20, 50));
    ArrayList<Integer> TimeArr = new ArrayList<>(Arrays.asList(5000, 10000, 20000, 50000));
    public void InitializeTable() {
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        String sqlSET = "SELECT * FROM " + tbSET_name;
        cur = db.rawQuery(sqlSET, null);

        if(cur.moveToFirst()) {
            HomeLocation = cur.getString(0);
            String[] strArr = HomeLocation.split(",");
            if(strArr.length == 3) {
                homeLat = Double.parseDouble(strArr[0]);
                homeLng = Double.parseDouble(strArr[1]);
                ((EditText)findViewById(R.id.editText2)).setText(strArr[2]);
            }
            else Toast.makeText(this, "HomeLocation讀取錯誤", Toast.LENGTH_LONG).show();
            Goal = cur.getInt(1);
            MIN_DIS = cur.getInt(2);
            MIN_TIME = cur.getInt(3);
            ((EditText)findViewById(R.id.editText1)).setText("" +Goal);
            sp_dis.setSelection(DistanceArr.indexOf(MIN_DIS));
            sp_time.setSelection(TimeArr.indexOf(MIN_TIME));
            UserID = cur.getString(4);
            ((EditText)findViewById(R.id.editText_Name)).setText(UserID);
        }
        else {
            Toast.makeText(this, "讀取資料發生錯誤", Toast.LENGTH_LONG).show();
        }

    }

    // 點擊 ImageView 後搜尋 GoogleMap
    public void onSearch(View v) {
        Geocoder gc = new Geocoder(this, Locale.getDefault());// Locale. >> 設定顯示格式
        try {
            // 地址轉經緯度 >> getFromLocationName( 地址String, 回傳數量)
            EditText et = findViewById(R.id.editText2);
            List<Address> listAddr = gc.getFromLocationName(et.getText().toString(), 1);       // Address >> 變數型態
            // 防呆
            if(listAddr == null || listAddr.size() == 0) {
                Toast.makeText(this, "查無\"" + et.getText().toString() + "\"之結果", Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(this, "已放置圖釘", Toast.LENGTH_SHORT).show();
                Address addr = listAddr.get(0);     // 取出查詢結果之第一筆資料(也是唯一一筆)
                homeLat = addr.getLatitude();
                homeLng = addr.getLongitude();
                Home = new LatLng(homeLat, homeLng);
                // 更動目的地標
                if(homeMarker != null)
                    homeMarker.remove();
                homeMarker = mMap.addMarker(new MarkerOptions().position(Home).title("家"));
                mMap.moveCamera(CameraUpdateFactory.newLatLng(Home));
                mMap.animateCamera(CameraUpdateFactory.zoomTo(18));
                HomeLocation = homeLat+","+homeLng+","+et.getText().toString();
            }
        }
        catch (Exception e) {
            Toast.makeText(this, "取得目標經緯度發生錯誤", Toast.LENGTH_LONG).show();
        }
    }

    public void Save(View v) {
        // 儲存資料並返回主畫面
        if(((EditText)findViewById(R.id.editText1)).getText().toString().length() != 0) {
            Goal = Integer.parseInt(((EditText) findViewById(R.id.editText1)).getText().toString());
            if(Goal < 10 || Goal > 1000) {
                Toast.makeText(this, "請輸入10~1000", Toast.LENGTH_LONG).show();
                return;
            }
        }
        else {
            Toast.makeText(this, "里程欄位不能為空", Toast.LENGTH_LONG).show();
            return;
        }
        UserID = ((EditText)findViewById(R.id.editText_Name)).getText().toString();
        if(UserID.length() == 0) {
            Toast.makeText(this, "使用者名稱無效", Toast.LENGTH_LONG).show();
            return;
        }
        MIN_DIS = Integer.parseInt(sp_dis.getSelectedItem().toString());
        MIN_TIME = Integer.parseInt(sp_time.getSelectedItem().toString())*1000;
        // 寫入設定到資料庫
        ContentValues cv = new ContentValues(5);
        cv.put("HomeLocation", HomeLocation);
        cv.put("Goal", Goal);
        cv.put("MIN_DIS", MIN_DIS);
        cv.put("MIN_TIME", MIN_TIME);
        cv.put("UserID", UserID);
        db.update(
                tbSET_name,
                cv,
                "Goal="+cur.getInt(1),
                null);
        db.close();
        Toast.makeText(this, "設定已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}