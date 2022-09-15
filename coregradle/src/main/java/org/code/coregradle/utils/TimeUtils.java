package org.code.coregradle.utils;

public class TimeUtils {
    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {

        }
    }
}
