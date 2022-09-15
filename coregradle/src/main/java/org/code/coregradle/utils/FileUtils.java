package org.code.coregradle.utils;

import android.annotation.SuppressLint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileUtils {
    @SuppressLint("SimpleDateFormat")
    private final static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");

    public static String readString(File file) {
        StringBuilder stringBuffer = new StringBuilder();
        if (file.exists() && file.canRead()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuffer.append(line);
                }
                return stringBuffer.toString();
            } catch (IOException ignored) {
            }
        }
        return null;
    }


    public static void writeString(File file, String string) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            bufferedWriter.write(string);
        }
    }


    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @SuppressLint("SimpleDateFormat")
    public static String getTimeStampName() {
        Date date = new Date();
        return simpleDateFormat.format(date);
    }
}
