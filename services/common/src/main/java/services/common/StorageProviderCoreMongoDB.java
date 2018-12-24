package services.common;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.ne;

public class StorageProviderCoreMongoDB {
    protected MongoDatabase database;
    private StorageProviderCoreMongoDB sp;

    public StorageProviderCoreMongoDB(MongoClientURI uri, String database) {
        this.database = new MongoClient(uri).getDatabase(database);
    }

    protected MongoCollection<Document> deleteCollection(String collectionName) {
        MongoCollection<Document> collection = database.getCollection(collectionName);
        // Deletes all items in the collection
        collection.drop();
        return collection;
    }
}
