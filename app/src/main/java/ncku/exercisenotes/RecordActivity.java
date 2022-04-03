package ncku.exercisenotes;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.util.GregorianCalendar;
import java.util.HashMap;

public class RecordActivity extends AppCompatActivity
implements OnMapReadyCallback, LocationListener, SensorEventListener {

    Toast tos;
    SQLiteDatabase db;      Cursor cur;
    static final String db_name = "DB", tbSET_name = "Settings", tbREC_name = "RecordLocation";
    int MIN_DIS = 5, MIN_TIME = 5000;
    SensorManager sm;
    Sensor sr_acc, sr_mag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        this.setTitle("紀錄位置資訊中");
        // 取消自動旋轉
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        tos = Toast.makeText(this, "", Toast.LENGTH_LONG);

        DBIntitialize();
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sr_mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);       // 磁力計(動態開關)
        sr_acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);        // 加速度計
        sm.registerListener(this, sr_acc, SensorManager.SENSOR_DELAY_NORMAL);      // 綁定監聽器
        enableLocationUpdates(true);
        SupportMapFragment smf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);        // 取得 地圖Fragment 控制權
        smf.getMapAsync(this);      // 綁定監聽器
        /***********多人連線***********/
        // Step 3 接收位置訊息
        Webdb = new HashMap<>();
        iH = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 0) {        // 輸入模式
                    String s = msg.obj.toString();
                    String[] strArr = s.split(",");
                    if(strArr.length != 3)
                        return;
                    String id = strArr[0];
                    Double[] msglatlng = new Double[2];
                    msglatlng[0] = Double.parseDouble(strArr[1]);
                    msglatlng[1] = Double.parseDouble(strArr[2]);
                    Webdb.put(id, msglatlng);       // java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Object java.util.HashMap.put(java.lang.Object, java.lang.Object)' on a null object reference
                }
            }
        };

        // Step 1 建立連線
        ct = new ClientThread(iH, host, port);
        new Thread(ct).start();     // 開始執行 "多執行序"
    }

    private void enableLocationUpdates(boolean isTurnOn) {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 使用者已經允許定位權限
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if(isTurnOn) {
                // 檢查 GPS 與網路定位是否可用
                Boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                Boolean isNETEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                if (!isGPSEnabled && !isNETEnabled) {
                    // 無提供者，顯示提示訊息
                    Toast.makeText(this, "請確認已開啟定位功能", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(this, "取得定位資訊中...", Toast.LENGTH_SHORT).show();
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DIS, this);      // 向 GPS 提供者註冊位置事件監聽器
                    if(isNETEnabled)
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DIS, this);  // 向網路訂位提供者註冊位置事件監聽器
                }
            }
            else    // isTurnOn ==  false
                lm.removeUpdates(this);     // 停止監聽事件
        }
        else {      // 使用者拒絕定位權限
            Toast.makeText(this, "請重啟程式\n並允許位置權限", Toast.LENGTH_LONG);
        }
    }

    LatLng homeLatLng;
    double homeLat, homeLng;
    String UserId = "";
    public void DBIntitialize() {
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        String sqlSET = "SELECT * FROM " + tbSET_name;
        cur = db.rawQuery(sqlSET, null);

        if(cur.moveToFirst()) {
            String HomeLocation = cur.getString(0);
            String[] strArr = HomeLocation.split(",");
            if(strArr.length == 3) {
                homeLat = Double.parseDouble(strArr[0]);
                homeLng = Double.parseDouble(strArr[1]);
            }
            else Toast.makeText(this, "HomeLocation讀取錯誤", Toast.LENGTH_LONG).show();
            MIN_DIS = cur.getInt(2);
            MIN_TIME = cur.getInt(3);
            UserId = cur.getString(4);
        }
        else {
            tos.setText("讀取資料發生錯誤");
            tos.show();
        }
        db.close();
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        String sqlREC = "SELECT * FROM " + tbREC_name;
        cur = db.rawQuery(sqlREC, null);

    }
    String getDateStr() {
        GregorianCalendar g = new GregorianCalendar();
        int y = g.get(GregorianCalendar.YEAR);
        int m = g.get(GregorianCalendar.MONTH);
        int d = g.get(GregorianCalendar.DAY_OF_MONTH);
        int h = g.get(GregorianCalendar.HOUR_OF_DAY);
        int min = g.get(GregorianCalendar.MINUTE);
        int s = g.get(GregorianCalendar.SECOND);
        String date = y + "," + m + "," + d + "," + h + "," +min+","+s;
        return date;
    }

    final int limit = 5;
    public void onStop(View v) {
        if(pointCount >= limit) {
            String sql = "SELECT * FROM " + tbREC_name;
            cur = db.rawQuery(sql, null);
            ContentValues cv = new ContentValues(7);
            String Date = getDateStr();
            cv.put("Date", Date);
            cv.put("Lat", DataLat);
            cv.put("Lng", DataLng);
            cv.put("Step", Step + "," + Distance);
            cv.put("Count", pointCount);
            cv.put("StatLat", maxLat+","+minLat+","+((maxLat+minLat)/2));
            cv.put("StatLng", maxLng+","+minLng+","+((maxLng+minLng)/2));
            db.insert(tbREC_name, null, cv);
            db.close();
            Toast.makeText(this, "記錄已保存至\n\"" + Date + "\"", Toast.LENGTH_LONG).show();
        }
        else Toast.makeText(this, "錯誤！記錄須超過"+ limit+ "個點", Toast.LENGTH_LONG).show();
        sm.unregisterListener(this);
        enableLocationUpdates(false);
        finish();
    }

    double myLat = 22.9981107, myLng = 120.2191568;
    private GoogleMap mMap;     LatLng myLatLng = null;
    Marker myMarker = null, homeMarker = null;
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        homeLatLng = new LatLng(homeLat, homeLng);
        homeMarker = mMap.addMarker(new MarkerOptions().position(homeLatLng).title("家")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(homeLatLng));
    }

    float[] aValue = null;      // 加速度計讀值
    float[] mValue = null;      // 磁力計讀值
    float bias = 0;             // 家與北方之夾角(rad)
    int Step = 0;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mValue = event.values;
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            aValue = event.values;

        if(aValue != null) {
            StepCode();
        }
        if(aValue != null && mValue != null) {
            // 計算正y軸與北方的夾角 (旋轉矩陣)
            float[] Rotation = new float[9];
            float[] degree = new float[3];
            SensorManager.getRotationMatrix(Rotation, null, aValue, mValue);
            SensorManager.getOrientation(Rotation, degree);
            float angle = (float) Math.toDegrees(degree[0]);

            ImageView iv_Dir = findViewById(R.id.imageView_Dir);
            if(DirMode == 1) {      // 指北針模式
                iv_Dir.setImageResource(R.drawable.ncompass);
                iv_Dir.setRotation(-angle);
            }
            else if (DirMode == 2){
                iv_Dir.setImageResource(R.drawable.arr);
                iv_Dir.setRotation((float) (-angle + Math.toDegrees(bias)));
            }
        }
    }

    public void StepCode() {
        int xyz = 0;
        for (int i = 0; i < 3; i++) xyz += Math.abs(aValue[i]) > 10 ? 1 : 0;
        if (xyz >= 2) {
            ((TextView) findViewById(R.id.textView_Step)).setText(++Step + " 步");
//            ((TextView) findViewById(R.id.textView_Distance)).setText(xyz + "");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    int pointCount = 0;
    double maxLat, minLat, maxLng, minLng;
    @Override
    public void onLocationChanged(Location location) {
        pointCount++;
//        Toast.makeText(this, "pointCount = " + pointCount, Toast.LENGTH_SHORT).show();
        if(myMarker != null) {
            myMarker.remove();
            myMarker = mMap.addMarker(new MarkerOptions().position(myLatLng).title((pointCount-1)+"-"+getDateStr())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));       // 變動上個紀錄的Marker顏色
            lastLat = myLat;
            lastLng = myLng;
        }
        else {      // 第一個記錄點
            maxLat = minLat = location.getLatitude();
            maxLng =  minLng = location.getLongitude();
        }
        myLat = location.getLatitude();
        myLng = location.getLongitude();
        myLatLng = new LatLng(myLat, myLng);
        RecordData();
        ReloadMap();
        SendToServer();
    }

    HashMap<String, Double[]> Webdb;
    ClientThread ct;
    Handler iH;   // inputHandler
    private String host = "140.116.47.92";
    private int port = 7001;
    public void SendToServer() {
        //Step 2 傳送位置訊息到 ClientThread Output Handler (往Server方向)
        try {
            Message msg = new Message();
            msg.what = 1;
            msg.obj = UserId+","+myLat+","+myLng;
            ct.send(msg);
            Toast.makeText(this, "傳送" + msg.obj.toString(), Toast.LENGTH_SHORT);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    double lastLat, lastLng;
    String DataLat = "", DataLng = "";      // 字串預設為 null ，所以要先指定空字串
    double Distance = 0.0;
    public void RecordData() {
        boolean isFirst = myMarker == null;
        DataLat += (isFirst? "":",") + myLat ;
        DataLng += (isFirst? "":",") + myLng;
        if(!isFirst) {  // 非第一個點才要算距離
            Distance += 100*(Math.sqrt(Math.pow(lastLat-myLat, 2) + Math.pow(lastLng-myLng, 2)));    // 單位：km
            ((TextView)findViewById(R.id.textView_Distance)).setText(String.format("%.1f km", Distance));
        }
        maxLat = Math.max(myLat, maxLat);
        minLat = Math.min(myLat, minLat);
        maxLng = Math.max(myLng, maxLng);
        minLng = Math.min(myLng, minLng);
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override
    public void onProviderEnabled(String provider) { }
    @Override
    public void onProviderDisabled(String provider) { }

    int DirMode = 0;
    public void onModeChg(View v) {
        if (myLatLng != null) {
            DirMode = ++DirMode % 3;
            Button bt = findViewById(R.id.button_Dir);
            ImageView iv_Dir = findViewById(R.id.imageView_Dir);
            switch (DirMode) {
                case 0:
                    EnableSensors(false);
                    bt.setText("關閉");
                    iv_Dir.setImageResource(R.drawable.foot2);
                    iv_Dir.setRotation(0);
                    break;
                case 1:
                    EnableSensors(true);
                    bt.setText("指北針");
                    iv_Dir.setImageResource(R.drawable.ncompass);
                    break;
                case 2:
                    EnableSensors(true);
                    bt.setText("回家方向");
                    iv_Dir.setImageResource(R.drawable.arr);
                    break;
            }
            ReloadMap();
        }
        else Toast.makeText(this, "尚未取得位置資訊", Toast.LENGTH_SHORT).show();
    }
    public void ReloadMap() {
            double Dist = 100 * (Math.sqrt(Math.pow(homeLat - myLat, 2) + Math.pow(homeLng - myLng, 2)));    // 單位：km
            bias = (float) Math.atan2(homeLng - myLng, homeLat - myLat);
            LatLng center_LatLng = new LatLng((myLat + homeLat) / 2.0, (myLng + homeLng) / 2.0);
            float zoom = (float) (Dist * Math.max(Math.abs(Math.sin(bias)), Math.abs(Math.cos(bias))));       // 選取最大之經度或緯度
            zoom = (float) (Math.log(30000.0 / zoom) / Math.log(2.0));      // 縮放倍率是以Log2函數增長，係數 k/距離 即可計算
            zoom = (zoom < mMap.getMinZoomLevel() ? mMap.getMinZoomLevel() : zoom);
            zoom = (zoom > mMap.getMaxZoomLevel() ? mMap.getMaxZoomLevel() : zoom);
            myMarker = mMap.addMarker(new MarkerOptions().position(myLatLng).title("目前位置"));

            if(DirMode == 0) {
                mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
                mMap.animateCamera(CameraUpdateFactory.newLatLng(myLatLng));
            }
            else {
                mMap.moveCamera(CameraUpdateFactory.zoomTo(zoom));
                mMap.animateCamera(CameraUpdateFactory.newLatLng(center_LatLng));
            }

    }
//    int startNum = 0;
//    public void getstartNum() {
//        if (cur.moveToFirst()) {
//            do {
//                String[] strdateArr1 = cur.getString(0).split(",");
//                String[] strdateArr2 = getDateStr().split(",");
//                if (strdateArr.length == 6) {
//                    int[] RECdateArr = new int[3];
//                    int[] NOWdateArr = new int[3];
//
//                    for (int i = 0; i < 3; i++) {
//                        RECdateArr[i] = Integer.parseInt(strdateArr[i]);
//                    }
//
//                    for (int i = 0; i < 3; i++) RECdateArr[i] = Integer.parseInt(strdateArr[i]);
//                    if (RECdateArr[0] == year && RECdateArr[1] == month + 1 && RECdateArr[2] == dayOfMonth) {
//                        startNum++;
//                    } else {
//                        if (startNum != 0) break;
//                    }
//                } else {
//                    tos.setText("資料庫日期讀取錯誤");
//                    tos.show();
//                }
//            } while (cur.moveToNext());
//        }
//    }
    public void EnableSensors(boolean enable) {
        if(enable)
            sm.registerListener(this, sr_mag, SensorManager.SENSOR_DELAY_NORMAL);      // 綁定監聽器
        else
            sm.unregisterListener(this, sr_mag);
    }
}