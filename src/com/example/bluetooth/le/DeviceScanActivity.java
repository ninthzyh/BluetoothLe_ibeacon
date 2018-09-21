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

package com.example.bluetooth.le;

import android.app.Activity;
//import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.bluetooth.BluetoothManager;
import android.content.Context;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.example.bluetooth.le.iBeaconClass.iBeacon;

import org.apache.commons.lang.ArrayUtils;

import javax.security.auth.login.LoginException;


/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity {
	private final static String TAG = DeviceScanActivity.class.getSimpleName();
	private final static String UUID_KEY_DATA = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private final static int MUSICTYPE = 1;//message type
    private final static double AREA_SPAN = 3.0; //感应范围
    private final static int FILTER_TIMES = 10;   //消抖次数
    /**搜索BLE终端*/
    private BluetoothAdapter mBluetoothAdapter;
    private List<ibeaconDisatance> distanceList = new ArrayList<ibeaconDisatance>();
    private MediaPlayer player = null;  //各实验室介绍
//    private MediaPlayer backplayer = null; //通用介绍
    private MediaPlayer noVoiceplayer = null; //空白声音，用于衔接
    private musicThread MusicThread;
    private volatile boolean reloadLayout = true;//是否重载布局
    private volatile int changeLocationFlag = 0;
    private Handler musicHandler;
    private Handler mHandler;
    private boolean mScanning;
    Activity activity;
//    public static volatile int dFlag;
    private int[] templocation = new int[2];

    //可以滑动的界面Viewpager
    private myViewPager viewPager;
    private ArrayList<View> pageview = new ArrayList<View>();
    private TextView introductionLayout;
    private TextView curriculumLayout;
    //


    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 900000000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

        getActionBar().setTitle(R.string.title_devices);
 // TODO: 2017/6/2
        //创建数据库
        activity = this;
//        handler1.postDelayed(runnable, TIME); //每隔30s执行
        // TODO: 2017/6/2 
        mHandler = new Handler();
        setContentView(R.layout.main_view);
//        initMediaPlayer(templocation[1]);
//        initblankMusic();

        //imageView0 = (ImageView) findViewById(R.id.imageView0);
        Button button1=(Button)findViewById(R.id.button1);
        button1.setOnClickListener(new myButtonOnclicklistener());


        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        //开启蓝牙
        mBluetoothAdapter.enable();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
     if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
     {
//land
     }
     else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
     {
//port
     }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initializes list view adapter.



        scanLeDevice(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //开启一个定时器用于定时更新list中的times值，当times大于某一值时，视为长时间接收不到该
        //该ibeacon信号,则从表中删除
        initblankMusic();
        initMediaPlayer(0);
        noVoiceplayer.start();
        MusicThread = new musicThread();
        MusicThread.start();
        musicHandler = MusicThread.getHandler();
        while(musicHandler == null){
            SystemClock.sleep(100);
            musicHandler = MusicThread.getHandler();
        }
        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateiBeaconTimes(distanceList);
            }
        },500,500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();

                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
