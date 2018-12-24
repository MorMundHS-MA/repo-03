package register.server;

import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.BsonArray;
import org.bson.Document;
import services.common.StorageProviderCoreMongoDB;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.addToSet;

public class StorageProviderMongoDB extends StorageProviderCoreMongoDB {
    public StorageProviderMongoDB(MongoClientURI uri, String database) {
        super(uri, database);
    }

    /**
     * Registers a new user.
     *
     * @param user The user to register.
     * @return Returns true if the user could be registered or false if the user already existed or invalid inforamtion
     * was provided.
     */
    public boolean createNewUser(User user) {
        if (user.getEmail() == "" || user.getPseudonym() == "" || userExists(user.getPseudonym(), user.getEmail())) {
            return false;
        }

        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbAccountCollection));
        Document doc = new Document("user", user.getEmail()).append("password", user.getHashedPassword())
                .append("user", user.getEmail()).append("pseudonym", user.getPseudonym()).append("contacts", new BsonArray());
        collection.insertOne(doc);
        return true;
    }

    /**
     * Gets a users a profile from a given pseudonym.
     *
     * @param name The users pseudonym.
     * @return Returns the users profile or null if the user could not be found.
     */
    public User getUserProfile(String name) {
        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbAccountCollection));
        Document doc = collection.find(eq("pseudonym", name)).first();
        if (doc == null) {
            return null;
        }

        String email = doc.getString("user");
        String hashedPW = doc.getString("password");
        @SuppressWarnings("rawtypes")
        List dbContacts = doc.get("contacts", List.class);
        List<String> contacts = new ArrayList<>();
        if (dbContacts != null) {
            for (Object contact : dbContacts) {
                contacts.add(contact.toString());
            }
        }

        return new User(this, name, hashedPW, email, contacts);
    }

    /**
     * Checks if a user with a given email or pseudonym exists. Either of the criteria has to match the user.
     *
     * @param name  The users pseudonym.
     * @param email The users email.
     * @return Returns true if a user with this email or pseudonym already exists.
     */
    public boolean userExists(String name, String email) {
        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbAccountCollection));
        Document doc = collection.find(or(eq("pseudonym", name), eq("user", email))).first();
        return doc != null;
    }

    /**
     * Adds a new contact to a user's contact list.
     *
     * @param user        The user who will gain a new friend.
     * @param contactName The pseudonym of the new contact.
     * @return Returns true if the contact was added to the contact list and false if invalid information was provided or
     * the user is already in the contact list
     */
    public boolean newContact(User user, String contactName) {
        if (contactName == "" || user.getPseudonym() == contactName) {
            return false;
        }

        // TODO : Check if the contact wants to be added?
        MongoCollection<Document> collection = database.getCollection(Config.getSettingValue(Config.dbAccountCollection));
        Document doc = collection.findOneAndUpdate(
                and(eq("pseudonym", user.getPseudonym()), eq("user", user.getEmail())),
                addToSet("contacts", contactName),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE));

        // Doc is state before update so we can check if the contact was already in the contact list.
        if (doc == null || doc.get("contacts", List.class).contains(contactName)) {
            return false;
        } else {
            return true;
        }
    }

    public void clearForTest() {
        deleteCollection(Config.getSettingValue(Config.dbAccountCollection));
    }
}
