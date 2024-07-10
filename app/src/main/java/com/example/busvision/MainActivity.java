package com.example.busvision;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.busvision.ml.Busmodel;
import com.example.busvision.ml.Doormodel;
import com.example.busvision.ml.Stopmodel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {
    // 화면-코드 연결
    ImageView stop;
    ImageView bus;
    ImageView door;
    TextView stopTxt;
    TextView busTxt;
    TextView doorTxt;
    TextView modeTxt;

    // Finding View
    View fdPop;
    TextView fdTitle;
    TextView fdContext;

    // FoundStop View
    View fsPop;
    TextView fsTitle;
    TextView fsContext1;
    TextView fsContext2;
    RecyclerView fsRecycle;

    // 카메라
    private final int MY_PERMISSIONS_REQUEST_CAMERA = 1001;
    Preview preview = new Preview.Builder().build();
    PreviewView previewView;
    int lensFacing = CameraSelector.LENS_FACING_BACK;
    ProcessCameraProvider processCameraProvider;
    ImageCapture imageCapture;

    // GPS
    private final int MY_PERMISSIONS_LOCATION = 100;
    LocationManager locationManager;
    final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location_) {
            lati = location_.getLatitude();
            glong = location_.getLongitude();

            System.out.println("LATI :" + lati + " " + "LONG :" + glong);
        }
    };;
    double lati, glong;

    // TTS
    TextToSpeech tts;

    // STT
    Intent intent;
    final int PERMISSION = 1;

    // Recycler View
    LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    UserAdapter adapter = new UserAdapter();

    // API
    private OkHttpClient client = new OkHttpClient();
    private MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    String serviceKey = "T8aTxlHEaonYss%2BarjTmxYdFTEk5y%2FGWpOFXPibDQ7a98I%2Fjimly0PjpIL01pVFNcN3tNARXitmc58WsBBC%2FUg%3D%3D";

    // Thread
    Thread tr = new Thread(() -> getStopInfo(serviceKey, lati, glong));

    // API - Information

    // 모드
    int mode = 1; // 1: stop, 2: bus, 3: door
    public void changeMode(int md) {
        // 변수
        int stopClr, busClr, doorClr;
        String txt = "BusVision: ";

        // 모드별로 변하는거
        if (md == 1) {
            stopClr = Color.WHITE;
            busClr = Color.GRAY;
            doorClr = Color.GRAY;
            txt += "정류소 찾기";
        } else if (md == 2) {
            stopClr = Color.GRAY;
            busClr = Color.WHITE;
            doorClr = Color.GRAY;
            txt += "버스 찾기";
        } else {
            stopClr = Color.GRAY;
            busClr = Color.GRAY;
            doorClr = Color.WHITE;
            txt += "문 찾기";
        }

        // 모드 텍스트, 변수 변경
        mode = md;
        modeTxt.setText(txt);

        // 색상 변경
        stop.setColorFilter(stopClr);
        stopTxt.setTextColor(stopClr);
        bus.setColorFilter(busClr);
        busTxt.setTextColor(busClr);
        door.setColorFilter(doorClr);
        doorTxt.setTextColor(doorClr);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        stop = findViewById(R.id.StopModeImg);
        bus = findViewById(R.id.BusModeImg);
        door = findViewById(R.id.DoorModeImg);
        stopTxt = findViewById(R.id.StopModeTxt);
        busTxt = findViewById(R.id.BusModeTxt);
        doorTxt = findViewById(R.id.DoorModeTxt);
        modeTxt = findViewById(R.id.modename);
        previewView = findViewById(R.id.cameraView);

        // Finding Something view
        fdPop = findViewById(R.id.finding_pop);
        fdTitle = findViewById(R.id.finding_title);
        fdContext = findViewById(R.id.finding_text);

        // FoundStop View
        fsPop = findViewById(R.id.foundstop_pop);
        fsTitle = findViewById(R.id.foundstop_title);
        fsContext1 = findViewById(R.id.foundstop_text1);
        fsContext2 = findViewById(R.id.foundstop_text2);
        fsRecycle = findViewById(R.id.foundstop_routes);



        int permssionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (permssionCheck != PackageManager.PERMISSION_GRANTED) {

            toastMsg("권한 승인이 필요합니다");

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                toastMsg( "BusVision 사용을 위해 카메라 권한이 필요합니다.");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
                toastMsg("BusVision 사용을 위해 카메라 권한이 필요합니다.");

            }
        }


        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        try {
            processCameraProvider = ProcessCameraProvider.getInstance(this).get();
        }
        catch (ExecutionException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        previewView.setVisibility(View.VISIBLE);
        bindPreview();

        try {
            imageCapture = new ImageCapture.Builder().setTargetRotation(previewView.getDisplay().getRotation()).build();
        } catch (Exception e) {
            imageCapture = new ImageCapture.Builder().build();
        }

        changeMode(1);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_LOCATION);
            return;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, -1, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, -1, locationListener);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!= TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        if ( Build.VERSION.SDK_INT >= 23 ){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO}, PERMISSION);
        }
        intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");   // 텍스트로 변환시킬 언어 설정

