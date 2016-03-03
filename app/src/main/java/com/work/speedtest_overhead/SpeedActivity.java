package com.work.speedtest_overhead;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.work.speedtest_overhead.object.SpeedData;
import com.work.speedtest_overhead.object.SpeedUpdateObj;
import com.work.speedtest_overhead.util.Config;
import com.work.speedtest_overhead.util.Network;
import com.work.speedtest_overhead.util.RuntimeCmd;
import com.work.speedtest_overhead.wiget.SpeedView;

import speedtest.Download;
import com.work.speedtest_overhead.Interface.IDownloadListener;
import com.work.speedtest_overhead.Interface.IUploadListener;
import speedtest.Upload;

/**
 * Created by ngodi on 2/24/2016.
 */
public class SpeedActivity extends Activity {
    private static final String TAG = "SpeedActivity";
    Context context;
    ImageButton ibSetting;
    RelativeLayout rlSpeed, rlProgress, rlInterface;
    TextView tvMaxResult, tvAvgResult;
    TextView tvWifiMax, tvWifiAvg, tvLteMax, tvLteAvg;
    TextView tvProgressText;
    ProgressBar pbStatus;
    ImageButton ibStartSpeed;
    AsyncTask<Void, Void, String> atSpeedTest;
    SpeedView svSpeedDisplay;
    SharedPreferences prefs;
    public boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speedtest);
        context = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        ibSetting = (ImageButton) findViewById(R.id.ibSetting);
        rlSpeed = (RelativeLayout) findViewById(R.id.rlSpeed);
        tvMaxResult = (TextView) findViewById(R.id.tvMaxResult);
        tvAvgResult = (TextView) findViewById(R.id.tvAvgResult);
        tvWifiMax = (TextView) findViewById(R.id.tvWifiMax);
        tvWifiAvg = (TextView) findViewById(R.id.tvWifiAvg);
        tvLteMax = (TextView) findViewById(R.id.tvLteMax);
        tvLteAvg = (TextView) findViewById(R.id.tvLteAvg);

        rlProgress = (RelativeLayout) findViewById(R.id.rlProgress);
        tvProgressText = (TextView) findViewById(R.id.tvProgressText);
        rlInterface = (RelativeLayout) findViewById(R.id.rlInterface);

        svSpeedDisplay = (SpeedView) findViewById(R.id.svSpeedDisplay);
        pbStatus = (ProgressBar) findViewById(R.id.pbStatus);
        ibStartSpeed = (ImageButton) findViewById(R.id.ibStartSpeed);
        ibSetting.setOnClickListener(settingListener);
        ibStartSpeed.setOnClickListener(startListener);
        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
        if((ip == null) || (port == 0))
            SettingServer(context);
        else {
            strIp = ip;
            if(!Network.CheckHost(ip))
                Toast.makeText(SpeedActivity.this, "Can not ping to server ip", Toast.LENGTH_LONG).show();
        }
        Log.d(TAG, "app path: " + context.getApplicationInfo().dataDir);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        loManager.removeUpdates(gpslistener);

    }



    final Handler hSpeedCircle = new Handler() {
        public void handleMessage(Message msg) {
            SpeedUpdateObj object = (SpeedUpdateObj) msg.obj;
            svSpeedDisplay.setValue(object.getCurrent());
            tvLteMax.setText(String.format("%.1f", object.getMax_lte()));
            tvLteAvg.setText(String.format("%.1f", object.getAvg_lte()));
            tvWifiMax.setText(String.format("%.1f", object.getMax_wifi()));
            tvWifiAvg.setText(String.format("%.1f", object.getAvg_wifi()));

        }
    };

    final Handler hConnection = new Handler() {
        public void handleMessage(Message msg) {
            svSpeedDisplay.setValue(0);
            tvLteMax.setText("");
            tvLteAvg.setText("");
            tvWifiMax.setText("");
            tvWifiAvg.setText("");

        }
    };

    final Handler hDownload = new Handler() {
        public void handleMessage(Message msg) {
            rlProgress.setVisibility(View.GONE);
            tvProgressText.setText("Download");
            rlSpeed.setBackgroundResource(R.drawable.bg_result_download);
            rlSpeed.setVisibility(View.VISIBLE);
            SpeedUpdateObj object = (SpeedUpdateObj) msg.obj;
            tvMaxResult.setText(String.format("%.1f", object.getMax()));
            tvAvgResult.setText(String.format("%.1f", object.getAvg()));

            tvWifiMax.setText(String.format("%.1f", object.getMax_wifi()));
            tvWifiAvg.setText(String.format("%.1f", object.getAvg_wifi()));

            tvLteMax.setText(String.format("%.1f", object.getMax_lte()));
            tvLteAvg.setText(String.format("%.1f", object.getAvg_lte()));
            svSpeedDisplay.setValue(object.getMax());
        }
    };

    final Handler hUpload = new Handler() {
        public void handleMessage(Message msg) {
            rlProgress.setVisibility(View.GONE);
            rlSpeed.setBackgroundResource(R.drawable.bg_result_upload);
            rlSpeed.setVisibility(View.VISIBLE);
            SpeedUpdateObj object = (SpeedUpdateObj) msg.obj;
            tvMaxResult.setText(String.format("%.1f", object.getMax()));
            tvAvgResult.setText(String.format("%.1f", object.getAvg()));
            tvWifiMax.setText(String.format("%.1f", object.getMax_wifi()));
            tvWifiAvg.setText(String.format("%.1f", object.getAvg_wifi()));

            tvLteMax.setText(String.format("%.1f", object.getMax_lte()));
            tvLteAvg.setText(String.format("%.1f", object.getAvg_lte()));
            svSpeedDisplay.setValue(object.getMax());
        }
    };

    final Handler hStatus = new Handler() {
        public void handleMessage(Message msg) {
            SpeedData data = (SpeedData)msg.obj;
            rlProgress.setVisibility(View.VISIBLE);
        }
    };

    final Handler hButton = new Handler() {
        public void handleMessage(Message msg) {
            SpeedData data = (SpeedData)msg.obj;
            rlProgress.setVisibility(View.VISIBLE);
            ibStartSpeed.setVisibility(View.VISIBLE);
        }
    };

    public OnClickListener settingListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if(!isRunning) {
                SettingServer(context);
            } else
                Toast.makeText(context, "upload or download progress is running", Toast.LENGTH_LONG).show();
        }
    };

    public OnClickListener startListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            rlSpeed.setVisibility(View.GONE);
            svSpeedDisplay.setValue(0);
            tvWifiMax.setText("");
            tvWifiAvg.setText("");
            tvLteMax.setText("");
            tvLteAvg.setText("");
            if(!isRunning) {
                /*atSpeedTest = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        isRunning = true;
                        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
                        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
                        Log.d(TAG, "ip: " + ip + ", port: " + port);
                        Download down = new Download(ip, port, "/speedtest/", Config.DOWNLOAD_FILE);
                        down.addDownloadTestListener(new IDownloadListener() {
                            @Override
                            public void onDownloadPacketsReceived(SpeedUpdateObj data) {
                                Log.d(TAG, "Download [ OK ]");
                                Log.d(TAG, "download transfer rate  : " + transferRateBitPerSeconds + " Mbit/second");
                                Log.d(TAG, "##################################################################");

                                UpDownObject data = new UpDownObject(transferRateBitPerSeconds, wifi_avg, lte_avg);
                                Message msg = new Message();
                                msg.obj = data;
                                hDownload.sendMessage(msg);
                            }

                            @Override
                            public void onDownloadError(int errorCode, String message) {

                            }

                            @Override
                            public void onDownloadProgress(int percent) {
//                                  Log.d(TAG, "Download  percent: " + percent);
                                if (percent == 0) {
                                    Message msg = new Message();
                                    hStatus.sendMessage(msg);
                                }
                                pbStatus.setProgress(percent);
                            }

                            @Override
                            public void onDownloadUpdate(float wifi, float lte) {
                                Log.d(TAG, "onDownloadUpdate speed: " + wifi + lte);
                                Message msg = new Message();
                                UpDownObject data = new UpDownObject(wifi, lte);
                                msg.obj = data;
                                hSpeedCircle.sendMessage(msg);
                            }
                        });
                        down.Download_Run();

                        isRunning = false;
                        return "";
                    }

                    @Override
                    protected void onPostExecute(String result) {

                    }

                };
                atSpeedTest.execute(null, null, null);*/
                SelectTest(context);
            } else
                Toast.makeText(context, "upload or download progress is running", Toast.LENGTH_LONG).show();
        }
    };

    public void SettingServer(Context context) {
        final Dialog dialog = new Dialog(context, R.style.select_test_dialog);
        dialog.setContentView(R.layout.settingserver);
        dialog.setTitle("Setting Server");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        final EditText etServerIp = (EditText)dialog.findViewById(R.id.etServerIp);
        final EditText etServerPort = (EditText)dialog.findViewById(R.id.etServerPort);
        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
        if(ip != null)
            etServerIp.setText(ip);
        if(port > 0)
            etServerPort.setText(String.valueOf(port));
        ImageButton ibSettingServerOk = (ImageButton)dialog.findViewById(R.id.ibSettingServerOk);
        ibSettingServerOk.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                try {
                    SharedPreferences.Editor editor = prefs.edit();
                    String ip = etServerIp.getText().toString().trim();
                    if (!Network.isIP(ip))
                        Toast.makeText(SpeedActivity.this, "Server host is not ip address", Toast.LENGTH_LONG).show();
                    int port = Integer.valueOf(etServerPort.getText().toString().trim());
                    if (Network.CheckHost(ip)) {
                        editor.putString(Config.PREF_KEY_SERVER_HOST, ip);
                        editor.putInt(Config.PREF_KEY_SERVER_PORT, port);
                        Config.strServer_Ip = ip;
                        Config.iServer_Port = port;
                        //Log.d(TAG, "ip: " + Config.strServer_Ip + ", port: " + Config.iServer_Port);
                        editor.commit();
                        dialog.dismiss();
                    } else
                        Toast.makeText(SpeedActivity.this, "Can not ping to server ip", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(SpeedActivity.this, "port is number", Toast.LENGTH_LONG).show();
                }
            }
        });
        dialog.show();
    }
    String strIp;
    public void SelectTest(final Context context) {
        final Dialog dialog = new Dialog(context, R.style.select_test_dialog);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(R.layout.select_test);
        ImageButton ibSelectTest_Download = (ImageButton)dialog.findViewById(R.id.ibSelectTest_Download);
        ImageButton ibSelectTest_Upload = (ImageButton)dialog.findViewById(R.id.ibSelectTest_Upload);
        ibSelectTest_Download.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                RuntimeCmd.Download_Start();
                tvProgressText.setText("Download");
                atSpeedTest = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        isRunning = true;
                        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
                        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
                        //Log.d(TAG, "ip: " + ip + ", port: " + port);
                        Download.setInterrupt(false);
                        Download down = new Download(ip, port, "/speedtest/", Config.DOWNLOAD_FILE);
                        down.addDownloadTestListener(new IDownloadListener() {
                            @Override
                            public void onDownloadPacketsReceived(SpeedUpdateObj data) {
                                /*Log.d(TAG, "Download [ OK ]");
                                Log.d(TAG, "download transfer rate  : " + data.getMax() + " Mbit/second");
                                Log.d(TAG, "##################################################################");*/

                                Message msg = new Message();
                                msg.obj = data;
                                hDownload.sendMessage(msg);
                            }

                            @Override
                            public void onDownloadError(int errorCode, String message) {

                            }

                            @Override
                            public void onDownloadProgress(int percent) {
//                                  Log.d(TAG, "Download  percent: " + percent);
                                if (percent == 0) {
                                    Message msg = new Message();
                                    hStatus.sendMessage(msg);
                                }
                                pbStatus.setProgress(percent);
                            }

                            @Override
                            public void onDownloadUpdate(SpeedUpdateObj data) {
                                //Log.d(TAG, "onDownloadUpdate speed: " + data.getCurrent());
                                Message msg = new Message();
                                msg.obj = data;
                                hSpeedCircle.sendMessage(msg);
                            }
                        });
                        down.Download_Run();
                        RuntimeCmd.Download_Stop();
                        isRunning = false;
                        return "";
                    }

                    @Override
                    protected void onPostExecute(String result) {

                    }

                };
                atSpeedTest.execute(null, null, null);
                Log.d(TAG, "==============================complete download test=============================>");
            }
        });
        ibSelectTest_Upload.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                tvProgressText.setText("Upload");
                atSpeedTest = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        isRunning = true;
                        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
                        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
                        Log.d(TAG, "ip: " + ip + ", port: " + port);
                        Upload up = new Upload(ip, 80, "/", Config.UPLOAD_SIZE);
                        up.addUploadTestListener(new IUploadListener() {
                            @Override
                            public void onUploadPacketsReceived(SpeedUpdateObj data) {
                                Log.d(TAG, "========= Upload [ OK ]   =============");
                                Log.d(TAG, "upload transfer rate  : " + data.getMax() + " Mbit/second");
                                Log.d(TAG, "##################################################################");
                                Message msg = new Message();
                                msg.obj = data;
                                hUpload.sendMessage(msg);
                            }

                            @Override
                            public void onUploadError(int errorCode, String message) {

                            }

                            @Override
                            public void onUploadProgress(int percent) {
//                                  Log.d(TAG, "Upload  percent: " + percent);
                                if (percent == 0) {
                                    Message msg = new Message();
                                    hStatus.sendMessage(msg);
                                }
                                pbStatus.setProgress(percent);
                            }

                            @Override
                            public void onUploadUpdate(SpeedUpdateObj data) {
                                Log.d(TAG, "onUploadUpdate speed: " + data.getCurrent());
                                Message msg = new Message();
                                msg.obj = data;
                                hSpeedCircle.sendMessage(msg);
                            }
                        });
                        up.Upload_Run();
                        isRunning = false;
                        return "";
                    }

                    @Override
                    protected void onPostExecute(String result) {

                    }

                };
                atSpeedTest.execute(null, null, null);
            }
        });
        dialog.show();
    }
}

