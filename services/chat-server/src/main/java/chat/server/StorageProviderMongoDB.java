package chat.server;

import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import services.common.StorageProviderCoreMongoDB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class StorageProviderMongoDB extends StorageProviderCoreMongoDB{

    private static final int sequenceOffset = 1;

    public StorageProviderMongoDB(MongoClientURI uri, String database) {
        super(uri, database);
    }

    public int addMessage(User user, Message msg) {
        if (user.getName() == null || user.getName().isEmpty()) return -1;
        if (!user.getName().equals(msg.to) || msg.from == null || msg.from.equals("") || msg.date == null) return -1;
        MongoCollection<Document> messages = database.getCollection(Config.getSettingValue(Config.dbChatCollection));
        MongoCollection<Document> sequences = database.getCollection(Config.getSettingValue(Config.dbSequenceCollection));

        Document sequence =
                sequences.findOneAndUpdate(
                        eq("user", user.getName()),
                        combine(set("user", user.getName()), inc("sequence", 1)),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (sequence == null) {
            sequence = new Document("user", user.getName()).append("sequence", sequenceOffset);
            sequences.insertOne(sequence);
        }

        msg.sequence = sequence.getInteger("sequence");

        messages.insertOne(messageToDoc(msg));

        return msg.sequence;
    }

    public List<Message> getMessages(User user, int sequenceBegin) {
        MongoCollection<Document> messages = database.getCollection(Config.getSettingValue(Config.dbChatCollection));
        MongoCollection<Document> sequences = database.getCollection(Config.getSettingValue(Config.dbSequenceCollection));
        if (user.getName() == null || user.getName().equals("")) return null;

        List<Message> newMessages = new ArrayList<>();
        Document sequence = sequences.find(eq("user", user.getName())).first();
        if (sequence == null) return newMessages;
        if (sequence.getInteger("sequence") < sequenceBegin) return null;
        FindIterable<Document> docs;

        // Get all if sequenceBegin is zero
        if (sequenceBegin == 0) {
            docs = messages.find(eq("to", user.getName()));
        } else {
            docs = messages.find(
                    and(eq("to", user.getName()), gt("sequence", sequenceBegin)));
        }

        docs.forEach((Consumer<? super Document>) document -> newMessages.add(messageFromDoc(document)));

        return newMessages;
    }

    public boolean removeMessages(User user, int sequenceBegin) {
        MongoCollection<Document> messages = database.getCollection(Config.getSettingValue(Config.dbChatCollection));
        MongoCollection<Document> sequences = database.getCollection(Config.getSettingValue(Config.dbSequenceCollection));
        if (user.getName() == null || user.getName().equals("")) return false;

        Document sequence = sequences.find(eq("user", user.getName())).first();
        if (sequence == null || sequence.getInteger("sequence") < sequenceBegin) return false;

        messages.deleteMany(and(eq("to", user.getName()), lte("sequence", sequenceBegin)));
        return true;
    }

    private static Document messageToDoc(Message msg) {
        return new Document("to", msg.to)
                .append("from", msg.from)
                .append("text", msg.text)
                .append("sequence", msg.sequence)
                .append("date", msg.date);
    }

    private static Message messageFromDoc(Document msgDoc) {
        return new Message(
                msgDoc.getString("from"),
                msgDoc.getString("to"),
                msgDoc.getDate("date"),
                msgDoc.getString("text"),
                null,
                msgDoc.getInteger("sequence"));
    }

    public void clearForTests() {
        deleteCollection(Config.getSettingValue(Config.dbChatCollection));
        deleteCollection(Config.getSettingValue(Config.dbSequenceCollection));
    }
}
