package shared.messages;
import java.io.Serializable;

public class HMessage implements Serializable{
    public byte[] message;
    public byte[] hmac;
    public HMessage(byte[] message, byte[] hmac){
        this.message = message;
        this.hmac = hmac;
    }

    public byte[] getMessage(){
        return this.message;
    }

    public byte[] getHmac(){
        return this.hmac;
    }
}
