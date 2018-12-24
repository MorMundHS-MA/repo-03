package login.server;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mongodb.MongoClientURI;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;
import services.common.StorageException;
import services.common.StorageProviderCoreMongoDB;

@Path("/")
public class Service {
    private static SelectorThread threadSelector = null;

    /**
     * String for date parsing in ISO 8601 format.
     */
    static final String ISO8601 = "yyyy-MM-dd'T'HH:mm:ssZ";

    private static StorageProviderMongoDB provider;

    public static void main(String[] args) throws Exception {
        try {
            Config.init(args);
        } catch (Exception e) {
            System.out.println("Invalid launch arguments!");
            e.printStackTrace();
            System.exit(-1);
        }

        provider = new StorageProviderMongoDB(new MongoClientURI(Config.mongoURI.value()), Config.dbName.value());

        startLoginServer(Config.baseURI.value());
    }

    public static void startLoginServer(String uri) {
        final String paket = "login.server";
        final Map<String, String> initParams = new HashMap<>();

        initParams.put("com.sun.jersey.config.property.packages", paket);
        System.out.println("Starting grizzly...");
        try {
            threadSelector = GrizzlyWebContainerFactory.create(uri, initParams);
        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
        }

        System.out.printf("Grizzly(loginServer) running at %s%n", uri);

    }

    public static void stopLoginServer() {
        //System.exit(0);
        threadSelector.stopEndpoint();
    }

    /**
     * Logs a user in if his credentials are valid.
     *
     * @param jsonString A JSON object containing the fields user(email) and password.
     * @return Returns a JSON object containing the fields token and
     * expire-date.
     */
    @POST
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response LoginUser(String jsonString) {
        try {
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            String userName, password, pseudonym;
            boolean allowEmailLogin = Objects.equals(Config.getSettingValue(Config.allowEmailLogin), "true");
            try {
                JSONObject obj = new JSONObject(jsonString);
                password = obj.getString("password");
                userName = obj.getString("user");
                pseudonym = obj.optString("pseudonym");
                if (Objects.equals(pseudonym, "")) pseudonym = null;
                System.out.println("user: " + userName);
            } catch (JSONException e) {
                System.out.println("[/login] Failed to parse json request.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            // Check in settings if a login with partial login data is allow.
            if (pseudonym == null && !allowEmailLogin) {
                System.out.println("[/login] User sent incomplete request.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            User user = provider.retrieveUser(userName, pseudonym);
            if (user != null && user.VerifyPassword(password)) {
                JSONObject obj = new JSONObject();
                user.GenerateToken();
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(Service.ISO8601);
                    Calendar expireDate = user.GetTokenExpireDate();
                    sdf.setTimeZone(expireDate.getTimeZone());
                    obj.put("expire-date", sdf.format(expireDate.getTime()));
                    obj.put("token", user.GetToken());
                    obj.put("pseudonym", user.pseudonym);
                } catch (JSONException e) {
                    System.out.println("[/login] Error when building json response.");
                    return Response
                            .status(Response.Status.INTERNAL_SERVER_ERROR)
                            .header("Access-Control-Allow-Origin", corsOrigin)
                            .build();
                }
                SimpleDateFormat sdf = new SimpleDateFormat(Service.ISO8601);
                Calendar expireDate = user.GetTokenExpireDate();
                sdf.setTimeZone(expireDate.getTimeZone());
                provider.saveToken(user.GetToken(), sdf.format(expireDate.getTime()), user.pseudonym);
                System.out.printf("user %s : %s\n", user.pseudonym, user.GetToken());
                return Response
                        .status(Response.Status.OK)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .entity(obj.toString()).build();
            } else {
                System.out.printf("User %s entered a wrong password!", user);
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
        } catch (Exception e) {
            System.out.printf("[/login] Unhandled exception %s %s\n", jsonString, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    /**
     * Validates a user token.
     *
     * @param jsonString A JSON object containing the fields token and pseudonym.
     * @return Returns a JSON object containing the fields expire-date and
     * success.
     */
    @POST
    @Path("/auth")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ValidateToken(String jsonString) {
        try {
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            String token, pseudonym;
            try {
                JSONObject obj = new JSONObject(jsonString);
                token = obj.getString("token");
                pseudonym = obj.getString("pseudonym");
            } catch (JSONException e) {
                System.out.println("[/auth] Failed to parse json request.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            Date expireDate = provider.retrieveTokenExpireDate(pseudonym, token);
            if (expireDate != null) {
                Calendar cal = Calendar.getInstance();
                if (cal.getTime().before(expireDate)) {
                    JSONObject obj = new JSONObject();
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(ISO8601);
                        obj.put("success", "true");
                        obj.put("expire-date", sdf.format(expireDate));
                        return Response
                                .status(Response.Status.OK)
                                .header("Access-Control-Allow-Origin", corsOrigin)
                                .entity(obj.toString()).build();

                    } catch (JSONException e) {
                        System.out.println("[/auth] Error when building json response.");
                        return Response
                                .status(Response.Status.INTERNAL_SERVER_ERROR)
                                .header("Access-Control-Allow-Origin", corsOrigin)
                                .build();
                    }
                } else {
                    // Token has expired
                    provider.deleteToken(token);
                }
            }

            System.out.printf("[/auth] Could not authenticate user %s with token %s\n", pseudonym, token);
            return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .header("Access-Control-Allow-Origin", corsOrigin)
                    .build();
        } catch (Exception e) {
            System.out.printf("[/auth] Unhandled exception  %s %s", jsonString, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @OPTIONS
    @Path("/login")
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
    @Path("/auth")
    public Response optionsProfile() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

}
