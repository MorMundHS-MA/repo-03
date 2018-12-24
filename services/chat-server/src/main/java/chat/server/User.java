package chat.server;

import java.util.Date;
import java.util.List;

/**
 * A chat user. Contains the user's name and information about his messages.
 */
public class User {

    private static final boolean removeOldMessages = true;
    private String name;
    private StorageProviderMongoDB provider;

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
     * The user's name.
     */
    public String getName() {
        return name;
    }
}
