package com.kapitonenko.httpserver;

import java.io.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    SelectionKey selectionKey;
    Map<SocketChannel, byte[]> dataToSend;
    SocketChannel socketChannel;
    String response;

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
        } catch (FileNotFoundException | NoSuchFileException e) {
            System.out.println(e.toString());
            sendResponse(createErrorResponse("404", "Not Found"));
        } catch (UnsupportedEncodingException e) {
            System.out.println(e.toString());
            sendResponse(createErrorResponse("400", "Bad request"));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            sendResponse(createErrorResponse("500", "Internal Server Error"));
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

    private String createByteResponse(byte[] img, String contentType, String eTag) {
        return createCommonResponse("200", "OK", "", contentType, img.length, eTag);
    }

    private String createIfMatchResponse(String eTag) {
        return createCommonResponse("304", "Not Modified", "", null, 0, eTag);
    }

    private void sendFile(String pathname) throws Exception {
        String contentType = FileReader.getContentType(pathname);
        if(contentType == null) {
            throw  new FileNotFoundException();
        }

        byte[] temp = FileReader.getResource(pathname, contentType);
        sendByteResponse(temp, createByteResponse(temp, contentType, FileReader.getETag(pathname)));
    }

    private HashMap<String, String> readHeaders(String[] response) {
        HashMap<String, String> headers= new HashMap<>();
        for(int i = 1; i < response.length; i++) {
            String[] header = response[i].split(":");
            headers.put(header[0], header[1].substring(1));
        }
        return headers;
    }

    private void readInput(String response) throws Throwable {
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
            checkCharset(headers);

            if (checkIfNoneMatch(pathname, headers)) return;
            sendFile(pathname);
        } else
            throw new Exception();
    }

    private boolean checkIfNoneMatch(String pathname, HashMap<String, String> headers) throws IOException {
        if(headers.containsKey("If-None-Match")) {
            String eTag = FileReader.getETag(pathname);
            if (headers.get("If-None-Match").equals(eTag)) {
                sendResponse(createIfMatchResponse(eTag));
                return true;
            }
        }
        return false;
    }

    private void checkCharset(HashMap<String, String> headers) throws UnsupportedEncodingException {
        if(headers.containsKey("Accept-Charset")) {
            String charsetTemp = headers.get("Accept-Charset");
            if(charsets.contains(charsetTemp))
                charset = charsetTemp;
            else {
                throw new UnsupportedEncodingException();
            }
        }
    }
}
