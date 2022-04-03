package ncku.exercisenotes;

import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CalendarView;
import android.widget.DatePicker;
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

public class NotesActivity extends AppCompatActivity
implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish(); // back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    Toast tos;
    SQLiteDatabase db;      Cursor cur;
    static final String db_name = "DB", tbSET_name = "Settings", tbREC_name = "RecordLocation";
    double homeLat, homeLng;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        // 設定功能表之返回紐
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        this.setTitle("我的運動日記");
        // 取消自動旋轉
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        tos = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        MaxLatArr = new ArrayList<>();
        MinLatArr = new ArrayList<>();
        MaxLngArr = new ArrayList<>();
        MinLngArr = new ArrayList<>();
        DBIntitialize();
        CalendarMethod();
        SupportMapFragment smf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);        // 取得 地圖Fragment 控制權
        smf.getMapAsync(this);      // 綁定監聽器
    }

    public void CalendarMethod() {
        CalendarView calendarView = findViewById(R.id.calendarView);
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView calendarView, int year, int month, int dayOfMonth) {
                mMap.clear();
                int Colorindex = 0;
                MaxLatArr.clear();
                MinLatArr.clear();
                MaxLngArr.clear();
                MinLngArr.clear();
                if(cur.moveToFirst()) {
                    do {
                        GregorianCalendar g = new GregorianCalendar(year, month, dayOfMonth);
                        if (strToGre(cur.getString(0)).equals(g)) {      // 比較日期是否相同
                            Colorindex++;
                            ShowMarker(Colorindex);
                        }
                        else if(Colorindex != 0)     break;     // 已讀完所選日期
                    } while (cur.moveToNext());
                    if(Colorindex == 0)
                        tos.setText("查無" + year + "/" + (month + 1) + "/" + dayOfMonth + "之記錄");
                    else {
                        ReloadMap(new double[] {maxLat, minLat, maxLng, minLng});
                        tos.setText("找到" + Colorindex + "筆路徑");
                    }
                    tos.show();
                } else {
                    tos.setText("無任何路徑記錄");
                    tos.show();
                }
            }
        });
    }

    public GregorianCalendar strToGre(String str) {
        String[] arr = str.split(",");
        return new GregorianCalendar(Integer.parseInt(arr[0]),
                                    Integer.parseInt(arr[1]),
                                    Integer.parseInt(arr[2]));
//            g.set(GregorianCalendar.HOUR_OF_DAY, Integer.parseInt(arr[3]));
//            g.set(GregorianCalendar.MINUTE, Integer.parseInt(arr[4]));
//            g.set(GregorianCalendar.SECOND, Integer.parseInt(arr[5]));
    }

    double maxLat, minLat, maxLng, minLng;
    ArrayList<Double> MaxLatArr, MinLatArr, MaxLngArr, MinLngArr;
    public void ShowMarker(int index) {
        float color = index * 50.0f;
        ArrayList<String> strLatArr = new ArrayList<>(Arrays.asList(cur.getString(1).split(",")));
        ArrayList<String> strLngArr = new ArrayList<>(Arrays.asList(cur.getString(2).split(",")));
        for (int i = 0; i < strLatArr.size(); i++) {
            double RECLat = Double.parseDouble(strLatArr.get(i));
            double RECLng = Double.parseDouble(strLngArr.get(i));
            LatLng REC_LatLng = new LatLng(RECLat, RECLng);
            mMap.addMarker(new MarkerOptions().position(REC_LatLng).title(index + "-" + (i + 1))
                    .icon(BitmapDescriptorFactory.defaultMarker(color)));
        }
        String[] StatLat = cur.getString(5).split(",");
        String[] StatLng = cur.getString(6).split(",");
        MaxLatArr.add(Double.parseDouble(StatLat[0]));
        MinLatArr.add(Double.parseDouble(StatLat[1]));
        MaxLngArr.add(Double.parseDouble(StatLng[0]));
        MinLngArr.add(Double.parseDouble(StatLng[1]));
        if (index == 1) {
            maxLat = Double.parseDouble(StatLat[0]);
            minLat = Double.parseDouble(StatLat[1]);
            maxLng = Double.parseDouble(StatLng[0]);
            minLng = Double.parseDouble(StatLng[1]);
        }
        else {
            maxLat = Math.max(Double.parseDouble(StatLat[0]), maxLat);
            minLat = Math.min(Double.parseDouble(StatLat[1]), minLat);
            maxLng = Math.max(Double.parseDouble(StatLng[0]), maxLng);
            minLng = Math.max(Double.parseDouble(StatLng[1]), minLng);
        }
    }

    public void ReloadMap(double[] margins) {
        // 決定 zoom 必須先取得"最大矩形邊界" = margin
        LatLng centerLatLng = new LatLng((margins[0]+margins[1])/2.0, (margins[2]+margins[3])/2.0);
        double margin = Math.max(margins[0] - margins[1], margins[2] - margins[3]) * 100;
        float zoom = (float) (Math.log(20000.0 / margin) / Math.log(2.0));      // 縮放倍率是以Log2函數增長，係數 k/距離 即可計算
        zoom = (zoom < mMap.getMinZoomLevel() ? mMap.getMinZoomLevel() : zoom);
        zoom = (zoom > mMap.getMaxZoomLevel() ? mMap.getMaxZoomLevel() : zoom);
        mMap.moveCamera(CameraUpdateFactory.zoomTo(zoom));
        mMap.animateCamera(CameraUpdateFactory.newLatLng(centerLatLng));
    }

    private GoogleMap mMap;
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        homeLatLng = new LatLng(homeLat, homeLng);
        mMap.addMarker(new MarkerOptions().position(homeLatLng).title("家")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(18));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(homeLatLng));
        mMap.setOnMarkerClickListener(this);
    }

    LatLng homeLatLng;
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
        }
        else {
            tos.setText("讀取資料發生錯誤");
            tos.show();
        }
        db.close();
        db = openOrCreateDatabase(db_name, MODE_PRIVATE, null);
        String sql = "SELECT * FROM " + tbREC_name;
        cur = db.rawQuery(sql, null);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        String markerText[] = marker.getTitle().split("-");
        int index = Integer.parseInt(markerText[0])-1;      // 編號從1開始 >> 要減1
        double[] margins = new double[]{MaxLatArr.get(index), MinLatArr.get(index), MaxLngArr.get(index), MinLngArr.get(index)};
        ReloadMap(margins);
        tos.setText(index+"");
        tos.show();
        return true;
    }
//
//    @Override
//    public void onSelectedDayChange(@androidx.annotation.NonNull CalendarView view, int year, int month, int dayOfMonth) {
//
//    }
}