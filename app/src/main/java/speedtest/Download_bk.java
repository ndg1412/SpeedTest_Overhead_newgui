package speedtest;

import android.util.Log;

import com.work.speedtest_overhead.Interface.IDownloadListener;
import com.work.speedtest_overhead.object.SpeedUpdateObj;
import com.work.speedtest_overhead.util.Config;
import com.work.speedtest_overhead.util.Network;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import fr.bmartel.protocol.http.HttpFrame;
import fr.bmartel.protocol.http.states.HttpStates;

/**
 * Created by ngodi on 2/22/2016.
 */
public class Download_bk {
    private static final String TAG = "Download";
    String host;
    int port;
    String uri;
    long total_size = 0;
    long speed_size = 0;
    long finish_size = 0;
    int total_download = 0;
    Timer tiDownload = null;
    String sLteName = null;
    List<Float> lMax_wifi = new ArrayList<Float>();
    List<Float> lMax_lte = new ArrayList<Float>();
    List<String> lFile = new ArrayList<String>();
    long wlan_rx = 0, lte_rx;
    long wlan_rx_first = 0, lte_rx_first;
    long timeStart = 0;
    int count_wlan = 0, count_lte;

    private IDownloadListener downloadTestListenerList;
    public void addDownloadTestListener(IDownloadListener listener) {
        downloadTestListenerList = listener;
    }

    public Download_bk(String host, int port, String uri, String[] files) {
        this.host = host;
        this.port = port;
        this.uri = uri;
        sLteName = Network.getLTEIfName();
        for(String file : files) {
            String url = String.format("http://%s:%d%s%s", host, port, uri, file);
            int size = getUrlSize(url);
            if(size > 0) {
                lFile.add(file);
                total_size += getUrlSize(url);
                Log.d(TAG, "download total_size: " + total_size);
            }
        }
    }

    public String Create_Head(String file) {
        String url = this.uri + file;
        String downloadRequest = "GET " + url + " HTTP/1.1\r\n" + "Host: " + this.host + "\r\n\r\n";

        return downloadRequest;
    }

