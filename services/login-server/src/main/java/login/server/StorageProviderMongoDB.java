package login.server;

import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import services.common.StorageException;
import services.common.StorageProviderCoreMongoDB;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Storage provider for a MongoDB.
 */
public class StorageProviderMongoDB extends StorageProviderCoreMongoDB {

    public StorageProviderMongoDB(MongoClientURI uri, String database) {
        super(uri, database);
    }

    /**
     * Retrieves a user's info from the database. If Config.allowEmailLogin is true pseudonym can be null.
     *
     * @param username  The user's email.
     * @param pseudonym The user's pseudonym.
     * @return Returns the user's data or null if the user wasn't found.
     */
    public User retrieveUser(String username, String pseudonym) {

        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbAccountCollection));
        Document doc;
        // Retrieve the hashed password
        if (pseudonym == null && Objects.equals(Config.getSettingValue(Config.allowEmailLogin), "true")) {
            doc = collection.find(eq("user", username)).first();
        } else {
            doc = collection.find(and(eq("user", username), eq("pseudonym", pseudonym))).first();
        }

        if (doc == null) {
            return null;
        }

        return new User(username, doc.getString("password"), doc.getString("pseudonym"), true);
    }

    /**
     * Saves the token with the given expire date into the database.
     *
     * @param token          The users new token.
     * @param expirationDate The tokens expire date.
     * @param pseudonym      The users pseudonym.
     */
    public void saveToken(String token, String expirationDate, String pseudonym) {

        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbTokenCollection));

        // add user to database
        Document doc = new Document("token", "" + token + "").append("expire-date", expirationDate).append("pseudonym",
                pseudonym);

        if (collection.find(eq("pseudonym", pseudonym)).first() != null) {
            collection.updateOne(eq("pseudonym", pseudonym), new Document("$set", doc));
        } else {
            collection.insertOne(doc);
        }
    }

    /**
     * Fetches a user's current token expire date. If the token is not found or expired null is returned.
     *
     * @param pseudonym The user's pseudonym.
     * @param token     The user's current token.
     * @return The token's expiration date or null if the token was not found or is expired.
     */
    public Date retrieveTokenExpireDate(String pseudonym, String token) {
        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbTokenCollection));
        // Retreive the tokeninformation
        Document doc = collection.find(and(eq("pseudonym", pseudonym), eq("token", token))).first();
        if (doc == null) {
            return null;
        }
        String expDateString = doc.getString("expire-date");
        if (expDateString == null) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(Service.ISO8601);
        Calendar expireDate = Calendar.getInstance();
        try {
            expireDate.setTime(sdf.parse(expDateString));
        } catch (ParseException e) {
            // The token seems to be corrupted.
            collection.deleteOne(and(eq("pseudonym", pseudonym), eq("token", token)));
            return null;
        }

        Calendar currentTime = Calendar.getInstance();
        if (currentTime.before(expireDate))
            return expireDate.getTime();
        else
            System.out.printf("User %s's token has expired for %f s.\n",
                    pseudonym,
                    (currentTime.getTimeInMillis() - expireDate.getTimeInMillis()) / 1000f);
        return null;
    }

    /**
     * Removes a token from the token db.
     *
     * @param token The token to remove.
     */
    public void deleteToken(String token) {
        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbTokenCollection));
        collection.deleteOne(eq("token", token));
    }

    public void clearForTest(User[] newUsers) {
        MongoCollection<Document> collection = deleteCollection(Config.getSettingValue(Config.dbAccountCollection));
        deleteCollection(Config.getSettingValue(Config.dbTokenCollection));

        for (User u :
                newUsers) {
            Document doc = new Document();
            doc.append("user", u.email);
            doc.append("pseudonym", u.pseudonym);
            doc.append("password", u.getSecurePassword());
            collection.insertOne(doc);
        }
    }
}