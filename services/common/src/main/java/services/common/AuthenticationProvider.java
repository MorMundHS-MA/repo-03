package services.common;

import com.sun.jersey.api.client.Client;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.core.MediaType;
import java.text.ParseException;

public class AuthenticationProvider {
    private Client client;
    private String baseURL;

    public AuthenticationProvider(String authServerURL) {
        client = Client.create();
        baseURL = authServerURL;
    }

    public boolean authenticateUser(String token, String userPseudonym) {
        String response;

        JSONObject obj = new JSONObject();
        obj.put("token", token);
        obj.put("pseudonym", userPseudonym);

        try {
            response = client.resource(baseURL + "/auth").accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON).post(String.class, obj.toString());
            client.destroy();
        } catch (RuntimeException e) {
            System.out.printf("Failed to authenticate user %s with token %s caused by : %s", userPseudonym, token, e.getMessage());
            return false;
        }

        JSONObject jo = new JSONObject(response);
        if (jo.get("success").equals("true")) {
            return true;
        }

        System.out.printf(
                "Failed to authenticate user %s with token %s. Auth server response did not indicate success",
                userPseudonym,
                token);
        return false;
    }
}
