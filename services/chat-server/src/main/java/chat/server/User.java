package chat.server;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;

import javax.ws.rs.core.MediaType;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.jersey.api.client.Client;

/**
 * A chat user. Contains the user's name and information about his messages.
 */
public class User {

    private static final boolean removeOldMessages = true;
    private String name;
    private StorageProviderMongoDB provider;

    /**
     * The user's token.
     */
    private String token;

    /**
     * The expiration date of the token.
     */
    private Date expireDate;

    /**
     * Creates a new user with the given name.
     *
     * @param name            The user's name.
     * @param storageProvider The storage provider used for persisting user messages.
     */
    public User(StorageProviderMongoDB storageProvider, String name) {
        this.provider = storageProvider;
        this.name = name;
    }

    /**
     * Sends a message to the user. The method returns a message object with the
     * correct sequence number.
     *
     * @param msg The message to sent to the user.
     * @return The sent message with the correct sequence number.
     */
    public Message sendMessage(Message msg) {
        int seq = provider.addMessage(this, msg);
        if (seq == -1) {
            return null;
        }

        System.out.println(String.format("%s -> %s [%d]: %s", msg.from, msg.to, msg.sequence, msg.text));
        return msg;
    }

    /**
     * Gets all received message with a sequence number higher than the
     * parameter. Returned messages are deleted.
     *
     * @param sequenceNumber The last sequence number received by the client or 0 to fetch
     *                       all available messages.
     * @return Returns all message with a sequence number lower than the
     * parameter.
     */
    public List<Message> receiveMessages(int sequenceNumber) {
        List<Message> recvMsgs = provider.getMessages(this, sequenceNumber);
        if (recvMsgs == null) {
            return null;
        }

        // Remove all message with a sequence <= the parameter. This removes all
        // messages from storage that
        // the client confirmed as received.
        if (User.removeOldMessages && sequenceNumber > 0) {
            provider.removeMessages(this, sequenceNumber);
        }

        return recvMsgs;
    }

    /**
     * Method to authenticate a user with his token.
     *
     * @return Returns true if the authentication was successful and false if
     * not
     */
    public boolean authenticateUser(String token) {
        SimpleDateFormat sdf = new SimpleDateFormat(Service.ISO8601);
        if (this.token == token) {
            if (sdf.format(new Date()).compareTo(expireDate.toString()) < 0) {
                return true;
            }
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("token", token);
            obj.put("pseudonym", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Client client = Client.create();
        String response;
        try {
            response = client.resource(Config.getSettingValue(Config.loginURI) + "/auth").accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON).post(String.class, obj.toString());
            client.destroy();
        } catch (RuntimeException e) {
            System.out.printf("Failed to authenticate user %s with token %s caused by : %s", this.getName(), token, e.getMessage());
            return false;
        }

        JSONObject jo = new JSONObject(response);
        if (jo.get("success").equals("true")) {
            try {
                this.expireDate = sdf.parse(jo.getString("expire-date"));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return true;
        }
        System.out.printf(
                "Failed to authenticate user %s with token %s. Auth server response did not indicate success",
                this.getName(),
                token);
        return false;
    }

    /**
     * The user's name.
     */
    public String getName() {
        return name;
    }
}
