package ncku.exercisenotes;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
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
import java.util.HashMap;
/* 溫馨小提醒：記得展開程式看喔
* [技術一！] 多人連線清單(ArrayList<LatLng>類別)
* [技術二！] 不同人用不同顏色的圖釘顯示
* [技術三！] 最佳化地圖顯示
* 可以再自己掰下去喔......
* */
public class MonitorActivity extends AppCompatActivity
implements OnMapReadyCallback {
    @Override       // 畫面左上角返回功能
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            this.finish(); // back button
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    Toast tos;
    HashMap<String, Double[]> Webdb;
    ClientThread ct;
    Handler iH;   // inputHandler
    private String host = "140.116.47.92";
    private int port = 7001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        // 設定功能表之返回紐
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        this.setTitle("監控位置");
        tos = Toast.makeText(this, "", Toast.LENGTH_LONG);

        DBInitialize();     // 從資料庫(Settings Table)取出自己家的位置、使用者名稱ID

        SupportMapFragment smf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);        // 取得 地圖Fragment 控制權
        smf.getMapAsync(this);      // 綁定監聽器

        /***********多人連線***********/
        // Step 3 接收位置訊息
        Webdb = new HashMap<>();
        iH = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 0) {
                    String s = msg.obj.toString();
                    String[] strArr = s.split(",");
                    if(strArr.length != 3)
                        return;
                    String id = strArr[0];
                    Double[] msglatlng = new Double[2];
                    msglatlng[0] = Double.parseDouble(strArr[1]);
                    msglatlng[1] = Double.parseDouble(strArr[2]);
                    Webdb.put(id, msglatlng);
                    UpdateScreen();     // 更新 Map 畫面
                }
            }
        };
        // Step 1 建立連線
        ct = new ClientThread(iH, host, port);
        new Thread(ct).start();     // 開始執行 "多執行序"
        UpdateScreen();     // 更新 Map 畫面
    }

    SQLiteDatabase db;      Cursor cur;
    static final String db_name = "DB", tbSET_name = "Settings";
    double myLat, myLng;
    ArrayList<LatLng> OthersLatLng;     // 宣告多人連線位置清單(第一項放自己的位置)[技術一！]
    public void DBInitialize() {
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        String sqlSET = "SELECT * FROM " + tbSET_name;
        cur = db.rawQuery(sqlSET, null);

        if(cur.moveToFirst()) {
            String HomeLocation = cur.getString(0);
            String[] strArr = HomeLocation.split(",");
            if(strArr.length == 3) {
                myLat = Double.parseDouble(strArr[0]);
                myLng = Double.parseDouble(strArr[1]);
                OthersLatLng = new ArrayList<>(Arrays.asList(new LatLng(myLat, myLng)));        // 設定自己家的位置到 LatLng 清單
                OthersID = new ArrayList<>(Arrays.asList(cur.getString(4)));
            }
            else Toast.makeText(this, "HomeLocation讀取錯誤", Toast.LENGTH_LONG).show();
        }
        else {
            tos.setText("讀取資料發生錯誤");
            tos.show();
        }
        db.close();
    }

    GoogleMap mMap;
    Marker homeMarker;
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        homeMarker = mMap.addMarker(new MarkerOptions().position(OthersLatLng.get(0)).title("家")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));     // 設定成紅色圖釘
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(OthersLatLng.get(0)));
    }

    ArrayList<String> OthersID;
    public void UpdateScreen() {
        LatLng myhome = OthersLatLng.get(0);            // 保留自己家的位置(先暫存再填入)
        OthersLatLng = new ArrayList<>(Arrays.asList(myhome));

        String myID = OthersID.get(0);                  // 保留自己使用者ID(先暫存再填入)
        OthersID = new ArrayList<>(Arrays.asList(myID));

        TextView tv = findViewById(R.id.textViewStatus);
        String people = "";
        double[] margins = new double[4];       // MaxLat, MinLat, MaxLng, MinLng
        margins[0] = margins[1] = myLat;
        margins[2] = margins[3] = myLng;
        for (String ID : Webdb.keySet()) {
            Double[] latlng = Webdb.get(ID);
            margins[0] = Math.max(latlng[0], margins[0]);   // 最大緯度
            margins[1] = Math.min(latlng[0], margins[1]);   // 最小緯度
            margins[2] = Math.max(latlng[1], margins[2]);   // 最大經度
            margins[3] = Math.min(latlng[1], margins[3]);   // 最小經度
            OthersID.add(ID);
            OthersLatLng.add(new LatLng(latlng[0], latlng[1]));     // 寫入多人連線位置清單[技術一！]
            people += "\n" + ID;
        }

        if(OthersLatLng.size() != 1) {      // 如果有人連線(自己不算)
            tv.setText("目前連線人數：" + (OthersLatLng.size() - 1) + people);
            ShowMarker();       // 顯示所有人位置圖釘
            ReloadMap(margins); // 最佳化地圖顯示[技術三！]
        }
        else    tv.setText("目前無人在線");
    }

    public void ShowMarker() {
        mMap.clear();
        for(int i = 0; i < OthersLatLng.size(); i++) {
            mMap.addMarker(new MarkerOptions().position(OthersLatLng.get(i)).title(OthersID.get(i))
                    .icon(BitmapDescriptorFactory.defaultMarker(i*50.0f)));     // 不同人用不同顏色的圖釘顯示[技術二！]
        }
    }

    public void ReloadMap(double[] margins) {
        // double[] margins 內容：MaxLat, MinLat, MaxLng, MinLng
        // 決定 zoom 必須先取得"最大矩形邊界"
        LatLng centerLatLng = new LatLng((margins[0]+margins[1])/2.0, (margins[2]+margins[3])/2.0);     // 中心經緯度
        float zoom = (float) Math.max(margins[0] - margins[1], margins[2] - margins[3]) * 100;
        zoom = (float) (Math.log(30000.0 / zoom) / Math.log(2.0));      // 縮放倍率是以Log2函數增長，係數 k/距離 即可計算
        zoom = (zoom < mMap.getMinZoomLevel() ? mMap.getMinZoomLevel() : zoom);
        zoom = (zoom > mMap.getMaxZoomLevel() ? mMap.getMaxZoomLevel() : zoom);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(zoom));
        mMap.animateCamera(CameraUpdateFactory.newLatLng(centerLatLng));
    }
}