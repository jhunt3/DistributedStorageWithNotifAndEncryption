package shared.comm;

import app_kvServer.KVServer;
import org.apache.log4j.Logger;
import shared.messages.KVAdminMsg;
import shared.messages.KVMessage;
import shared.messages.KVMsg;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static shared.messages.KVMessage.StatusType;
import static shared.messages.KVMessage.StatusType.*;

public class CommModule implements ICommModule, Runnable {

    private static Logger logger = Logger.getRootLogger();

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private KVServer server;
    private boolean isOpen;
    private int secretInt; // x or y in g^(xy) mod p for Diffie-Hellman key exchange
    private int key; // encryption key
    private int g = 2; // generator number
    private BigInteger p = new BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                                            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                                            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                                            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                                            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                                            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                                            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                                            "670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF", 16);

    /**
     * @param socket Client Socket (output of socket.accept() for the server, socket for the client), or ECS Socket.
     * @param server Server object if the module is being instantiated in the server, null otherwise.
     */
    public CommModule(Socket socket, KVServer server) {
        try {
            this.output = new ObjectOutputStream(socket.getOutputStream());
            this.output.flush();
            this.input = new ObjectInputStream(socket.getInputStream());
            if (server != null) {
                logger.info("Server communication module connected");
            } else {
                logger.info("Client communication module connected");
            }
        } catch (IOException ioe) {
            logger.error("Error! Connection could not be established! ", ioe);
        }
        this.isOpen = true;
        this.server = server;
        this.socket = socket;

        Random random = new Random();
        this.secretInt = random.nextInt(65536); // Secret is a 16-bit unsigned number
	if (server != null) {
		logger.debug("Server secret int: " + String.valueOf(this.secretInt));
	} else {
		logger.debug("Client secret int: " + String.valueOf(this.secretInt));
	}
	// Diffie-Hellman Key Exchange
	try {
		this.sendSecret();
	} catch (IOException ioe) {
		logger.error("Error! Could not send secret! ", ioe);
	}
	BigInteger receivedSecret = null;
	while (receivedSecret == null) {
		try {
			receivedSecret = this.receiveSecret();
		} catch (Exception e) {
			continue;
		}	
	}
	this.setKey(receivedSecret);

    }

    /**
     * The run method is intended for usage by the server (not client!). It allows each server communication
     * thread to monitor new messages, call the appropriate server methods to process requests, and send
     * reply messages to the client upon completing a put/get/delete operation.
     */
    // Server communication
    @Override
    public void run() {
        if (this.server != null) { // Only meant to run for the server comm module
            try {
                while (isOpen) {
                    KVMessage msg = receiveMsg();
                    if (msg.isAdminMessage()){
                        try {
                            System.out.println("received adminmsg");
                            KVAdminMsg in_msg = (KVAdminMsg) msg;
                            KVAdminMsg out_msg = adminServe(in_msg);
                            sendAdminMsg(null, out_msg.getStatus(), null, null);
                        } catch (Exception e){
                            logger.info("Server found an exception when receiving/processing/sending message! " + e);
                            isOpen = false;
                        }
                    } else {
                        try {
                            KVMsg in_msg = (KVMsg) msg;
                            KVMsg out_msg = serve(in_msg);
                            sendMsg(out_msg.getStatus(), out_msg.getKey(), out_msg.getValue(), out_msg.getMetadata());
                        } catch (Exception e) {
                            logger.info("Server found an exception when receiving/processing/sending message! " + e);
                            isOpen = false;
                        }
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (this.socket != null) {
                        this.input.close();
                        this.output.close();
                        this.socket.close();
                    }
                } catch (IOException ioe) {
                    logger.error("Error! Unable to tear down connection! ", ioe);
                }
            }
        }
    }

    /**
     * @return Message read in the objectInputStream
     * @throws IOException
     */
    @Override
    public KVMessage receiveMsg() throws IOException{
        KVMessage msg = null;

        try {
            msg = (KVMessage) this.input.readObject();
        } catch (Exception e) {
            logger.error("No message.");
        }

        if (msg != null){
            if (!msg.isAdminMessage()){
                KVMsg kvMsg = (KVMsg) msg;
                if (this.server != null) {
                    logger.info("Message received by server ->" + " Status: " + kvMsg.getStatus() + " Key: " + kvMsg.getKey() + " Value: " + kvMsg.getValue());
                } else {
                    logger.info("Message received by client ->" + " Status: " + kvMsg.getStatus() + " Key: " + kvMsg.getKey() + " Value: " + kvMsg.getValue());
                }
            }else {
                KVAdminMsg kvAdminMsg = (KVAdminMsg) msg;
                if (this.server != null){
                    logger.info("Message received by server ->" + " Status: " + kvAdminMsg.getStatus());
                } else {
                    logger.info("Message received by ECS ->" + " Status: " + kvAdminMsg.getStatus());
                }
            }
        }

        return msg;
    }

    /**
     * @param status status field
     * @param key key to be sent
     * @param value value to be sent. Can be left empty or null for messages that do not require a value
     * @throws IOException
     */
    @Override
    public void sendMsg(StatusType status, String key, String value, HashMap<String,String> metadata) throws IOException {
        KVMsg msg = new KVMsg(status, key, value, metadata);
        this.output.writeObject(msg);
        this.output.flush();
        if (this.server != null) {
            logger.info("Message sent by server ->" + " Status: " + msg.getStatus() + " Key: " + msg.getKey() + " Value: " + msg.getValue());
        } else {
            logger.info("Message sent by client ->" + " Status: " + msg.getStatus() + " Key: " + msg.getKey() + " Value: " + msg.getValue());
        }
    }

    /**
     * Send input message to the Server for processing (calls the appropriate server methods),
     * and build reply message.
     * @param msg Input message (from the client)
     * @return Output Message (from the server)
     * @throws Exception Exception
     */
    @Override
    public KVMsg serve(KVMsg msg) throws Exception {

        if (msg != null) {
	    logger.debug("In serve");

            KVMessage.StatusType status = msg.getStatus();
            String key = msg.getKey();
            String value = msg.getValue();

            KVMessage.StatusType out_status = null;
            String out_value = null;

            int key_len = key.getBytes(StandardCharsets.UTF_8).length;
            int val_len = key.getBytes(StandardCharsets.UTF_8).length;

	    logger.debug("Checking if stopped or write locked");

            if (this.server.isStopped()) { // SERVER_STOPPED
                out_status = SERVER_STOPPED;
                out_value = null;
            }  else { // Server is not stopped nor write-locked
                switch (status) {

                    case GET:

                        if (!this.server.inRange(key)) { // This server is not responsible for this key. Send updated metadata to KVStore
                            out_status = SERVER_NOT_RESPONSIBLE;
                            HashMap<String,String> out_metadata = this.server.getMetadata();
                            return new KVMsg(out_status, key, null, out_metadata);
                        } else {
                            out_value = this.server.getKV(key);

                            if (out_value != null) { // key currently in storage
                                out_status = GET_SUCCESS;
                            } else { // key not in storage
                                out_status = GET_ERROR;
                            }
                        }

                        break;

                    case PUT:
                        if (this.server.isWriteLock()) { // SERVER_WRITE_LOCK
                            out_status = SERVER_WRITE_LOCK;
                            out_value = null;
                        }else {
                            logger.debug("PUT request being served");
                            if (key_len < 1 || key_len > 20 || val_len > 122880) { // Size constraints are violated
                                out_status = PUT_ERROR;
                            } else if (!this.server.inRange(key)) { // This server is not responsible for this key. Send updated metadata to KVStore
                                logger.debug("Not in range");
                                out_status = SERVER_NOT_RESPONSIBLE;
                                HashMap<String,String> out_metadata = this.server.getMetadata();
                                return new KVMsg(out_status, key, null, out_metadata);
                            } else {
                                logger.debug("In range");
                                out_value = value;
                                boolean delete = (value == null || value.equals("null") || value.equals(""));
                                if (this.server.inStorage(key)) { // key currently in storage
                                    this.server.putKV(key, value);
                                    if (delete) { // Value is null. User wants to delete the key
                                        out_status = DELETE_SUCCESS;
                                    } else {
                                        out_status = PUT_UPDATE;
                                    }
                                } else { // key not in storage
                                    if (delete) { // Value is null. User wants to delete the key, but it is not present
                                        out_status = DELETE_ERROR;
                                    } else {
                                        this.server.putKV(key, value);
                                        out_status = PUT_SUCCESS;
                                    }
                                }
                            }
                        }

                        break;

                    default:
                        break;

                }
            }

            return new KVMsg(out_status, key, out_value);
        } else { // msg == null
            return null;
        }
    }

    public void sendAdminMsg(String kvServer, StatusType status, HashMap<String,String> metadata, String range) {
        // send admin msg to ECS or server
        KVAdminMsg adminMsg = new KVAdminMsg(kvServer, status, metadata, range);
        try {
            this.output.writeObject(adminMsg);
            this.output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (this.server != null){
            logger.info("Message sent by server -> " + "Status: " + adminMsg.getStatus());
        } else {
            logger.info("Message sent by ECS -> " + "Status: " + adminMsg.getStatus());
        }
    }

    /**
     * Serve the admin message send from ECS
     */
    public KVAdminMsg adminServe(KVAdminMsg adminMsg) throws Exception{
        if (adminMsg.getStatus() != null){
            KVMessage.StatusType status = adminMsg.getStatus();
            KVMessage.StatusType outStatus = null;
            switch (status){
                case INIT_SERVER:
                    if (adminMsg.getMetadata() == null){
                        outStatus = INIT_SERVER_FAILED;
                        break;
                    }
                    this.server.initKVServer(adminMsg.getMetadata(), adminMsg.getRange());
                    outStatus = INIT_SERVER_SUCCESS;
                    break;
                case START:
                    this.server.setStart();
                    outStatus = START_SUCCESS;
                    break;
                case STOP:
                    this.server.setStop();
                    outStatus = STOP_SUCCESS;
                    break;
                case SHUTDOWN:
                    this.server.shutDown();
                    outStatus = SHUTDOWN_SUCCESS;
                    break;
                case LOCK:
                    this.server.lockWrite();
                    outStatus = LOCK_SUCCESS;
                    break;
                case UNLOCK:
                    this.server.unLockWrite();
                    outStatus = UNLOCK_SUCCESS;
                    break;
                case MOVE_DATA:
                    logger.debug("Input data: ");
                    logger.debug(adminMsg.getRange());
                    logger.debug(adminMsg.getNewKvServer());
                    if (adminMsg.getRange() == null || adminMsg.getNewKvServer() == null){
                        outStatus = MOVE_DATA_FAILED;
                        break;
                    }
                    try{
                        this.server.moveData(adminMsg.getRange(), adminMsg.getNewKvServer());
                    } catch (Exception e){
                        logger.debug("Printing trace:");
                        logger.debug(e);
                        e.printStackTrace();
                        outStatus = MOVE_DATA_FAILED;
                        break;
                    }
                    outStatus = MOVE_DATA_SUCCESS;
                    break;
                case UPDATE:
                    if (adminMsg.getMetadata() == null){
                        outStatus = UPDATE_FAILED;
                        break;
                    }
                    this.server.update(adminMsg.getMetadata());
                    outStatus = UPDATE_SUCCESS;
                    break;
                case FLUSH:
                    try {
                        this.server.flushData();
                    }catch (Exception e) {
                        outStatus = FLUSH_FAILED;
                        break;
                    }
                    outStatus = FLUSH_SUCCESS;
                default:
                    break;
            }
            return new KVAdminMsg(null, outStatus, null, null);
        }
        return null;
    }

    /**
     * Close the communication module
     */
    @Override
    public void closeConnection() {
        this.isOpen = false;
        if (this.socket != null) {
            try {
                this.input.close();
                this.output.close();
                this.socket.close();
            } catch (IOException ioe) {
                logger.error("Error! Unable to tear down connection!", ioe);
            }
            logger.info("Connection closed!");
        }
    }

    @Override
    public BigInteger receiveSecret() throws Exception {
        BigInteger secret = null;

        try {
            secret = (BigInteger) this.input.readObject();
        } catch (Exception e) {
            logger.error("No secret.");
        }

        if (secret != null){
            if (this.server != null) {
                logger.debug("Secret received by server -> P = " + secret.toString());
            } else {
                logger.debug("Secret received by client -> Q = " + secret.toString());
            }
        }

        return secret;
    }

    @Override
    public void sendSecret() throws IOException {
        BigInteger V = fastModExp(new BigInteger(String.valueOf(this.g)), this.secretInt, this.p);

        this.output.writeObject(V);
        this.output.flush();

        if (this.server != null) {
        	logger.debug("Secret sent by server -> Q = " + V.toString());
        } else {
        	logger.debug("Secret sent by client -> P = " + V.toString());
        }
    }

    @Override
    public void setKey(BigInteger secret) {
        BigInteger sharedSecret = fastModExp(secret, this.secretInt, this.p); // S = Q^x mod(p) = P^y mod(p) = g^(xy) mod(p)

        // Get key SHA-256 hash value as an integer (key)
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        sha256.update(sharedSecret.toByteArray());
        byte[] digest = sha256.digest();

        this.key = ByteBuffer.wrap(Arrays.copyOfRange(digest, 0, 4)).getInt(); // Get lowest 4 bytes of hash: key
	logger.debug("Key is: " + String.valueOf(key));
    }

    private BigInteger fastModExp(BigInteger G, int x, BigInteger p) { // Compute g^x mod(p) fast using exp by squaring

        if (x == 0) {
            return BigInteger.ONE;
        }

        BigInteger Y = BigInteger.ONE;

        while (x > 1) {
            if (x%2 == 0) {
                G = (G.multiply(G)).mod(p);
                x = x/2;
            } else {
                Y = (G.multiply(Y)).mod(p);
                G = (G.multiply(G)).mod(p);
                x = (x-1)/2;
            }
        }

        return G.multiply(Y).mod(p);
    }

}