    public void Download_Run() {
        downloadTestListenerList.onDownloadProgress(0);

        BlockingQueue queue = new LinkedBlockingQueue(Config.NUMBER_QUEUE_THREAD_DOWNLOAD);
        Producer procedure = new Producer(queue, lFile);
        Consumer consumer = new Consumer(queue, total_size);
        Thread thPro = new Thread(procedure);
        Thread thCon = new Thread(consumer);
        wlan_rx = wlan_rx_first = Network.getRxByte(Config.WLAN_IF);
        lte_rx = lte_rx_first = Network.getRxByte(sLteName);
        timeStart = System.currentTimeMillis();

        thPro.start();
        thCon.start();

        tiDownload = new Timer();
        tiDownload.schedule(new TimerTask() {
            @Override
            public void run() {
                long tmp_wlan = Network.getRxByte(Config.WLAN_IF);
                long tmp_lte = Network.getRxByte(sLteName);
                long wlan = tmp_wlan;
                long lte = tmp_lte;
                if(wlan < wlan_rx) {
                    wlan += Config.ULONG_MAX;
                    count_wlan++;
                }
                if(lte < lte_rx) {
                    lte += Config.ULONG_MAX;
                    count_lte++;
                }
                if((tmp_wlan != 0) || (tmp_lte != 0)) {
                    float speed_wlan = ((wlan - wlan_rx) * 8 / 1000000 * (1000f / (Config.TIMER_SLEEP)));
                    float speed_lte = ((lte - lte_rx) * 8 / 1000000 * (1000f / (Config.TIMER_SLEEP)));
                    long timeCur = System.currentTimeMillis();
                    if(wlan < wlan_rx_first)
                        wlan += Config.ULONG_MAX;
                    if(lte < lte_rx_first)
                        lte += Config.ULONG_MAX;
                    float speed_wlan_avg = (wlan - wlan_rx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;
                    float speed_lte_avg = (lte - lte_rx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;
                    lMax_wifi.add(speed_wlan);
                    lMax_lte.add(speed_lte);
                    float speed = speed_wlan + speed_lte;
                    SpeedUpdateObj data = new SpeedUpdateObj(speed, getBandwidthWifi(), speed_wlan_avg, getBandwidthLte(),
                            speed_lte_avg);
                    Log.d(TAG, "download speed crrrent: " + speed);
                    downloadTestListenerList.onDownloadUpdate(data);
                    wlan_rx = tmp_wlan;
                    lte_rx = tmp_lte;
                }
            }
        }, 0, Config.TIMER_SLEEP);
        try {
            while(thPro.isAlive())
                thPro.join(100);
            while(thCon.isAlive())
                thCon.join(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long timeEnd = System.currentTimeMillis();
        long wlan_rx_end = Network.getRxByte(Config.WLAN_IF) + count_wlan * Config.ULONG_MAX;
        long lte_rx_end = Network.getRxByte(sLteName) + count_lte * Config.ULONG_MAX;
//        float transfer = (finish_size * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        float max_wifi = getBandwidthWifi();
        float max_lte = getBandwidthLte();
        float avg_wifi = ((wlan_rx_end - wlan_rx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        float avg_lte = ((lte_rx_end - lte_rx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        SpeedUpdateObj data = new SpeedUpdateObj(avg_wifi+avg_lte, max_wifi, avg_wifi, max_lte, avg_lte);
        downloadTestListenerList.onDownloadPacketsReceived(data);
        tiDownload.cancel();
        tiDownload = null;
    }

    public class Do_Download extends Thread {
        String file;
        int download_size = 0;

        public Do_Download(String file) {
            this.file = file;

        }
        @Override
        public void run() {
            String request = Create_Head(file);

            Socket socket = null;
            int frameLength = 0;
            try {
                socket = new Socket();
                socket.setTcpNoDelay(false);
                socket.setSoTimeout(Config.TIME_OUT);
                socket.setReuseAddress(true);
                socket.setKeepAlive(true);

                socket.connect(new InetSocketAddress(host, port));
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(request.getBytes());
//                total_download += request.length();
                outputStream.flush();
                HttpFrame httpFrame = new HttpFrame();

                int read = 0;
                byte[] buffer = new byte[10240];
                long totalPackets = 0;
                HttpStates errorCode = httpFrame.decodeFrame(socket.getInputStream());
                HttpStates headerError = httpFrame.parseHeader(socket.getInputStream());
                frameLength = httpFrame.getContentLength();
                while ((read = socket.getInputStream().read(buffer)) != -1) {
                    Log.d(TAG, "read byte in socket: " + read);
                    totalPackets += read;

                    speed_size += read;

                    downloadTestListenerList.onDownloadProgress((int) (100 * speed_size / total_size));
                    if (totalPackets == frameLength) {
                        break;
                    }
                }

                if(errorCode == HttpStates.HTTP_FRAME_OK) {
                    download_size = frameLength;
                }

            } catch (Exception e) {
                e.printStackTrace();
                download_size = frameLength;
                finish_size +=frameLength;
            }
        }

        public int getDownloadSize() {
            return download_size;
        }
    }

    public class Producer implements Runnable {
        BlockingQueue queue;
        List<String> files;

        public Producer(BlockingQueue queue, List<String> files) {
            this.queue = queue;
            this.files = files;
        }

        @Override
        public void run() {
            for(String file : files) {
                Do_Download down = new Do_Download(file);
                down.start();
                try {
                    queue.put(down);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public class Consumer implements Runnable {
        BlockingQueue queue;
        long total_size;

        public Consumer(BlockingQueue queue, long total_size) {
            this.queue = queue;
            this.total_size = total_size;
        }
        @Override
        public void run() {

            while(finish_size < total_size) {
                try {
                    Do_Download down = (Do_Download)queue.take();
                    while (down.isAlive()) {
                        down.join(100);
                    }
                    finish_size += down.getDownloadSize();
                    Log.d(TAG, "giang dbg down.getDownloadSize(): " + down.getDownloadSize());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public float getBandwidthWifi() {
        float max = lMax_wifi.get(0);
        for(int i = 1; i < lMax_wifi.size(); i++) {
            if(max < lMax_wifi.get(i))
                max = lMax_wifi.get(i);
        }
        return max;
    }

    public float getBandwidthLte() {
        float max = lMax_lte.get(0);
        for(int i = 1; i < lMax_lte.size(); i++) {
            if(max < lMax_lte.get(i))
                max = lMax_lte.get(i);
        }
        return max;
    }

    public int getUrlSize(String url) {
        int size = 0;
        try
        {
            URL uri = new URL(url);
            URLConnection ucon;
            ucon = uri.openConnection();
            ucon.connect();
            String contentLengthStr = ucon.getHeaderField("content-length");
            Log.d(TAG, "getUrlSize: " + contentLengthStr);
            if(contentLengthStr != null)
                size = Integer.valueOf(contentLengthStr);
        } catch(IOException e) {
            e.printStackTrace();
        }
        return size;
    }
}
