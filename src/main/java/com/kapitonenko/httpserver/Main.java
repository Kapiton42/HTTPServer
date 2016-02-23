package com.kapitonenko.httpserver;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;

/**
 * Created by kapiton on 05.02.16.
 */
public class Main {
    public static void main(String[] args) {
        HashMap<String, String> config = readConfigJson();

        com.kapitonenko.httpserver.FileReader.initFileReader(config.get("rootDir"));
        com.kapitonenko.httpserver.FileReader.createWatcher();

        NIOHttpServer server;
        try {
            server = new NIOHttpServer(Integer.parseInt(config.get("port")));
            server.startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HashMap<String, String> readConfigJson() {
        HashMap<String, String> config = new HashMap<>();
        JSONParser parser = new JSONParser();
        try {
            JSONObject object = (JSONObject) parser.parse(
                    new FileReader("/home/kapiton/config"));
            String temp = (String) object.get("port");
            if(temp == null)
                temp = "8080";
            config.put("port", temp);
            System.out.println("Port: " + config.get("port"));

            temp = (String) object.get("rootDir");
            if(temp == null)
                temp = "/home/kapiton/Http-root";
            config.put("rootDir", temp);
            System.out.println("RootDirectory: " + config.get("rootDir"));

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return config;
    }

}
