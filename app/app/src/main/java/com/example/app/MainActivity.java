package com.example.app;

import static com.example.app.Fragment1.angryface;
import static com.example.app.Fragment1.noface;
import static com.example.app.Fragment1.score;
import static com.example.app.Fragment1.smileface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    Toolbar toolbar;
    TabLayout tabLayout;
    ViewPager2 viewPager;
    TabPagerAdapter adapter;
    boolean water, light0, light1;

    String[] tabName = new String[]{"대시보드", "상세분석", "식물관리"};
    //습도(humid), 온도(temp), 전기전도도(ec), 산화도(ph), 질소(nitro), 인(phos), 칼륨(pota), 광량(light);
    public static HashMap<String, String> mDataHashMap;
    private static final int PERMISSION_REQUEST_CODE = 0;
    public static SharedPreferences sharedPreferences_fragment2;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();// 앱 권한 동의 요청

        sharedPreferences_fragment2 = getSharedPreferences("myPreferences", Context.MODE_PRIVATE);
        toolbar = (Toolbar) findViewById(R.id.toolbar);           // actionbar에서 toolbar로 변경
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        tabLayout = findViewById(R.id.tabs);
        viewPager = findViewById(R.id.viewPager);
        adapter = new TabPagerAdapter(this);//adapter 준비 및 연결
        viewPager.setAdapter(adapter);

        // TabLayout, ViewPager 연결
        new TabLayoutMediator(tabLayout, viewPager, new TabLayoutMediator.TabConfigurationStrategy() {
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                TextView textView = new TextView(MainActivity.this);
                textView.setText(tabName[position]);
                textView.setTextSize(18);
                textView.setGravity(Gravity.CENTER);
                tab.setCustomView(textView);
            }
        }).attach();

        //탭 눌릴때 클릭이벤트하려면 여기다 쓰면됨
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {// 탭이 선택되었을 때 실행되는 코드
                int position = tab.getPosition();

                if (position == 0) {  // Fragment1에 대한 탭이 선택되었을 때
                    Fragment1 fragment1 = (Fragment1) adapter.getFragment(position);
                    if (popup.CONNECT_STATE)    //연결되어있다면
                        new GetPlantManageDataTask().execute(popup.url); //plantmanage 데이터만 가져오는 비동기task 실행
                }
                else if(position == 1){ }//fragment2일때
                else if(position == 2){ }//fragment3일떄
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}   // 탭이 선택 해제되었을 때 실행되는 코드 (필요한 경우)
            @Override
            public void onTabReselected(TabLayout.Tab tab) { }// 이미 선택된 탭이 다시 선택되었을 때 실행되는 코드 (필요한 경우)
        });


        findViewById(R.id.wifi_button).setOnClickListener(this);

        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        popup.plant = sharedPreferences.getString("plant", "");
        popup.ssid = sharedPreferences.getString("ssid", "");
        popup.pw = sharedPreferences.getString("pw", "");
        popup.ip = sharedPreferences.getString("ip", "");
        popup.url = sharedPreferences.getString("url", "");

        WifiConnectionManager connManager = new WifiConnectionManager(this, popup.connText);
        if (!connManager.permission.hasAll())
            connManager.permission.requestAll();
        if (!popup.ssid.equals("") && !popup.pw.equals("") && !popup.ip.equals("") && !popup.url.equals("")) {
            new Thread(() -> {
                connManager.connectToExternal(popup.ssid, popup.pw, 30000);
            }).start();
            connManager.setOnExternalAvailable(() -> {
                new Thread(() -> {
                    if (connManager.sendPing(5000, popup.ip)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "서버 연결 성공", Toast.LENGTH_SHORT).show();
                                popup.CONNECT_STATE = true;
                            }
                        });
                        new GetJsonDataTask().execute(popup.url);
                        new Thread(() -> {
                            try {
                                while (score != -1) {
                                    Thread.sleep(100);
                                }
                                setFace();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }).start();
                    }
                }).start();
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "재등록 바람", Toast.LENGTH_SHORT).show();
                }
            });
            setBlackFace();
        }
    }

    @Override
    public void onClick(View v) {   //wifi버튼-페이지 연결
        switch (v.getId()) {
            case R.id.wifi_button:
                startActivity(new Intent(this, popup.class));
                break;
        }
    }

    public class GetJsonDataTask extends AsyncTask<String, Void, HashMap<String, String>> {
        @Override
        protected HashMap<String, String> doInBackground(String... urls) {
            mDataHashMap = null;
            HashMap<String, String> resultHashMap = new HashMap<>();
            try {
                URL url = new URL(urls[0] + "getTableData?name=soil_data");
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder responseData = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    responseData.append(line);
                }
                bufferedReader.close();
                inputStream.close();
                httpURLConnection.disconnect();

                String parsed[] = responseData.toString().split("\\|");
                if (parsed[0].equals("ok") && parsed[1].equals("0")) {
                    String dataString = parsed[2];
                    JSONArray jsonArray = new JSONArray(dataString);
                    if (jsonArray.length() > 0) {
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        resultHashMap.put("temp", jsonObject.getString("tm"));
                        resultHashMap.put("humid", jsonObject.getString("hm"));
                        resultHashMap.put("light", jsonObject.getString("lt"));
                        resultHashMap.put("ph", jsonObject.getString("ph"));
                        resultHashMap.put("nitro", jsonObject.getString("n"));
                        resultHashMap.put("phos", jsonObject.getString("p"));
                        resultHashMap.put("pota", jsonObject.getString("k"));
                        resultHashMap.put("ec", jsonObject.getString("ec"));
                        resultHashMap.put("ts", jsonObject.getString("ts"));
                    }
                }

                URL url2 = new URL(urls[0] + "getTableData?name=plant_manage");
                HttpURLConnection httpURLConnection2 = (HttpURLConnection) url2.openConnection();

                InputStream inputStream2 = httpURLConnection2.getInputStream();
                BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(inputStream2));
                String line2;
                StringBuilder responseData2 = new StringBuilder();
                while ((line2 = bufferedReader2.readLine()) != null) {
                    responseData2.append(line2);
                }
                bufferedReader2.close();
                inputStream2.close();
                httpURLConnection2.disconnect();

                String parsed2[] = responseData2.toString().split("\\|");
                if (parsed2[0].equals("ok") && parsed2[1].equals("0")) {
                    String dataString2 = parsed2[2];
                    JSONArray jsonArray2 = new JSONArray(dataString2);
                    if (jsonArray2.length() > 0) {
                        JSONObject jsonObject2 = jsonArray2.getJSONObject(0); //자동=1, 수동=0
                        if (jsonObject2.optString("w_auto").equals("0"))
                            water = true;   //수동
                        else if (jsonObject2.optString("w_auto").equals("1"))
                            water = false;
                        if (jsonObject2.optString("l_auto").equals("0")) {
                            light0 = true;
                            if (jsonObject2.optString("l_on").equals("1"))
                                light1 = true;
                            else if (jsonObject2.optString("l_on").equals("0"))
                                light1 = false;
                        } else if (jsonObject2.optString("l_auto").equals("1")) {
                            light0 = false;
                            if (jsonObject2.optString("l_on").equals("1"))
                                light1 = true;
                            else if (jsonObject2.optString("l_on").equals("0"))
                                light1 = false;
                        }
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return resultHashMap;
        }

        @Override
        public void onPostExecute(HashMap<String, String> resultHashMap) {
            mDataHashMap = resultHashMap;
            updateDataTextView();
        }
    }


    private class GetPlantManageDataTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url2 = new URL(urls[0]+"getTableData?name=plant_manage");
                HttpURLConnection httpURLConnection2 = (HttpURLConnection) url2.openConnection();

                InputStream inputStream2 = httpURLConnection2.getInputStream();
                BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(inputStream2));
                String line2;
                StringBuilder responseData2 = new StringBuilder();
                while ((line2 = bufferedReader2.readLine()) != null) {
                    responseData2.append(line2);
                }
                bufferedReader2.close();
                inputStream2.close();
                httpURLConnection2.disconnect();

                String parsed2[] = responseData2.toString().split("\\|");
                if (parsed2[0].equals("ok") && parsed2[1].equals("0")) {
                    String dataString2 = parsed2[2];
                    JSONArray jsonArray2 = new JSONArray(dataString2);
                    if (jsonArray2.length() > 0) {
                        JSONObject jsonObject2 = jsonArray2.getJSONObject(0); //자동=1, 수동=0
                        if (jsonObject2.optString("w_auto").equals("0"))
                            water = true;   //수동
                        else if (jsonObject2.optString("w_auto").equals("1"))
                            water = false;
                        if (jsonObject2.optString("l_auto").equals("0")) {
                            light0=true;
                            if (jsonObject2.optString("l_on").equals("1"))
                                light1=true;
                            else if (jsonObject2.optString("l_on").equals("0"))
                                light1=false;
                        }
                        else if (jsonObject2.optString("l_auto").equals("1")){
                            light0=false;
                            if (jsonObject2.optString("l_on").equals("1"))
                                light1 = true;
                            else if (jsonObject2.optString("l_on").equals("0"))
                                light1 = false;
                        }
                    }
                }else
                    return false;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        }
        @Override
        public void onPostExecute(Boolean resultBool) {
            boolean result = resultBool.booleanValue();
            if (result)
                updateDataTextView();
        }
    }

    private void updateDataTextView() {
        if (mDataHashMap != null) {
            String temp = mDataHashMap.get("temp");
            String humid = mDataHashMap.get("humid");
            String light = mDataHashMap.get("light");
            String ph = mDataHashMap.get("ph");
            String nitro = mDataHashMap.get("nitro");
            String phos = mDataHashMap.get("phos");
            String pota = mDataHashMap.get("pota");
            String ec = mDataHashMap.get("ec");
            String ts = mDataHashMap.get("ts");
            TextView tempText = findViewById(R.id.temp);
            TextView humidText = findViewById(R.id.humid);
            TextView lightText = findViewById(R.id.light);
            TextView phText = findViewById(R.id.ph);
            TextView nitroText = findViewById(R.id.nitro);
            TextView phosText = findViewById(R.id.phos);
            TextView potaText = findViewById(R.id.pota);
            TextView ecText = findViewById(R.id.ec);
            TextView rTxt = findViewById(R.id.rText);
            tempText.setText(temp);
            humidText.setText(humid);
            lightText.setText(light);
            phText.setText(ph);
            nitroText.setText(nitro);
            phosText.setText(phos);
            potaText.setText(pota);
            ecText.setText(ec);
            if (ts != null)
                rTxt.setText("마지막 업데이트 시간 : " + ts);  //서버의 업데이트시간 불러오기
            AppCompatButton waterBtn = findViewById(R.id.nowWater);
            ToggleButton toggleButton = findViewById(R.id.toggleButton);
            waterBtn.setEnabled(water);
            toggleButton.setEnabled(light0);
            toggleButton.setChecked(light1);

            Fragment2.temp = temp;
            Fragment2.humid = humid;
            Fragment2.light = light;
            Fragment2.nitro = nitro;
            Fragment2.phos = phos;
            Fragment2.pota = pota;
            Fragment2.ec = ec;
            Fragment2.ph = ph;
            Fragment3.humid = humid;
            Fragment3.light = light;

            if (!popup.plant.equals("")) {
                ChatGPT chatGPT = new ChatGPT();
                new Thread() {
                    public void run() {
                        org.json.simple.JSONObject scoreResponse = chatGPT.score(popup.plant, Double.parseDouble(temp), Double.parseDouble(humid), Double.parseDouble(nitro), Double.parseDouble(phos), Double.parseDouble(pota), Double.parseDouble(ph), Double.parseDouble(ec), Double.parseDouble(light));
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONObject json = new JSONObject(scoreResponse);
                                    String scoreString = json.getString("총점");
                                    score = Float.parseFloat(scoreString);
                                    setFace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }.start();
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "식물이름 등록바람", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    public void setBlackFace() {
        setBlackImage(smileface, R.drawable.smileface1);
        setBlackImage(noface, R.drawable.noface1);
        setBlackImage(angryface, R.drawable.angryface1);
    }

    public void setFace() {
        setBlackFace();
        try {
            if (score >= 80)
                setColorImage(smileface, R.drawable.smileface);
            else if (score >= 50)
                setColorImage(noface, R.drawable.noface);
            else if (score > 1)
                setColorImage(angryface, R.drawable.angryface);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setBlackImage(ImageView imageView, int resourceId) {
        if (imageView != null) {
            Resources resources = getResources();
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resourceId);
            BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
            imageView.setImageDrawable(drawable);
        }
    }

    public void setColorImage(ImageView imageView, int resourceId) {
        if (imageView != null) {
            Resources resources = getResources();
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resourceId);
            BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
            imageView.setImageDrawable(drawable);
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
            boolean allPermissionsGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (!allPermissionsGranted) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 권한 동의 결과 처리
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
        }
    }
}