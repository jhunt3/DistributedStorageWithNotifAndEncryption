package shared.comm;

import shared.messages.KVMessage;
import shared.messages.KVMsg;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;

public interface ICommModule {

    public KVMessage receiveMsg() throws IOException, ClassNotFoundException;

    public void sendMsg(KVMessage.StatusType status, String key, String value, HashMap<String,String> metadata) throws IOException;

    public KVMsg serve(KVMsg msg) throws Exception;

    public void closeConnection();

    public BigInteger receiveSecret() throws Exception;

    public void sendSecret() throws IOException;

    public void setKey(BigInteger secret);
}
