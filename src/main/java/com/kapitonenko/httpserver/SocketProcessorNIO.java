package com.kapitonenko.httpserver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by kapiton on 21.02.16.
 */
public class SocketProcessorNIO implements Runnable {

    private static HashSet<String> charsets;
    static {
        charsets = new HashSet<>();
        charsets.add("UTF-8");
        charsets.add("US-ASCII");
    }

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

    SelectionKey selectionKey;
    Map<SocketChannel, byte[]> dataToSend;
    SocketChannel socketChannel;
    String response;

    public static String rootDir = "/home/kapiton/Http-root";

    private String charset = "UTF-8";

    public SocketProcessorNIO(SocketChannel socketChannel, SelectionKey selectionKey,
                              Map<SocketChannel, byte[]> dataToSend, String getResponse) throws Throwable {
        this.selectionKey = selectionKey;
        this.dataToSend = dataToSend;
        this.socketChannel = socketChannel;
        this.response = getResponse;
    }

    public void run() {
        try {
            readInput(response);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            sendResponse(createErrorResponse("404", "Not Found"));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            sendResponse(createErrorResponse("500", "Bad request"));
        }
    }

    private void sendByteResponse(byte[] bytes, String s) {
        try {
            byte[] combined;
            if(bytes != null) {
                byte[] one = s.getBytes();
                combined = new byte[one.length + bytes.length];

                System.arraycopy(one, 0, combined, 0, one.length);
                System.arraycopy(bytes, 0, combined, one.length, bytes.length);
            } else {
                combined = s.getBytes();
            }
            dataToSend.put(socketChannel, combined);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            //socketChannel.write(ByteBuffer.wrap(combined));
            //selectionKey.cancel();
            //socketChannel.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(String s) {
        sendByteResponse(null, s);
    }

    private String createCommonResponse(String code, String cause, String text,
                                        String contentType, int contentLength, String eTag) {
        String response = "";
        response += "HTTP/1.1 " + code + " " + cause +"\r\n" +
                "Server: Http-server\r\n" +
                "Content-Length: " + contentLength + "\r\n";
        if(contentType != null)
            response += "Content-Type: " + contentType + "; charset=" + charset + "\r\n";
        if(eTag != null)
            response +=  "ETag: " + eTag + "\r\n";
        return response +
                "Connection: close\r\n\r\n" +
                text;
    }

    private String createErrorResponse(String code, String cause) {
        String htmlText = "<html><body><h1>" + code + " : " + cause + "</h1></body></html>";
        return createCommonResponse(code, cause, htmlText, "text/html", htmlText.length(), null);
    }

    private String createTextResponse(String text, String contentType, String eTag) {
        return createCommonResponse("200", "OK", text, contentType, text.length(), eTag);
    }

    private String createImageResponse(byte[] img, String contentType, String eTag) {
        return createCommonResponse("200", "OK", "", contentType, img.length, eTag);
    }

    private String createIfMatchResponse(String eTag) {
        return createCommonResponse("304", "Not Modified", "", null, 0, eTag);
    }

    private void sendFile(String pathname) throws Exception {
        String contentType = FileReader.getContentType(pathname);
        if(contentType == null) {
            throw  new Exception();
        }

        byte[] temp = FileReader.getResource(pathname, contentType);
        sendByteResponse(temp, createImageResponse(temp, contentType, getETag(pathname)));
        /*String contentType = getContentType(pathname);
        if(contentType == null) {
            return;
        }

        if(contentType.equals("application/javascript") || contentType.equals("text/html")
                || contentType.equals("text/css")) {
            //String fileString = readTextFile(pathname);
            //sendResponse(createTextResponse(fileString, contentType, getETag(pathname)));
            byte[] temp = readTextFileByte(pathname);
            sendByteResponse(temp, createImageResponse(temp, contentType, getETag(pathname)));
        }

        Pattern p = Pattern.compile("^image/.*");
        Matcher m = p.matcher(contentType);

        if(m.find()) {
            p = Pattern.compile("\\w*$");
            m = p.matcher(contentType);
            if(m.find())
                sendImage(pathname, m.group(), contentType);
            else {
                throw new Exception();
            }
        }*/
    }

    private String getETag(String pathname) throws IOException {
        BasicFileAttributes attr = null;

        Path path = Paths.get(rootDir + pathname.substring(1));
        attr = Files.readAttributes(path, BasicFileAttributes.class);

        Object fileKey = attr.fileKey();
        String s = fileKey.toString();
        String inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(")"));

        return (inode + attr.lastModifiedTime() + attr.size()).replace(":", "-");
    }

    private void sendImage(String pathname, String format, String contentType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        BufferedImage img = ImageIO.read(new File(rootDir + pathname.substring(1)));
        ImageIO.write(img, format, baos);
        baos.flush();

        byte[] bytes = baos.toByteArray();
        baos.close();

        sendByteResponse(bytes, createImageResponse(bytes, contentType, getETag(pathname)));
    }

    private String getContentType(String pathname) {
        String contentType = null;

        Pattern p = Pattern.compile("\\.\\w*$");
        Matcher m = p.matcher(pathname);

        if(m.find())
            contentType = contentTypesMap.get(m.group().substring(1));
        if(contentType == null)
            sendResponse(createErrorResponse("400", "Bad Request"));

        return contentType;
    }

    private String readTextFile(String pathname) throws IOException {
        String fileString = null;

        File f = new File(rootDir + pathname.substring(1));

        if(!f.exists() || f.isDirectory()) {
            sendResponse(createErrorResponse("404", "Not found"));
        } else {
            Path path = Paths.get(rootDir + pathname.substring(1));
            try (Stream<String> lines = Files.lines(path)) {
                fileString = lines.collect(Collectors.joining("\n"));
            }
        }
        return fileString;
    }

    private byte[] readTextFileByte(String pathname) throws  IOException{
        try(FileInputStream fin = new FileInputStream(rootDir + pathname.substring(1))) {
            InputStreamReader isr = new InputStreamReader(fin);
            char[] buffer = new char[fin.available()];
            isr.read(buffer, 0, fin.available());
            return new String(buffer).getBytes(charset);
        }

    }

    private HashMap<String, String> readHeaders(String[] response) throws IOException {
        HashMap<String, String> headers= new HashMap<>();
        for(int i = 1; i < response.length; i++) {
            String[] header = response[i].split(":");
            headers.put(header[0], header[1].substring(1));
        }
        return headers;
    }

    private void readInput(String response) throws Throwable {
        System.out.print(response);

        String[] responseParsed = response.split("\r\n");

        String firstString = responseParsed[0];

        Pattern p = Pattern.compile("^GET");
        Matcher m = p.matcher(firstString);

        if(!m.find()) {
            sendResponse(createErrorResponse("405", "Method Not Allowed"));
            return;
        }

        p = Pattern.compile(" \\/\\S*");
        m = p.matcher(firstString);
        if(m.find()) {
            String pathname = m.group();
            HashMap<String, String> headers = readHeaders(responseParsed);
            if(headers.containsKey("Accept-Charset")) {
                String charsetTemp = headers.get("Accept-Charset");
                if(charsets.contains(charsetTemp))
                    charset = charsetTemp;
                else {
                    throw new UnsupportedEncodingException();
                }
            }

            /*if(headers.containsKey("If-None-Match")) {
                String eTag = getETag(pathname);
                if (headers.get("If-None-Match").equals(eTag)) {
                    sendResponse(createIfMatchResponse(eTag));
                    return;
                }
            }*/
            sendFile(pathname);
        } else
            throw new Exception();
    }
}
