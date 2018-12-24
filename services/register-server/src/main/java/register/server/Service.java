package register.server;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mongodb.MongoClientURI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;
import services.common.StorageException;

@Path("/")
public class Service {
    private static final String ISO8601 = "yyyy-MM-dd'T'HH:mm:ssZ";
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
        startRegistrationServer(Config.baseURI.value());
    }

    public static SelectorThread startRegistrationServer(String uri) {
        final String baseUri = uri;
        final String paket = "register.server";
        final Map<String, String> initParams = new HashMap<>();
        SelectorThread threadSelector = null;
        initParams.put("com.sun.jersey.config.property.packages", paket);
        System.out.println("Starte grizzly...");
        try {
            threadSelector = GrizzlyWebContainerFactory.create(baseUri, initParams);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.printf("Grizzly(registerServer) running at %s%n", baseUri);
        return threadSelector;
    }

    public static void stopRegisterServer(SelectorThread threadSelector) {
        threadSelector.stopEndpoint();
    }

    @PUT
    @Path("/register")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response RegisterNewUser(String jsonString) {
        try {
            String pseudonym, email, password;
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            try {
                JSONObject obj = new JSONObject(jsonString);
                password = obj.getString("password");
                email = obj.getString("user");
                pseudonym = obj.getString("pseudonym");

            } catch (JSONException e) {
                System.out.println("[/register] User send invalid json data.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            if (!isValidEmail(email) || !isValidPassword(password) || !isValidPseudonym(email)) {
                System.out.println("[/register] A registration value was invalid.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            User user = new User(provider, pseudonym, password, email);
            if (provider.userExists(pseudonym, email)) {
                System.out.printf("[/register] User %s was already registered and is potentially a teapot.\n", email);
                return Response
                        .status(418)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            if (provider.createNewUser(user)) {
                JSONObject obj = new JSONObject();
                obj.append("success", "true");
                System.out.printf("[/register] Added new user %s. \n", email);
                return Response
                        .status(Response.Status.OK)
                        .header("Access-Control-Allow-Origin", corsOrigin).entity(obj.toString()).build();
            } else {
                System.out.println("[/register] Registration failed.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
        } catch (Exception e) {
            System.out.printf("[/register] Unhandled exception  %s %s", jsonString, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @POST
    @Path("/profile")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getUserProfile(String jsonString) {
        try {
            String token, pseudonym;
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            try {
                JSONObject obj = new JSONObject(jsonString);
                token = obj.getString("token");
                pseudonym = obj.getString("getownprofile");

            } catch (JSONException e) {
                System.out.println("[/profile] User send invalid json data.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }

            if (verifyToken(pseudonym, token)) {
                User user = provider.getUserProfile(pseudonym);
                JSONObject obj = new JSONObject();
                obj.append("name", user.getPseudonym());
                obj.append("email", user.getEmail());
                JSONArray contacts = new JSONArray(user.getContacts());
                obj.append("contact", contacts);

                return Response
                        .status(Response.Status.OK)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .entity(obj.toString()).build();
            } else {
                System.out.printf("[/profile] User %s sent invalid token %s to authenticate.\n", pseudonym, token);
                return Response
                        .status(Response.Status.FORBIDDEN)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
        } catch (Exception e) {
            System.out.printf("[/profile] Unhandled exception  %s %s", jsonString, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @PUT
    @Path("/addcontact")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addContact(String jsonString) {
        try {
            String token, pseudonym, newContact;
            String corsOrigin = Config.getSettingValue(Config.corsAllowOrigin);
            try {
                JSONObject obj = new JSONObject(jsonString);
                token = obj.getString("token");
                pseudonym = obj.getString("pseudonym");
                newContact = obj.getString("newContact");
            } catch (JSONException e) {
                System.out.println("[/addcontact] User send invalid json data.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("Invalid json data.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            if (token == null || pseudonym == null || newContact == null || token.equals("") || pseudonym.equals("") || newContact.equals("")) {
                System.out.println("[/addcontact] User send incomplete json data.");
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("Incomplete json data.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            if (!verifyToken(pseudonym, token)) {
                System.out.println("[/addcontact] User sent invalid token to authenticate.");
                return Response
                        .status(Response.Status.FORBIDDEN)
                        .entity("Invalid token.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            if (pseudonym.equals(newContact)) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("Can't add self to contact list.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            User user = provider.getUserProfile(pseudonym);
            User contact = provider.getUserProfile(newContact);
            if (user == null || contact == null) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("The user doesn't exist.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
            if (user.addContact(contact) == null) {
                return Response
                        .status(Response.Status.FORBIDDEN)
                        .entity("The user cannot be added to the contact list.")
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            } else {
                return Response
                        .status(Response.Status.OK)
                        .header("Access-Control-Allow-Origin", corsOrigin)
                        .build();
            }
        } catch (Exception e) {
            System.out.printf("[/addContact] Unhandled exception  %s %s", jsonString, e.getMessage());
            e.printStackTrace();
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @OPTIONS
    @Path("/register")
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
    @Path("/profile")
    public Response optionsProfile() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

    @OPTIONS
    @Path("/addcontact")
    public Response optionsAddContact() {
        return Response.ok("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Max-Age", "1209600")
                .build();
    }

    private static boolean verifyToken(String pseudonym, String token) {
        try {
            JSONObject obj = new JSONObject();
            try {
                obj.put("token", token);
                obj.put("pseudonym", pseudonym);
            } catch (JSONException e) {
                System.out.println("Failed to build authorization request json.");
                return false;
            }

            // TODO : Maybe use one static client?
            Client webClient = new Client();
            String response = webClient.resource(Config.getSettingValue(Config.loginURI) + "auth")
                    .accept(MediaType.APPLICATION_JSON)
                    .type(MediaType.APPLICATION_JSON)
                    .post(String.class, obj.toString());
            webClient.destroy();

            SimpleDateFormat sdf = new SimpleDateFormat(ISO8601);
            JSONObject jo = new JSONObject(response);
            if (jo.getString("success").equals("true")) {
                try {
                    Date expireDate = sdf.parse(jo.getString("expire-date"));
                    if (expireDate.after(new Date())) {
                        return true;
                    } else {
                        System.out.printf("Could not verify user %s. Token expired. \n", pseudonym);
                        return false;
                    }
                } catch (JSONException | ParseException e) {
                    System.out.printf("Could not verify user %s. Failed to parse auth json. \n", pseudonym);
                    return false;
                }
            }
        } catch (Exception e) {
            System.out.printf("Uncaught exception occurred during user authentication %s  \n", e.getMessage());
            return false;
        }

        return false;
    }

    private static boolean isValidEmail(String email) {
        // TODO : Use actual verification
        return email.length() > 3;
    }

    private static boolean isValidPseudonym(String pseudonym) {
        // TODO : Use actual verification
        return pseudonym.length() > 4;
    }

    private static boolean isValidPassword(String password) {
        // TODO : Use actual verification
        return true;
    }

}