//            Log.e("onLeScan",""+ArrayUtils.toString(scanRecord));
            try {
                final iBeacon ibeacon = iBeaconClass.fromScanData(device, rssi, scanRecord);
                final nowIbeaconDisatance beaconDistance = new nowIbeaconDisatance(ibeacon.minor, ibeacon.distance);
                Log.e("rssi2",""+ibeacon.rssi);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        addElement(beaconDistance);

                        Message msg = Message.obtain();
                        msg.what = MUSICTYPE;
                        msg.arg1 = caculateLocation();
                        musicHandler.sendMessage(msg);
                        if (false) {
                            Log.e("start---------------","--");
                            for (int i = 0; i < distanceList.size(); i++) {
                                Log.e("minor", "" + distanceList.get(i).minor);
                                Log.e("now meandistance", "" + distanceList.get(i).meanDistance);
                                Log.e("now times","" + distanceList.get(i).times);
                            }
                            Log.e("end-----------------","--");
                        }
                    }
                }).start();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private void initMediaPlayer(int position){
        if(changeLocationFlag != 0){
            if(player != null) player.reset();
        }
        switch (position) {
            case 0:
//                if(player == null);
//                else if (player.isPlaying()) {
//                    player.reset();
//                }
                break;
            case 348://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a348);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 344://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a344);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 340://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a340);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 336://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a336);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 136://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a136);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 132://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a132);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 128://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a128);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 124://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a124);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 127://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a127);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            case 123://一直使用1在测试
                if(noVoiceplayer.isPlaying()){
                    noVoiceplayer.stop();
                    noVoiceplayer.prepareAsync();
                }
                if (player == null||!player.isPlaying()) {
                    player = MediaPlayer.create(getApplicationContext(), R.raw.a123);
                    player.start();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            player.reset();
                            noVoiceplayer.start();
                        }
                    });
                }
                break;
            default:

                break;
        }


    }



    private void changeImage(int position){
        switch (position) {
        case 0:
        	setContentView(R.layout.main_view);
            Button button1=(Button)findViewById(R.id.button1);
            button1.setOnClickListener(new myButtonOnclicklistener());
         	break;
        case 348:
            if(reloadLayout) {
                setContentView(R.layout.text1);
                inittextlayout();
                TextView textView1 = (TextView) findViewById(R.id.textView1);
                textView1.setText("通信原理软件实验室(348)");
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageResource(R.drawable.p340);
                String string = getResources().getString(R.string.location_348);
                TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                textView.setText(string);
                ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                imageView1.setImageResource(R.drawable.noclass);
                textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                reloadLayout = false;
            }
            break;
        case 344:
            if(reloadLayout) {
                setContentView(R.layout.text1);
                inittextlayout();
                TextView textView1 = (TextView) findViewById(R.id.textView1);
                textView1.setText("通信原理硬件实验室(344)");
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageResource(R.drawable.p344);
                String string = getResources().getString(R.string.location_344);
                TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                textView.setText(string);
                ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                imageView1.setImageResource(R.drawable.class342_346);
                textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                reloadLayout = false;
            }
			break;
		case 340:
            if(reloadLayout) {
                setContentView(R.layout.text1);
                inittextlayout();
                TextView textView1 = (TextView) findViewById(R.id.textView1);
                textView1.setText("通信系统仿真实验室(340)");
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageResource(R.drawable.p340);
                String string = getResources().getString(R.string.location_340);
                TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                textView.setText(string);
                ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                imageView1.setImageResource(R.drawable.class340);
                textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                reloadLayout = false;
            }
        	break;
            case 336:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("西邮-XILINX通信信号处理联合实验室(336)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p336);
                    String string = getResources().getString(R.string.location_336);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.noclass);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 136:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("西邮-网经科技融合通信实验室(136)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p136);
                    String string = getResources().getString(R.string.location_136);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.noclass);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 132:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("华为C&C08数字程控交换实验室(132)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p132);
                    String string = getResources().getString(R.string.location_132);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.class132);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 128:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("武邮-西邮光通信与宽带接入联合共建实验室(128)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p128);
                    String string = getResources().getString(R.string.location_128);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.noclass);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 124:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("TD-SCDMA无线专网实验室(124)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p124);
                    String string = getResources().getString(R.string.location_124);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.noclass);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 127:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("西邮-华为NGN实验室(127)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p127);
                    String string = getResources().getString(R.string.location_127);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    textView.setText(string);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.noclass);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;
            case 123:
                if(reloadLayout) {
                    setContentView(R.layout.text1);
                    inittextlayout();
                    TextView textView1 = (TextView) findViewById(R.id.textView1);
                    textView1.setText("西邮-中兴IP网络技术实验室(123)");
                    ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                    imageView.setImageResource(R.drawable.p123);
                    String string = getResources().getString(R.string.location_123);
                    TextView textView = (TextView) pageview.get(0).findViewById(R.id.introduction);
                    ImageView imageView1 = (ImageView) pageview.get(1).findViewById(R.id.kebiao_image);
                    imageView1.setImageResource(R.drawable.class123);
                    textView.setText(string);
                    textView.setMovementMethod(ScrollingMovementMethod.getInstance());
                    reloadLayout = false;
                }
                break;

            // TODO: 2017/10/18 需要给对应的界面加载对应的信息（课表、介绍）   2017-10-23 完成

		default:
		}
    }


    @Override
    protected void onStop() {
        super.onStop();
        if(player != null){
            player.stop();
            player.reset();
        }
        if(noVoiceplayer != null){
            noVoiceplayer.stop();
            noVoiceplayer.reset();
        }

    }

    @Override
	protected void onDestroy(){
		super.onDestroy();
        if(player != null){
            player.stop();
            player.release();
        }
        if(noVoiceplayer != null){
            noVoiceplayer.stop();
            noVoiceplayer.release();
        }

	}

	private void addElement(nowIbeaconDisatance beaconDistance){
        for(int i = 0;i<distanceList.size();i++){
            ibeaconDisatance temp = distanceList.get(i);
            if(temp.minor == beaconDistance.minor){//if element is in the table,update the distance buffer
                double error = Math.abs(beaconDistance.distance - temp.meanDistance);//每次数据与前次均值相比较，差距太大视为无效数据。
                if(error <= 3) {
                    temp.distance[temp.index] = beaconDistance.distance;
                    temp.meanDistance = temp.caculateMean(temp.distance);
                    temp.index += 1;
                    temp.index = temp.index % 10;
                    temp.times = 0;
                    distanceList.set(i, temp);
                    return;
                }else return;
            }
        }
        //don't have this element,add it in the end
        ibeaconDisatance temp = new ibeaconDisatance();
        temp.index = 0;
        temp.distance[temp.index] = beaconDistance.distance;
        temp.minor = beaconDistance.minor;
        temp.meanDistance = beaconDistance.distance;
        temp.index = 1;
        temp.times = 0;
        distanceList.add(temp);
    }

    //update the ibeacon time,when no data about this ibeacon more than 40 times,delete it.
    private void updateiBeaconTimes(List list){
        for(int i=0;i<list.size();i++){
            ibeaconDisatance temp = (ibeaconDisatance) list.get(i);
            temp.times++;
            if(temp.times>20){
                list.remove(i);
            }else list.set(i, temp);

        }
    }

    //由距离集合中的距离均值来计算当前的位置
    private int caculateLocation(){
        int location = 0;
        Collections.sort(distanceList, new Comparator<ibeaconDisatance>() {//按照平均距离升序排列
            @Override
            public int compare(ibeaconDisatance distanceList1, ibeaconDisatance distanceList2) {
                if((distanceList1.getMeanDistance() - distanceList2.getMeanDistance())>0) return 1;
                else if((distanceList1.getMeanDistance() - distanceList2.getMeanDistance())<0) return -1;
                else return 0;
            }
        });
        ibeaconDisatance temp = distanceList.get(0);
        if(temp.meanDistance <= AREA_SPAN){
            location = temp.minor;
            Log.e("caculatelocation",""+location);
        }else location = 0;

        return location;
    }

    public void inittextlayout(){
        viewPager = (myViewPager) findViewById(R.id.viewPager);
        LayoutInflater inflater = getLayoutInflater();//find the layout file
        View view_introduction = inflater.inflate(R.layout.introduction_layout,null);
        View view_curriculum = inflater.inflate(R.layout.curriculum_layout,null);
        introductionLayout = (TextView) findViewById(R.id.introduction_title);
        curriculumLayout = (TextView) findViewById(R.id.curriculum_title);
        introductionLayout.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.introduction_select),
               null, null, null);
        introductionLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(0);
                introductionLayout.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.introduction_select),
                        null,null, null);
                introductionLayout.setTextColor(Color.parseColor("#28b6a5"));
                curriculumLayout.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.kebiao_noselect),
                        null,null, null);
                curriculumLayout.setTextColor(Color.parseColor("#cccccc"));
            }
        });
        curriculumLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(1);
                introductionLayout.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.introduction_noselect),
                        null,null, null);
                introductionLayout.setTextColor(Color.parseColor("#cccccc"));
                curriculumLayout.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.kebiao_select),
                        null,null, null);
                curriculumLayout.setTextColor(Color.parseColor("#28b6a5"));
            }
        });
        pageview.add(view_introduction);
        pageview.add(view_curriculum);
        PagerAdapter mPageAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return pageview.size();
            }

            @Override
            public boolean isViewFromObject(View view, Object o) {
                return view==o;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                container.addView(pageview.get(position));
                return pageview.get(position);
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView(pageview.get(position));
            }
        };
        viewPager.setAdapter(mPageAdapter);
        viewPager.setCurrentItem(0);
    }




    //播放音乐子线程
    public class musicThread extends Thread {
        private Handler musicHandler;

        public Handler getHandler() {//在run执行之前，返回的是null
            return musicHandler;
        }
        @Override
        public void run() {
            Looper.prepare();
            musicHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                            if (msg.arg1 != templocation[1]) {
                                if(msg.arg1 != templocation[0]){
                                    if(msg.arg1 != 0) {
                                        changeLocationFlag = 0;
                                        templocation[0] = msg.arg1;
                                    }
                                }else
                                    changeLocationFlag++;
                                if (changeLocationFlag >= FILTER_TIMES) {
                                    templocation[1] = msg.arg1;
                                    reloadLayout = true;
                                }
                            } else {
                                initMediaPlayer(templocation[1]);
                                runOnUiThread(new Runnable() {//在主线程上更新界面
                                    @Override
                                    public void run() {
                                        changeImage(templocation[1]);
                                    }
                                });
                                changeLocationFlag = 0;
                            }
                    Log.e("changeLoctionFlag",""+changeLocationFlag);
                }
            };
            Looper.loop();
        }
    }

    private void initblankMusic(){
        noVoiceplayer = MediaPlayer.create(getApplicationContext(),R.raw.white);
        noVoiceplayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                noVoiceplayer.stop();
                noVoiceplayer.prepareAsync();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            changeImage(0);
                            reloadLayout = true;
                            templocation[0] = 0;
                            templocation[1] = 0;
                        }
                    });
                }

        });
    }


     class ibeaconDisatance{//distancelist element have a 10length distance buffer
         int minor;
         double[] distance= new double[10];
         double meanDistance;
         int index;
         int times;

         public double caculateMean(double[] distance){
             double[] temp;
             double sum = 0;
             temp = ArrayUtils.removeElement(distance, 0);
             while (ArrayUtils.contains(temp, 0))
                 temp = ArrayUtils.removeElement(temp, 0);//先判断缓存中是否有0，有则去掉
             for(int i = 0;i<temp.length;i++) sum = sum + temp[i];
             double output = (Math.round(sum / temp.length * 100) / 100.0);
             return output;
         }

         public double getMeanDistance(){
             return meanDistance;
         }

     }

     class nowIbeaconDisatance{
         int minor;
         double distance;
         public nowIbeaconDisatance(int minor,double distance){
             this.minor = minor;
             this.distance = distance;

         }
     }

     class myButtonOnclicklistener implements OnClickListener{
         @Override
         public void onClick(View view) {
             Intent intent=new Intent(Intent.ACTION_VIEW);
             intent.setData(Uri.parse("http://xuputcommunication.duapp.com/MusicZ/"));
             if(player!=null)
                 player.reset();
             else
                 player=null;

             startActivity(intent);
         }
     }

}



