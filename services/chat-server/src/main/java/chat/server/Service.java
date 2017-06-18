package chat.server;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;
import org.json.JSONArray;
import org.json.JSONException;
import services.common.StorageException;

/**
 * Provides a basic REST chat server.
 */
@Path("")
public class Service {

    /**
     * String for date parsing in ISO 8601 format.
     */
    public static final String ISO8601 = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static SelectorThread threadSelector = null;

    private final static Map<String, User> authCache = new ConcurrentHashMap<>();
    private static boolean useAuthCaching;

    public static void main(String[] args) throws Exception {
        Config.init(args);
        try {
            StorageProviderMongoDB.init();
        } catch (StorageException e) {
            System.out.println("Storage provider already initialized.");
        }
        useAuthCaching = Config.getSettingValue(Config.useAuthCache).equals("true");
        if (!useAuthCaching) {
            System.out.println("Auth caching is disabled.");
        }
        startChatServer(Config.getSettingValue(Config.baseURI));
    }

    public static void startChatServer(String uri) {
        final String baseUri = uri;
        final String paket = "chat.server";
        final Map<String, String> initParams = new HashMap<String, String>();

        initParams.put("com.sun.jersey.config.property.packages", paket);
        System.out.println("Starting grizzly...");
        try {
            threadSelector = GrizzlyWebContainerFactory.create(baseUri, initParams);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.printf("Grizzly running at %s\n", baseUri);

    }

    public static void stopChatServer() {
        //System.exit(0);
        threadSelector.stopEndpoint();
    }


    /**
     * Receives new message from the user.
     *
     * @param json A JSON object containing the fields to,from,date and text.
     * @return If successful returns 204(Created) and a JSON object containing
     * date and sequenceNumber of the Message.
     */
    @PUT
    @Path("/send")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response send(String json) {
        try {
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            Message msg = null;
            try {
                msg = Message.fromJson(json);
            } catch (ParseException e) {
                System.out.println("[/send] Message was badly formatted");
                return Response.status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .entity("Message was incomplete").build();
            }

            if (msg != null && msg.to != null && msg.from != null &&
                    msg.date != null && msg.text != null && msg.token != null) {
                if (authenticateUser(msg.token, msg.from) != null) {
                    User receiver = new User(msg.to);
                    if (receiver.sendMessage(msg) == null) {
                        System.out.println("[/send] DB refused message.");
                        return Response.status(Response.Status.BAD_REQUEST)
                                .header("Access-Control-Allow-Origin", corsOrigin)
                                .entity("Message was not correctly formatted").build();
                    }
                } else {
                    System.out.printf("[/send] Could not authenticate user %s with token %s\n", msg.from, msg.token);
                    return Response.status(Response.Status.UNAUTHORIZED)
                            .entity("Invalid Token")
                            .header("Access-Control-Allow-Origin", corsOrigin)
                            .build();
                }
                try {
                    return Response.status(Response.Status.CREATED)
                            .header("Access-Control-Allow-Origin", corsOrigin)
                            .entity(msg.toJson(true).toString()).build();
                } catch (JSONException e) {
                    e.printStackTrace();
                    return Response
                            .status(Response.Status.INTERNAL_SERVER_ERROR)
                            .header("Access-Control-Allow-Origin", corsOrigin).build();
                }
            } else {
                System.out.println("[/send] Message was incomplete");
                return Response.status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .entity("Message was incomplete").build();
            }
        } catch (Exception e) {
            System.out.printf("[/send] Unhandled exception  %s %s", json, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    /**
     * Queries new messages for the user.
     *
     * @param userID The user's name.
     * @return If successful returns 200(OK) and a JSON array of new messages.
     * If no new messages are available returns 204(No Content).
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/messages/{userid}")
    public Response getMessages(@PathParam("userid") String userID, @Context HttpHeaders header) {
        return getMessages(userID, 0, header);
    }

    /**
     * Queries new messages for the user and removes all messages older than the
     * given sequence number.
     *
     * @param userID         The user's name.
     * @param sequenceNumber The starting sequenceNumber.
     * @return If successful returns 200(OK) and a JSON array of new messages.
     * If no new messages are available returns 204(No Content).
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/messages/{userid}/{sequenceNumber}")
    public Response getMessages(@PathParam("userid") String userID, @PathParam("sequenceNumber") int sequenceNumber,
                                @Context HttpHeaders header) {
        try {
            MultivaluedMap<String, String> map = header.getRequestHeaders();
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            JSONArray jsonMsgs = new JSONArray();
            User receiver = authenticateUser(map.get("Authorization").get(0), userID);
            if (receiver != null) {
                List<Message> newMsgs = receiver.receiveMessages(sequenceNumber);
                if (newMsgs == null) {
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .header("Access-Control-Allow-Origin", corsOrigin)
                            .entity("User not found.").build();
                } else if (newMsgs.isEmpty()) {
                    return Response.status(Response.Status.NO_CONTENT)
                            .header("Access-Control-Allow-Origin", corsOrigin).build();
                } else {
                    for (Message msg : newMsgs) {
                        try {
                            jsonMsgs.put(msg.toJson(false));
                        } catch (JSONException e) {
                            System.out.println("Failed to build json response.");
                            return Response
                                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .header("Access-Control-Allow-Origin", corsOrigin)
                                    .build();
                        }
                    }
                    try {
                        return Response.status(Response.Status.OK)
                                .header("Access-Control-Allow-Origin", corsOrigin)
                                .entity(jsonMsgs.toString(4)).build();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Response
                                .status(Response.Status.INTERNAL_SERVER_ERROR)
                                .header("Access-Control-Allow-Origin", corsOrigin)
                                .build();
                    }
                }
            } else {
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
        } catch (Exception e) {
            System.out.printf("[/messages] Unhandled exception  %s:%d %s", userID, sequenceNumber, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @OPTIONS
    @Path("/send")
    public Response optionsReg() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

    @OPTIONS
    @Path("/messages/{userid}/{sequenceNumber}")
    public Response optionsProfileWithSeqNumber() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

    @OPTIONS
    @Path("/messages/{userid}")
    public Response optionsProfile() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

    private static User authenticateUser(String token, String pseudonym) {
        User cachedUser = authCache.get(pseudonym);
        if (cachedUser != null) {
            if (cachedUser.authenticateUser(token)) {
                return cachedUser;
            } else {
                // Failed to authenticate this user, token was definitely expired.
                authCache.remove(token);
                return null;
            }
        } else {
            User user = new User(pseudonym);
            if (user.authenticateUser(token)) {
                authCache.put(pseudonym, user);
                return user;
            }
        }

        return null;
    }
}