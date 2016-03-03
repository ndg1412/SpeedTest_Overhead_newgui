package speedtest;

import android.util.Log;

import com.work.speedtest_overhead.Interface.IUploadListener;
import com.work.speedtest_overhead.object.SpeedUpdateObj;
import com.work.speedtest_overhead.util.Config;
import com.work.speedtest_overhead.util.Network;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import fr.bmartel.protocol.http.HttpFrame;
import fr.bmartel.protocol.http.states.HttpStates;


/**
 * Created by ngodi on 2/20/2016.
 */
public class Upload {
    private static final String TAG = "Upload";
    String host;
    int port;
    String uri;
    int[] sizes;
    long total_size = 0;
    long finish_size = 0;
    long total_upload = 0;
    long wlan_tx = 0, lte_tx;
    long wlan_tx_first = 0, lte_tx_first;
    String sLteName = null;
    Timer tiUpload = null;
    List<Float> lMax = new ArrayList<Float>();
    List<Float> lMax_wifi = new ArrayList<Float>();
    List<Float> lMax_lte = new ArrayList<Float>();
    long timeStart = 0;
    int count_wlan = 0, count_lte = 0;

    private IUploadListener uploadTestListenerList;
    public void addUploadTestListener(IUploadListener listener) {
        uploadTestListenerList = listener;
    }

    public Upload(String host, int port, String uri, int[] sizes) {
        this.host = host;
        this.port = port;
        this.uri = uri;
        this.sizes = sizes;
        sLteName = Network.getLTEIfName();
        for(int i : sizes)
            total_size += i;
        Log.d(TAG, "total_size: " + total_size);
        if(tiUpload != null) {
            tiUpload.cancel();
        }
    }

    public String Create_Head(int size) {
//        String uploadRequest = "POST " + uri + " HTTP/1.1\r\n" + "Host: " + host + "\r\nAccept: */*\r\nContent-Length: " + size + "\r\n\r\n";

        StringBuilder sb = new StringBuilder();
        sb.append("POST %s HTTP/1.1 \r\n");
        sb.append("Host :%s \r\n");
        sb.append("Accept: text/*\r\n");
        sb.append("Content-Length: %s \r\n");
        sb.append("Content-Type: application/x-www-form-urlencoded\r\n");
//        sb.append("Expect: 100-continue\r\n");
        sb.append("\r\n");
//
        String uploadRequest = String.format(sb.toString(), uri, host, size);

        return uploadRequest;
    }

