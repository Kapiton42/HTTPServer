package com.kapitonenko.httpserver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by kapiton on 23.02.16.
 */
public class FileReader {
    private static com.kapitonenko.httpserver.RecursiveWatcher watcher;

    private static Map<Path, byte[]> fileCash;
    static {
        fileCash = new HashMap<>();
    }

    private static Map<Path, Boolean> fileUpdateStatus;
    static {
        fileUpdateStatus = new HashMap<>();
    }

    private static String rootDirectory = "/home/kapiton/Http-root";

    private static Map<String, String> contentTypesMap;
    static {
        contentTypesMap = new HashMap<>();
        contentTypesMap.put("js", "application/javascript");
        contentTypesMap.put("html", "text/html");
        contentTypesMap.put("css", "text/css");
        contentTypesMap.put("jpg", "image/jpg");
        contentTypesMap.put("png", "image/png");
        contentTypesMap.put("gif", "image/gif");
    }

    public static void initFileReader(String rootDirectory) {
        FileReader.rootDirectory = rootDirectory;
    }

    public static void createWatcher() {
        try {
            watcher = new RecursiveWatcher(Paths.get(rootDirectory), fileCash, fileUpdateStatus);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getResource(String pathname, String contentType, String charset) throws IOException {
        Path filename = Paths.get(rootDirectory + pathname.substring(1));
        watcher.checkWatchService();
        if(fileUpdateStatus.get(filename) == null || fileUpdateStatus.get(filename)) {
            byte[] temp;
            if (contentType.equals("application/javascript") || contentType.equals("text/html")
                    || contentType.equals("text/css")) {
                temp = readTextFile(pathname, charset);
            } else {
                Pattern p = Pattern.compile("^image/.*");
                Matcher m = p.matcher(contentType);

                if (m.find()) {
                    p = Pattern.compile("\\w*$");
                    m = p.matcher(contentType);
                    if (m.find()) {
                        temp = readImage(pathname, m.group());
                    } else {
                        throw new IOException();
                    }
                } else {
                    throw new IOException();
                }
            }

            fileUpdateStatus.put(filename, false);
            fileCash.put(filename, temp);
            return temp;
        } else {
            System.out.println("Use cash");
            return fileCash.get(filename);
        }
    }

    public static byte[] getResource(String pathname, String contentType) throws IOException{
        return getResource(pathname, contentType, "UTF-8");
    }

    public static String getContentType(String pathname) {
        String contentType = null;

        Pattern p = Pattern.compile("\\.\\w*$");
        Matcher m = p.matcher(pathname);

        if(m.find())
            contentType = contentTypesMap.get(m.group().substring(1));

        return contentType;
    }

    private static byte[] readImage(String pathname, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        BufferedImage img = ImageIO.read(new File(rootDirectory + pathname.substring(1)));
        ImageIO.write(img, format, baos);
        baos.flush();

        byte[] bytes = baos.toByteArray();
        baos.close();
        return bytes;
    }

    private static byte[] readTextFile(String pathname, String charset) throws  IOException{
        try(FileInputStream fin = new FileInputStream(rootDirectory + pathname.substring(1))) {
            InputStreamReader isr = new InputStreamReader(fin);
            char[] buffer = new char[fin.available()];
            isr.read(buffer, 0, fin.available());
            if(charset != null)
                return new String(buffer).getBytes(charset);
            else
                return new String(buffer).getBytes();
        }

    }
}