//        top.setOnClickListener(v -> {
//            SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
//            mRecognizer.setRecognitionListener(listener);
//            mRecognizer.startListening(intent);
//        });
//
//        fsPop.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) { useTTS(1, "성남역", "720-1번 이매촌 안말고교 방면"); }
//        });
//

        fsPop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap img =  captureImage();
                if (mode == 1) {
                    if (isStop(img)) {
                        System.out.println("あ： LATI: " + lati);
                        System.out.println("あ： LONG: " + glong);
                        toastMsg("Lati: "+ lati + ", Long: " + glong, false);
                        if (tr.getState() == Thread.State.NEW) {
                            tr.start();
                        }
                    }
                } else if (mode == 2) {
                    if (isBus(img)) {
                        System.out.println("あ： It is Bus!!");
                    }
                } else if (mode == 3) {
                    /*
                    ----문----
                    1: 좌측
                    2: 전방
                    3: 우측
                    4: 버스아님
                    5: 오류
                     */
                    int whereIsDoor = whereDoor(img);

                    switch (whereIsDoor) {
                        case 1: {
                            System.out.println("あ： left (bus's front)");
                            break;
                        }
                        case 2: {
                            System.out.println("あ： front (bus's door)");
                            break;
                        }
                        case 3: {
                            System.out.println("あ： right (bus's back or side)");
                            break;
                        }
                        case 4: {
                            System.out.println("あ： this is not bus");
                            break;
                        }
                        case 5: {
                            toastMsg("오류가 발생하였습니다.");
                            break;
                        }
                    }
                }
            }
        });

        fsRecycle.setLayoutManager(layoutManager);
        fsRecycle.setAdapter(adapter);

        adapter.addItem(new UserAdapter.Item("11-3", "종합운동장방면", 1));
        adapter.addItem(new UserAdapter.Item("402", "수서역방면", 2));
        adapter.addItem(new UserAdapter.Item("4319", "잠실역방면", 3));
        adapter.addItem(new UserAdapter.Item("G3202", "삼성역방면", 4));
        adapter.addItem(new UserAdapter.Item("강남02", "대치역방면", 5));
        adapter.addItem(new UserAdapter.Item("6009", "인천국제공항방면", 6));
        adapter.addItem(new UserAdapter.Item("43850", "용당소방면", 7));

        adapter.notifyDataSetChanged();



        findSomethingView(false);

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(1);
            }
        });

        stopTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(1);
            }
        });

        bus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(2);
            }
        });

        busTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(2);
            }
        });

        door.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(3);
            }
        });

        doorTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMode(3);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    toastMsg("승인이 허가되어 있습니다.");

                } else {
                    toastMsg( "아직 승인받지 않았습니다.");
                }
                return;
            }

            case MY_PERMISSIONS_LOCATION: {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
                }
            }

        }
    }
    void bindPreview() {
        int aspectRatio = getAspectRatio();

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(aspectRatio) // 화면 비율 설정
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        processCameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private int getAspectRatio() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        return AspectRatio.RATIO_4_3; // 또는 적절한 화면 비율 선택
    }

    void useTTS(int mode, String titleText, String detail) {
        String script="";

        switch (mode) {
            case 1: {
                script += titleText + "정류소를 찾았습니다. ";
                script += "이 정류소의 정차노선은, " + detail + "입니다. ";
                script += "이용하시려는 노선이 있습니까?";
                break;
            }
            case 2: {
                script += titleText + "번 버스를 찾았습니다. ";
                script += "이 버스는, " + detail + "입니다. ";
                script += "승차하시겠습니까?";
                break;
            }
            case 3: {
                script += "문을 찾았습니다. ";
                script += "사용자의 " + detail + "에 문이 있습니다.";
                break;
            }
        }

        speak(script);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void speak(String text) {
        tts.setPitch(1.0f);
        tts.setSpeechRate(1.5f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            toastMsg("음성인식을 시작합니다.");
        }

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            String message;

            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "오디오 에러";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "클라이언트 에러";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "퍼미션 없음";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "네트워크 에러";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "네트웍 타임아웃";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "찾을 수 없음";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER가 바쁨";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "서버가 이상함";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "말하는 시간초과";
                    break;
                default:
                    message = "알 수 없는 오류임";
                    break;
            }

            Toast.makeText(getApplicationContext(), "에러가 발생하였습니다. : " + message,Toast.LENGTH_SHORT).show();
            speak("에러가 발생하였습니다. : " + message);
        }

        @Override
        public void onResults(Bundle results) {
            // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어준다.
            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            for(int i = 0; i < matches.size() ; i++){
                toastMsg(matches.get(i));
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };

    void findSomethingView(boolean visibility) {
        if (visibility) {
            fdPop.setVisibility(View.VISIBLE);
            fdTitle.setVisibility(View.VISIBLE);
            fdContext.setVisibility(View.VISIBLE);
        } else {
            fdPop.setVisibility(View.GONE);
            fdTitle.setVisibility(View.GONE);
            fdContext.setVisibility(View.GONE);
        }
    }

    void foundStopView(boolean visibility) {
        if (visibility) {
            fsPop.setVisibility(View.VISIBLE);
            fsTitle.setVisibility(View.VISIBLE);
            fsContext1.setVisibility(View.VISIBLE);
            fsContext2.setVisibility(View.VISIBLE);
            fsRecycle.setVisibility(View.VISIBLE);
        } else {
            fsPop.setVisibility(View.GONE);
            fsTitle.setVisibility(View.GONE);
            fsContext1.setVisibility(View.GONE);
            fsContext2.setVisibility(View.GONE);
            fsRecycle.setVisibility(View.GONE);
        }
    }

    ///TODO: API교체하기 + XML->JSON convert하기!
    // api: https://www.data.go.kr/tcs/dss/selectApiDataDetailView.do?publicDataPk=15000303
    public boolean getStopInfo(@NonNull String key, @NonNull double gpsLati, @NonNull double gpsLong) {
        try {

            String citycode, nodeid, nodenm, nodeno;

            String url
                    = "http://ws.bus.go.kr/api/rest/stationinfo/getStationByPos?serviceKey=" + key
                    + "&tmX=" + gpsLati
                    + "&tmY=" + gpsLong
                    + "&radius=500m";
            System.out.println(url);
            // OkHttp 클라이언트 객체 생성
            OkHttpClient client = new OkHttpClient();

            // GET 요청 객체 생성
            Request.Builder builder = new Request.Builder().url(url).get();
            Request request = builder.build();

            // OkHttp 클라이언트로 GET 요청 객체 전송
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                // 응답 받아서 처리
                ResponseBody body = response.body();
                if (body != null) {
                    JSONObject json = new JSONObject(body.string());

                    JSONObject res = json.getJSONObject("response");
                    JSONObject resBody = res.getJSONObject("body");
                    String totalCnt = resBody.getString("totalCount");
                    if(!totalCnt.equals("0")) {
                        JSONObject resBodyItems = resBody.getJSONObject("items");
                        JSONArray resBodyItemsItem = resBodyItems.getJSONArray("item");
                        JSONObject data = resBodyItemsItem.getJSONObject(0);

                        citycode = data.getString("citycode");
                        nodeid = data.getString("nodeid");
                        nodenm = data.getString("nodenm");
                        nodeno = data.getString("nodeno");

                        System.out.println("あ： city-" + citycode + " nid-" + nodeid + " nnm-" + nodenm + " nno-" + nodeno);
                    } else {
                        System.out.println("あ： 500m내에 정류소 없음");
                    }

                }
            }
            else {
                System.err.println("Error Occurred");
            }

            return true;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("あ: "+ e);
        }

        return false;
    }

    boolean isStop(Bitmap image1) {
        try {
            Bitmap image = Bitmap.createScaledBitmap(image1, 224, 224, true);

            Stopmodel model = Stopmodel.newInstance(getApplicationContext());

            int imageSize = 224;

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, imageSize, imageSize, 3}, DataType.FLOAT32);

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // get 1D array of 224 * 224 pixels in image
            int [] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            int pixel = 0;
            for(int i = 0; i < imageSize; i++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Stopmodel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();
            // find the index of the class with the biggest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for(int i = 0; i < confidences.length; i++){
                if(confidences[i] > maxConfidence){
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }
            String[] classes = {"Stop", "No"};

            model.close();
            return classes[maxPos].equals("Stop");
        } catch (IOException e) {
            return false;
        }
    }

    boolean isBus(Bitmap image1){
        try {
            Bitmap image = Bitmap.createScaledBitmap(image1, 224, 224, true);

            Busmodel model = Busmodel.newInstance(getApplicationContext());

            int imageSize = 224;

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, imageSize, imageSize, 3}, DataType.FLOAT32);

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // get 1D array of 224 * 224 pixels in image
            int [] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            int pixel = 0;
            for(int i = 0; i < imageSize; i++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Busmodel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();
            // find the index of the class with the biggest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for(int i = 0; i < confidences.length; i++){
                if(confidences[i] > maxConfidence){
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }
            String[] classes = {"Bus", "No"};

            model.close();
            return classes[maxPos].equals("Bus");
        } catch (IOException e) {
            return false;
        }

    }

    /*
    ----문----
    1: 좌측
    2: 전방
    3: 우측
    4: 버스아님
    5: 오류
     */
    int whereDoor(Bitmap image1) {
        int imageSize = 224;
        try {
            Bitmap image = Bitmap.createScaledBitmap(image1, 224, 224, true);
            Doormodel model = Doormodel.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // get 1D array of 224 * 224 pixels in image
            int [] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            int pixel = 0;
            for(int i = 0; i < imageSize; i++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Doormodel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();
            // find the index of the class with the biggest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for(int i = 0; i < confidences.length; i++){
                if(confidences[i] > maxConfidence){
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }
            String[] classes = {"door", "front", "back", "no"};

            model.close();

            switch (classes[maxPos]) {
                case "front": {
                    return 1;
                }
                case "door": {
                    return 2;
                }
                case "back": {
                    return 3;
                }
                case "no": {
                    return 4;
                }
            }
        } catch (IOException e) {
            return 5;
        }
        return 5;
    }

    public Bitmap captureImage() {
        processCameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture);

        File photoFile = new File(
                getApplicationContext().getCacheDir(),
                "busvision_image.jpg"
        );

        if (imageCapture == null) return null;

        // ImageCapture.OutputFileOptions는 새로 캡처한 이미지를 저장하기 위한 옵션
        // 저장 위치 및 메타데이터를 구성하는데 사용
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // takePicture를 통해 사진을 촬영.
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {}

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        toastMsg("사진 촬영에 실패하였습니다.");
                        System.out.println("あ (エラー): " + exception);
                    }
                }
        );

        String filePath = photoFile.getPath();
        return BitmapFactory.decodeFile(filePath);
    }

    void toastMsg(@NonNull String text){
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        speak(text);
    }

    void toastMsg(@NonNull String text, boolean spk){
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        if(spk)
            speak(text);
    }




}