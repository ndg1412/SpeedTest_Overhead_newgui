package com.work.speedtest_overhead.util;

/**
 * Created by ngodi on 3/2/2016.
 */
public class RuntimeCmd {
    static Process process = null;

    public static void Download_Start() {
        Runtime runtime = Runtime.getRuntime();
        try {

            String command = String.format("sh %s", Config.DOWNLOAD_PATH_SCRIPT_START);
            process = runtime.exec(command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Download_Stop() {
        try {
            if(process != null) {
                String command = String.format("sh %s", Config.DOWNLOAD_PATH_SCRIPT_STOP);
                Runtime runtime = Runtime.getRuntime();
                runtime.exec(command);
                process.destroy();
                process = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
