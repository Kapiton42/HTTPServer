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
        HttpServer server = new HttpServer();
        server.createServer(config);
        server.runServer();
    }

    private static HashMap<String, String> readConfigJson() {
        HashMap<String, String> config = new HashMap<>();
        JSONParser parser = new JSONParser();
        try {
            JSONObject object = (JSONObject) parser.parse(
                    new FileReader("/home/kapiton/config"));
            config.put("port", (String) object.get("port"));
            System.out.println("Port: " + config.get("port"));

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return config;
    }

}
