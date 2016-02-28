package com.kapitonenko.httpserver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by kapiton on 05.02.16.
 */
public class HttpServer {
    private ServerSocket serverSocket;

    private static HashSet<String> charsets;
    static {
        charsets = new HashSet<>();
        charsets.add("UTF-8");
        charsets.add("US-ASCII");
    }

    public void createServer(HashMap<String, String> config) {
        try {
            serverSocket = new ServerSocket(Integer.parseInt(config.get("port")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runServer() {
        ExecutorService service = Executors.newCachedThreadPool();
        while (true) {
            Socket s = null;
            try {
                s = serverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                service.submit(new SocketProcessor(s));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private static class SocketProcessor implements Runnable {

        private Socket s;
        private InputStream is;
        private OutputStream os;

        private String charset = "UTF-8";

        private SocketProcessor(Socket s) throws Throwable {
            this.s = s;
            this.is = s.getInputStream();
            this.os = s.getOutputStream();
        }

        public void run() {
            try {
                readInput();
            } catch (Throwable throwable) {
                sendResponse(createErrorResponse("400", "Bad request"));
            } finally {
                try {
                    s.close();
                } catch (Throwable t) {
                    /*do nothing*/
                }
            }
        }

        private void sendByteResponse(byte[] bytes, String s) {
            try {
                os.write(s.getBytes(charset));
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
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
            String contentType = getContentType(pathname);

            if(contentType.equals("application/javascript") || contentType.equals("text/html")
                    || contentType.equals("text/css")) {
                String fileString = readTextFile(pathname);
                sendResponse(createTextResponse(fileString, getContentType(pathname), getETag(pathname)));
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
            }
        }

        private String getETag(String pathname) throws IOException {
            BasicFileAttributes attr = null;

            Path path = Paths.get("/home/kapiton" + pathname.substring(1));
            attr = Files.readAttributes(path, BasicFileAttributes.class);

            Object fileKey = attr.fileKey();
            String s = fileKey.toString();
            String inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(")"));

            return (inode + attr.lastModifiedTime() + attr.size()).replace(":", "-");
        }

        private void sendImage(String pathname, String format, String contentType) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            BufferedImage img = ImageIO.read(new File("/home/kapiton" + pathname.substring(1)));
            ImageIO.write(img, format, baos);
            baos.flush();

            byte[] bytes = baos.toByteArray();
            baos.close();

            sendByteResponse(bytes, createImageResponse(bytes, contentType, getETag(pathname)));
        }

        private String getContentType(String pathname) {
            String contentType = null;

            Pattern p = Pattern.compile(".*\\.js$");
            Matcher m = p.matcher(pathname);

            if(m.find())
                contentType = "application/javascript";

            p = Pattern.compile(".*\\.html$");
            m = p.matcher(pathname);

            if(m.find())
                contentType = "text/html";

            p = Pattern.compile(".*\\.css$");
            m = p.matcher(pathname);

            if(m.find())
                contentType = "text/css";

            p = Pattern.compile(".*\\.jpg$");
            m = p.matcher(pathname);

            if(m.find())
                contentType = "image/jpg";

            p = Pattern.compile(".*\\.gif$");
            m = p.matcher(pathname);

            if(m.find())
                contentType = "image/gif";

            p = Pattern.compile(".*\\.png$");
            m = p.matcher(pathname);

            if(m.find())
                contentType = "image/png";

            return contentType;
        }

        private String readTextFile(String pathname) throws IOException {
            String fileString = null;

            File f = new File("/home/kapiton" + pathname.substring(1));

            if(!f.exists() || f.isDirectory()) {
                sendResponse(createErrorResponse("404", "Not found"));
            } else {
                Path path = Paths.get("/home/kapiton" + pathname.substring(1));
                try (Stream<String> lines = Files.lines(path)) {
                    fileString = lines.collect(Collectors.joining("\n"));
                }
            }
            return fileString;
        }

        private HashMap<String, String> readHeaders(BufferedReader br) throws IOException {
            HashMap<String, String> headers= new HashMap<>();
            while(true) {
                String s = br.readLine();
                if(s == null || s.trim().length() == 0) {
                    break;
                }
                String[] header = s.split(":");
                headers.put(header[0], header[1].substring(1));
            }
            return headers;
        }

        private void readInput() throws Throwable {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            String firstString = br.readLine();

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
                HashMap<String, String> headers = readHeaders(br);
                if(headers.containsKey("Accept-Charset")) {
                    String charsetTemp = headers.get("Accept-Charset");
                    if(charsets.contains(charsetTemp))
                        charset = charsetTemp;
                    else {
                        throw new UnsupportedEncodingException();
                    }
                }

                if(headers.containsKey("If-None-Match")) {
                    String eTag = getETag(pathname);
                    if (headers.get("If-None-Match").equals(eTag)) {
                        //sendResponse(createIfMatchResponse(eTag));
                        //return;
                    }
                }
                sendFile(pathname);
            } else
                throw new Exception();
        }
    }
}
