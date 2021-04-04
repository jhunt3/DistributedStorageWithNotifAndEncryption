package shared.messages;

import java.io.Serializable;
import java.util.HashMap;

public class KVMsg implements KVMessage, Serializable {

    private static final long serialVersionUID = 8006348832096871261L;
    private StatusType status;
    private String key;
    private String value;
    private HashMap<String,String> metadata = null;

    /**
     * @param status Request status
     * @param key Key string. Assumed to be less than 20B
     * @param value Value string. Assumed to be less than 120kB
     */
    public KVMsg(StatusType status, String key, String value) {
        this.status = status;
        this.key = key;
        this.value = value;
    }

    // Constructor overloading for when it is necessary to send the metadata from the server to KVStore
    public KVMsg(StatusType status, String key, String value, HashMap<String,String> metadata) {
        this.status = status;
        this.key = key;
        this.value = value;
        this.metadata = metadata;
    }

    /**
     * @return the key that is associated with this message,
     * 		null if not key is associated.
     */
    public String getKey() {
        return this.key;
    }

    /**
     * @return the value that is associated with this message,
     * 		null if not value is associated.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * @return a status string that is used to identify request types,
     * response types and error types associated to the message.
     */
    public StatusType getStatus() {
        return this.status;
    }

    public HashMap<String,String> getMetadata() { return this.metadata; }

    @Override
    public boolean isAdminMessage() {
        return false;
    }
}