    public void Upload_Run() {
        uploadTestListenerList.onUploadProgress(0);

        BlockingQueue queue = new LinkedBlockingQueue(Config.NUMBER_QUEUE_THREAD_UPLOAD);
        Producer procedure = new Producer(queue, sizes);
        Consumer consumer = new Consumer(queue, total_size);
        Thread thPro = new Thread(procedure);
        Thread thCon = new Thread(consumer);
        wlan_tx = wlan_tx_first = Network.getTxByte(Config.WLAN_IF);
        lte_tx = lte_tx_first = Network.getTxByte(sLteName);
        timeStart = System.currentTimeMillis();
        thPro.start();
        thCon.start();

        tiUpload = new Timer();
        tiUpload.schedule(new TimerTask() {
            @Override
            public void run() {
                long tmp_wlan = Network.getTxByte(Config.WLAN_IF);
                long tmp_lte = Network.getTxByte(sLteName);
                long wlan = tmp_wlan;
                long lte = tmp_lte;
                if(wlan < wlan_tx) {
                    wlan += Config.ULONG_MAX;
                    count_wlan++;
                }
                if(lte < lte_tx) {
                    lte += Config.ULONG_MAX;
                    count_lte++;
                }
                if((tmp_wlan != 0) || (tmp_lte != 0)) {
                    float speed_wlan = ((wlan - wlan_tx) * 8 / 1000000 * (1000f / Config.TIMER_SLEEP));
                    float speed_lte = ((lte - lte_tx) * 8 / 1000000 * (1000f / Config.TIMER_SLEEP));
                    long timeCur = System.currentTimeMillis();
                    if(wlan < wlan_tx_first)
                        wlan += Config.ULONG_MAX;
                    if(lte < lte_tx_first)
                        lte += Config.ULONG_MAX;
                    /*loat speed_wlan_avg = (tmp_wlan+count_wlan*Config.ULONG_MAX - wlan_tx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;
                    float speed_lte_avg = (tmp_lte+count_lte*Config.ULONG_MAX - lte_tx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;*/
                    float speed_wlan_avg = (wlan - wlan_tx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;
                    float speed_lte_avg = (lte - lte_tx_first) * 8 / ((timeCur - timeStart) / 1000f)/1000000;
                    lMax_wifi.add(speed_wlan);
                    lMax_lte.add(speed_lte);
                    float speed = speed_wlan + speed_lte;
                    lMax.add(speed);
                    float max_speed_wifi = getBandwidthWifi();
                    float max_speed_lte = getBandwidthLte();

                    if(max_speed_wifi < speed_wlan_avg) {
                        max_speed_wifi = speed_wlan_avg;
                        lMax_wifi.add(max_speed_wifi);
                    }
                    if(max_speed_lte < speed_lte_avg) {
                        max_speed_lte = speed_lte_avg;
                        lMax_lte.add(max_speed_lte);
                    }
                    SpeedUpdateObj data = new SpeedUpdateObj(speed, max_speed_wifi, speed_wlan_avg, max_speed_lte,
                            speed_lte_avg);
                    uploadTestListenerList.onUploadUpdate(data);
                    wlan_tx = tmp_wlan;
                    lte_tx = tmp_lte;
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
       /* long wlan_tx_end = Network.getTxByte(Config.WLAN_IF) + count_wlan * Config.ULONG_MAX;
        long lte_tx_end = Network.getTxByte(sLteName) + count_lte * Config.ULONG_MAX;*/
        long wlan_tx_end = Network.getTxByte(Config.WLAN_IF);
        long lte_tx_end = Network.getTxByte(sLteName);
        if(wlan_tx_end < wlan_tx_first)
            wlan_tx_end += Config.ULONG_MAX;
        if(lte_tx_end < lte_tx_first)
            lte_tx_end += Config.ULONG_MAX;
//        float transfer = (finish_size * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        float max_wifi = getBandwidthWifi();
        float max_lte = getBandwidthLte();
        float max_speed = max_wifi + max_lte;
        float avg_wifi = ((wlan_tx_end - wlan_tx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        float avg_lte = ((lte_tx_end - lte_tx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;

        float avg_speed = avg_wifi + avg_lte;
        SpeedUpdateObj data;
        if(max_speed < avg_speed)
            data = new SpeedUpdateObj(avg_speed, avg_speed, max_wifi, avg_wifi, max_lte, avg_lte);
        else
            data = new SpeedUpdateObj(avg_speed, max_speed, max_wifi, avg_wifi, max_lte, avg_lte);
        uploadTestListenerList.onUploadPacketsReceived(data);
        tiUpload.cancel();
        tiUpload = null;
    }

    public class Do_Upload extends Thread {
        int size;
        int upload_size = 0;
        long lTime_start;
        Socket socket = null;
        OutputStream outputStream = null;

        public Do_Upload(int size) {
            this.size = size;

        }
        @Override
        public void run() {
            String request = Create_Head(size);
            /*RandomGen random = new RandomGen(size);
            byte[] buf = random.getBuf();*/
            try {
                socket = new Socket();
                socket.setTcpNoDelay(false);
                socket.setSoTimeout(Config.TIME_OUT);
                socket.setReuseAddress(true);
                socket.setKeepAlive(true);
                socket.connect(new InetSocketAddress(host, port));
                outputStream = socket.getOutputStream();
                outputStream.write(request.getBytes());
                outputStream.flush();
                /*outputStream.write(buf);
                outputStream.flush();*/

                lTime_start = System.currentTimeMillis();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while(true) {
                            try {
                                Thread.sleep(100);
                                long time_current = System.currentTimeMillis();
                                if((time_current - lTime_start) > Config.TIME_OUT) {
                                    outputStream.close();
                                    outputStream = null;
                                    interrupt();
                                    break;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }

                    }
                }).start();
                for(int i = 0; i < size; i = i + 50000) {
                    lTime_start = System.currentTimeMillis();
                    byte[] buf = new byte[50000];
                    outputStream.write(buf);
                    outputStream.flush();
                    total_upload += 50000;
                    uploadTestListenerList.onUploadProgress((int) (total_upload * 100 / total_size));
                }
                HttpFrame frame = new HttpFrame();

                HttpStates httpStates = frame.parseHttp(socket.getInputStream());
                if (httpStates == HttpStates.HTTP_FRAME_OK) {
                    if (frame.getStatusCode() == 200 && frame.getReasonPhrase().toLowerCase().equals("ok")) {
                        //upload_size = size;

//                        Log.d(TAG, "upload complete==============================>");

                    }

                } else if (httpStates == HttpStates.HTTP_READING_ERROR) {
                    //upload_size = size;
                }

            } catch (Exception e) {
                e.printStackTrace();
                //upload_size = size;
            }
            upload_size = size;
            try {
                if(outputStream != null)
                    outputStream.close();
                if(socket != null)
                    socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public int getUploadSize() {
            return upload_size;
        }
    }

    public class Producer implements Runnable {
        BlockingQueue queue;
        int[] sizes;

        public Producer(BlockingQueue queue, int[] sizes) {
            this.queue = queue;
            this.sizes = sizes;
        }

        @Override
        public void run() {
            for(int i : sizes) {
                Do_Upload up = new Do_Upload(i);
                up.start();
                try {
                    queue.put(up);
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
                    Do_Upload up = (Do_Upload)queue.take();
                    while (up.isAlive()) {
//                        up.join(100);
                        up.join(100);
                    }
                    Log.d(TAG, "giang dbg: getUploadSize: " + up.getUploadSize());
                    finish_size += up.getUploadSize();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public float getBandwidth() {
        float max = lMax.get(0);
        for(int i = 1; i < lMax.size(); i++) {
            if(max < lMax.get(i))
                max = lMax.get(i);
        }
        return max;
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
}
