package com.example.busvision;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.busvision.ml.Stopmodel;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    // TEST
    final boolean DEBUG = false;

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

    // FoundBus View
    View fbPop;
    TextView fbTitle;
    TextView fbContext;

    // FoundDoor View
    View fndPop;
    TextView fndTitle;
    TextView fndContext;

    // 카메라
    private final int MY_PERMISSIONS_REQUEST_CAMERA = 1001;
    Preview preview = new Preview.Builder().build();
    PreviewView previewView;
    int lensFacing = CameraSelector.LENS_FACING_BACK;
    ProcessCameraProvider processCameraProvider;
    ImageCapture imageCapture;
    String filePath;

    // scr
    int nowScreen = 0;

    // firebase Storage
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();

    // GPS
    private final int MY_PERMISSIONS_LOCATION = 100;
    LocationManager locationManager;
    final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location_) {
            lati = location_.getLatitude();
            glong = location_.getLongitude();

        }
    };;
    double lati, glong;

    // TTS
    TextToSpeech tts;

    // STT
    Intent intent;
    final int PERMISSION = 1;
    String ans = "";
    SpeechRecognizer mRecognizer;
    boolean isSpeaking = false;

    // Recycler View
    LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    UserAdapter adapter = new UserAdapter();
    String nowStop = "234567890876543234567898765432345678";

    // GPT
    OkHttpClient client;
    public static  final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MY_SECRET_KEY = "<OPENAI_API_KEY>";
    final String imageUrl = "https://firebasestorage.googleapis.com/v0/b/busvision-92f29.appspot.com/o/busvision_image.jpg?alt=media&token=6c8d67b5-0f14-4324-a099-9e2a118943f4";
    final String[] ansForGpt = {"Error"};
    boolean isEmpty = false;

    // 모드
    int mode = 1; // 1: stop, 2: bus, 3: door
    public void changeMode(int md) {
        // 변수
        int stopClr, busClr, doorClr;
        String txt = "BusVision: ";

        // 모드별로 변하는거
        if (md == 1) {
            try {
                speak("정류소 찾기 모드입니다.");
            } catch (Exception ignored) {}
            stopClr = Color.WHITE;
            busClr = Color.GRAY;
            doorClr = Color.GRAY;
            txt += "정류소 찾기";
            fdTitle.setText("정류소 찾기");
            fdContext.setText("정류소를 찾고있습니다...");
            findSomethingView(true);
            foundStopView(false);
            foundBusView(false);
            foundDoorView(false);
            nowScreen=0;
        } else if (md == 2) {
            try {
                speak("버스 찾기 모드입니다.");
            } catch (Exception ignored) {}
            stopClr = Color.GRAY;
            busClr = Color.WHITE;
            doorClr = Color.GRAY;
            txt += "버스 찾기";
            fdTitle.setText("버스 찾기");
            fdContext.setText("버스를 찾고있습니다...");
            findSomethingView(true);
            foundStopView(false);
            foundBusView(false);
            foundDoorView(false);
            nowScreen=2;
        } else {
            try {
                speak("문 찾기 모드입니다.");
            } catch (Exception ignored) {}
            stopClr = Color.GRAY;
            busClr = Color.GRAY;
            doorClr = Color.WHITE;
            txt += "문 찾기";
            fdTitle.setText("문 찾기");
            fdContext.setText("문을 찾고있습니다...");
            findSomethingView(true);
            foundStopView(false);
            foundBusView(false);
            foundDoorView(false);
            nowScreen=4;
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

        // 다른거
        stopSTT();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        // FoundBus View
        fbPop = findViewById(R.id.foundbus_pop);
        fbTitle = findViewById(R.id.foundbus_title);
        fbContext = findViewById(R.id.foundbus_text);

        // FoundDoor View
        fndPop = findViewById(R.id.founddoor_pop);
        fndTitle = findViewById(R.id.founddoor_title);
        fndContext = findViewById(R.id.founddoor_text);

        // gpt
        client = new OkHttpClient().newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();


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

        //TODO: 임시로 기본모드 2로 해놨음
        changeMode(2);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_LOCATION);
            return;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, -1, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100, -1, locationListener);

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

        findSomethingView(true);
        foundStopView(false);
        foundBusView(false);
        foundDoorView(false);

        fsRecycle.setLayoutManager(layoutManager);
        fsRecycle.setAdapter(adapter);

        adapter.notifyDataSetChanged();

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

        if (!DEBUG) {
            Timer timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            toastMsg("작동시작~!");
                            Bitmap img;
                            if (mode == 1 && nowScreen == 0) {
                                img = captureImage();
                                if (isStop(img) && lati > 0 && glong > 0) {
                                    try {
                                        nowScreen = 1;
                                        getStopInfo(lati, glong);
                                    } catch (IOException | CsvException e) {
                                        toastMsg("오류가 발생하였습니다.");
                                    }
                                }
                            } else if (mode == 2 && nowScreen == 2) {
                                captureImage();
                                if (isBus()) {
                                    nowScreen = 3;
                                    callAPI("이건 몇번 버스인지 알수 있어? 알수 있다면 번호만, 알 수 없으면 null이라고만 답변해줘 ", imageUrl);
                                    while (isEmpty) { }
                                    String busNm = ansForGpt[0];

                                    fbTitle.setText(busNm + "번 버스를 찾았습니다.");

                                    findSomethingView(false);
                                    foundBusView(true);
                                    foundDoorView(false);

                                    System.out.println(busNm);
                                    if (Objects.equals(busNm, "null")) {
                                        nowScreen=2;
                                        mode=2;
                                        toastMsg("노선 번호를 읽을 수 없습니다..");
                                    } else {
                                        useTTS(2, busNm, "");
                                        while (tts.isSpeaking()) {
                                        }
                                        useSTT();
                                    }
                                }
                            } else if (mode == 3 && nowScreen == 4) {
                                captureImage();
                            /*
                            ----문----
                            1: 좌측
                            2: 전방
                            3: 우측
                            4: 버스아님
                            5: 오류
                             */
                                int whereIsDoor = whereDoor();

                                switch (whereIsDoor) {
                                    case 1: {
                                        nowScreen = 5;
                                        useTTS(3,"","좌측");
                                        fndContext.setText("사용자로부터 좌측에 문이 있습니다.");
                                        foundDoorView(true);
                                        findSomethingView(false);
                                        break;
                                    }
                                    case 2: {
                                        nowScreen = 5;
                                        useTTS(3,"","전방");
                                        fndContext.setText("사용자로부터 전방에 문이 있습니다.");
                                        foundDoorView(true);
                                        findSomethingView(false);
                                        break;
                                    }
                                    case 3: {
                                        nowScreen = 5;
                                        useTTS(3,"","우측");
                                        fndContext.setText("사용자로부터 우측에 문이 있습니다.");
                                        foundDoorView(true);
                                        findSomethingView(false);
                                        break;
                                    }
                                    case 4: {
                                        break;
                                    }
                                    case 5: {
                                        toastMsg("오류가 발생하였습니다");
                                        break;
                                    }
                                }
                            }
                        }
                    });
                }
            };

            timer.schedule(timerTask, 1000, 60000);
        } else {
            previewView.setVisibility(View.GONE);
            Button gptTest1 = findViewById(R.id.gptTest1);
            Button gptTest2 = findViewById(R.id.gptTest2);

            gptTest1.setVisibility(View.VISIBLE);
            gptTest2.setVisibility(View.VISIBLE);

            gptTest1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callAPI("너는 누구니?", null);
                }
            });

            gptTest2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callAPI("이 이미지를 묘사해줘", "https://firebasestorage.googleapis.com/v0/b/login-register-example-483bd.appspot.com/o/v8JjSOBS3fVxftuJFJ8cRihKMQn2%2Fcarbon%20(11).png?alt=media&token=fe6f202e-374e-41b9-99f0-4a69dd7ee8b7");
                }
            });
        }

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
                script += "이용하시려는 노선이 있습니까? 네 또는 아니오로 답해주십시오.";
                break;
            }
            case 2: {
                script += titleText + "번 버스를 찾았습니다. ";
                if (!detail.isEmpty())
                    script += "이 버스는, " + detail + "입니다. ";
                script += "승차하시겠습니까? 네 또는 아니오로 답해주십시오.";
                break;
            }
            case 3: {
                script += "문을 찾았습니다. ";
                script += "사용자로부터 " + detail + "에 문이 있습니다.";
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
            toastMsg("음성인식을 시작합니다.", false);
            System.out.println("お");
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
            stopSTT();
            toastMsg("다시 말씀해주세요");
            useSTT();
        }

        @Override
        public void onResults(Bundle results) {
            // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어준다.
            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            ans = "";
            for(int i = 0; i < matches.size() ; i++){
                ans += matches.get(i);
            }
            stopSTT();

            int yesMd=mode+1, noMd=mode;

            if (ans.equals("네") || ans.equals("예")) {
                changeMode(yesMd);
            } else if (ans.equals("아니오") || ans.equals("아니요")) {
                changeMode(noMd);
            } else {
                toastMsg("다시 말씀해주세요");
                useSTT();
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

    void foundBusView(boolean visibility) {
        if (visibility) {
            fbPop.setVisibility(View.VISIBLE);
            fbTitle.setVisibility(View.VISIBLE);
            fbContext.setVisibility(View.VISIBLE);
        } else {
            fbPop.setVisibility(View.GONE);
            fbTitle.setVisibility(View.GONE);
            fbContext.setVisibility(View.GONE);
        }
    }

    void foundDoorView(boolean visibility) {
        if (visibility) {
            fndPop.setVisibility(View.VISIBLE);
            fndTitle.setVisibility(View.VISIBLE);
            fndContext.setVisibility(View.VISIBLE);
        } else {
            fndPop.setVisibility(View.GONE);
            fndTitle.setVisibility(View.GONE);
            fndContext.setVisibility(View.GONE);
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

    public void getStopInfo(@NonNull double gpsLati, @NonNull double gpsLong) throws IOException, CsvException {
        if (gpsLati==0.0 && gpsLong==0.0) return;
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open("bus.csv");
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        List<String[]> allContent = csvReader.readAll();

        float[] distance = {2147483647};
        Location watashi = new Location("watashi");
        watashi.setLatitude(gpsLati);
        watashi.setLongitude(gpsLong);

        Location stopLoc = new Location("busStop");

        String[][] stopAbout = {{"56", "7", "방길", "3", "1", "일차로"}};

        allContent.forEach(stop -> {
            if(!Objects.equals(stop[5], "가상정류장")) {
                stopLoc.setLatitude(Double.parseDouble(stop[3]));
                stopLoc.setLongitude(Double.parseDouble(stop[4]));

                float dis = watashi.distanceTo(stopLoc);
                if(dis < distance[0]) {
                    String stopID, stopNO, stopNM, stopLA, stopLO, stopTP;
                    stopID=stop[0]; stopNO=stop[1]; stopNM = stop[2]; stopLA=stop[3]; stopLO=stop[4]; stopTP=stop[5];
                    if(Objects.equals(stop[5], "중앙차로")) {
                        stopNM = stopNM + "(중)";
                    }
                    if(stopNO.length() == 4) {
                        stopNO = "0"+stopNO;
                    }

                    stopAbout[0] = new String[]{stopID, stopNO, stopNM, stopLA, stopLO, stopTP};
                    distance[0] = dis;
                }
            }
        });

        inputStream = assetManager.open("busRoute.csv");
        csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        allContent = csvReader.readAll();

        ArrayList<String[]> stopRoute = new ArrayList<>();

        allContent.forEach(route -> {
            if(Objects.equals(route[3], stopAbout[0][0])) {
                stopRoute.add(new String[]{route[0], route[1], route[2]});
            }
        });


        if(!Objects.equals(nowStop, stopAbout[0][0])) {

//            String titleText = howLongThisTextIs2(stopAbout[0][2]) + " (" + stopAbout[0][1] + ")";
            String titleText = howLongThisTextIs2(stopAbout[0][2]) + " (" + stopNumberReformatting(stopAbout[0][1]) + ")";

            fsTitle.setText(titleText);
            fsContext1.setText("정류소를 찾았습니다.\n정차노선:");
            emptyRecyclerView();
            final String[] detail = {""};

            stopRoute.forEach(line -> {
                try {
                    String nextStop = findNextStop(line[0],line[2]);
                    adapter.addItem(new UserAdapter.Item(howLongThisTextIs(line[1]), howLongThisTextIs2(nextStop)+"방향", whatColorIsThisBus(line[1])));
                    detail[0] += line[1] + "번 " + nextStop + "방향, ";
//                    System.out.println(detail[0]);
                } catch (IOException | CsvException e) {
                    toastMsg("오류가 발생하였습니다.");
                }
            });
            nowStop = stopAbout[0][0];
            adapter.notifyDataSetChanged();

            findSomethingView(false);
            foundStopView(true);

            useTTS(1, stopAbout[0][2], detail[0]);
            while (tts.isSpeaking()) {}
            useSTT();
        }
    }

    String findNextStop(String busID, String cnt) throws IOException, CsvException {
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open("busRoute.csv");
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        final String[] nxt = {"종점"};

        List<String[]> allContent = csvReader.readAll();

        allContent.forEach(stop -> {
            if(Objects.equals(stop[0], busID) && Objects.equals(stop[2], Integer.toString(Integer.parseInt(cnt)+1))) {
                nxt[0] = stop[5];
            }
        });
        return nxt[0];
    }

    String howLongThisTextIs(String txt) {
        if(Objects.equals(txt, "청와대A01")) return "A01";
        if(Objects.equals(txt, "심야A21")) return "A21";
        return txt;
    }

    String howLongThisTextIs2(String txt) {
        String cp = txt;
        if(cp.length() >= 9)
            cp = cp.substring(0,8)+"...";
        return cp;
    }

    String stopNumberReformatting(String number) {
        return number.substring(0,2)+"-"+number.substring(2);
    }

    void emptyRecyclerView() {
        adapter = new UserAdapter();
        fsRecycle.setAdapter(adapter);
    }

    int whatColorIsThisBus(String number) throws IOException, CsvException {
        AssetManager assetManager = getAssets();
        InputStream inputStream = assetManager.open("busInfo.csv");
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        List<String[]> allContent = csvReader.readAll();

        int res = 7;
        for (String[] line : allContent) {
            if (Objects.equals(number, line[1])) {
                String tp = line[2];
                if(Objects.equals(tp, "간선")) res = 2;
                if(Objects.equals(tp, "지선")) res = 3;
                if(Objects.equals(tp, "광역")) res = 4;
                if(Objects.equals(tp, "순환") || Objects.equals(tp, "마을") || Objects.equals(tp, "관광")) res = 5;
                if(Objects.equals(tp, "공항")) res = 6;
                if(Objects.equals(tp, "동행")) {
                    if(Objects.equals(number, "서울01") || Objects.equals(number, "서울03") || Objects.equals(number, "서울06")) res = 4;
                    else res = 2;
                }
            }
        }

        return res;
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

    boolean isBus(){
        uploadFile(
                new File(filePath)
        );

        callAPI("이건 버스니? 맞다면 'true', 아니라면 'false'라고만 대답해줘", imageUrl);
        while (isEmpty) { }
        String gptRes = ansForGpt[0];

        if (Objects.equals(gptRes, "true")) return true;
        return false;
    }

    /*
    ----문----
    1: 좌측
    2: 전방
    3: 우측
    4: 버스아님
    5: 오류
     */
    int whereDoor() {
        try {
            uploadFile(
                new File(filePath)
            );

            callAPI("내가 지금 사진에 나와있는 대로 보고있다고 가정할게, 그렇다면 우리쪽에서 버스의 '앞'문은 어디에 있는지 정확히 묘사해줘. '우리쪽'에서 좌측이면 1, 전방에 있으면 2, 우측에 있으면 3, 버스를 인식하지 못했다면 4라고'만' 대답해줘,", imageUrl);
            while (isEmpty) {
            }
            String gptRes = ansForGpt[0];

            return Integer.parseInt(gptRes);
        } catch (Exception e) {
            return 5;
        }
    }

    public Bitmap captureImage() {
        processCameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture);

        File photoFile = new File(
                getExternalCacheDir().getAbsoluteFile(),
                "busvision_image.jpg"
        );

//        File[] files = getExternalCacheDir().getAbsoluteFile().listFiles();
//        System.out.println(getExternalCacheDir().getAbsoluteFile().toString());
//        for (File file : files) {
//            System.out.println(file.getName());
//        }

        if (imageCapture == null) return null;

        // ImageCapture.OutputFileOptions는 새로 캡처한 이미지를 저장하기 위한 옵션
        // 저장 위치 및 메타데이터를 구성하는데 사용
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
//        imageCapture = new ImageCapture.Builder()
//                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
//                .build();
        // takePicture를 통해 사진을 촬영.
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) { }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        toastMsg("사진 촬영에 실패하였습니다.");
                        System.out.println("あ (エラー): " + exception);
                    }
                }
        );

        filePath = photoFile.getPath();
        return BitmapFactory.decodeFile(filePath);
    }

    void toastMsg(@NonNull String text){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                speak(text);
            }
        });
    }

    void toastMsg(@NonNull String text, boolean spk){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                if(spk)
                    speak(text);
            }
        });
    }

    void useSTT() {
        isSpeaking=true;
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizer.setRecognitionListener(listener);
        mRecognizer.startListening(intent);
        System.out.println("い");
    }

    public void stopSTT(){
        if(mRecognizer!=null){
            isSpeaking=false;
            mRecognizer.destroy();
            mRecognizer.cancel();
            mRecognizer = null;
        }
    }

    void callAPI(String question, String imageUrl){
        isEmpty=true;
        JSONArray arr = new JSONArray();
        JSONObject userMsg = new JSONObject();
        try {
            userMsg.put("role", "user");
            if (imageUrl != null) {
                JSONArray contents = new JSONArray();
                JSONObject txt = new JSONObject();
                JSONObject img = new JSONObject();
                JSONObject img2 = new JSONObject();

                txt.put("type", "text");
                txt.put("text", question);

                img.put("type", "image_url");
                img2.put("url", imageUrl);
                img.put("image_url", img2);

                contents.put(txt);
                contents.put(img);

                userMsg.put("content", contents);
            } else {
                userMsg.put("content", question);
            }


            arr.put(userMsg);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        JSONObject object = new JSONObject();
        try {
            object.put("model", "gpt-4o-mini");
            object.put("messages", arr);

        } catch (JSONException e){
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(object.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer "+MY_SECRET_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                toastMsg("오류가 발생하였습니다.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject jsonObject;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");

                        String result = jsonArray.getJSONObject(0).getJSONObject("message").getString("content");
                        ansForGpt[0] = result.trim();
                        System.out.println(ansForGpt[0]);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    isEmpty=false;
                } else {
//                    toastMsg("오류가 발생하였습니다.");
                }
            }
        });
    }

    void uploadFile(File localFile) {
        Uri file = Uri.fromFile(localFile);
        StorageReference riversRef = storageRef.child(file.getLastPathSegment());
        UploadTask uploadTask = riversRef.putFile(file);

        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
            }
        });
    }
}