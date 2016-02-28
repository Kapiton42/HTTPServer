package com.kapitonenko.httpserver;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by kapiton on 05.02.16.
 */
public class Main {
    private static Map<String, String> configFields;
    static {
        configFields = new HashMap<>();
        configFields.put("port", "8080");
        configFields.put("rootDir", "root");
        configFields.put("cashing", "all");
    }

    public static void main(String[] args) {
        if(args.length == 1) {
            readConfigJson(args[0]);
        } else {
            printConfig();
        }

        com.kapitonenko.httpserver.FileReader.initFileReader(configFields.get("rootDir"));
        com.kapitonenko.httpserver.FileReader.createWatcher();
        com.kapitonenko.httpserver.FileReader.setCashingFiles(configFields.get("cashing"));

        NIOHttpServer server;
        try {
            server = new NIOHttpServer(Integer.parseInt(configFields.get("port")));
            server.startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readConfigJson(String pathConfig) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject object = (JSONObject) parser.parse(
                    new FileReader(pathConfig));
            for(Map.Entry field: configFields.entrySet()) {
                String temp = (String) object.get(field.getKey());
                if(temp != null)
                    configFields.put((String) field.getKey(), temp);
            }
            printConfig();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private static void printConfig() {
        for(Map.Entry field: configFields.entrySet()) {
            System.out.println(field.getKey() + ": " + field.getValue());
        }
    }

}
