package app_kvClient;
import javax.jms.*;
import org.apache.log4j.Logger;
import org.apache.activemq.ActiveMQConnectionFactory;
public class subscriber implements Runnable{
    private Logger logger = Logger.getRootLogger();
    ActiveMQConnectionFactory connectionFactory = null;
    MessageConsumer consumer;
    Connection connection;
    Session session;
    boolean running = true;
//    public subscriber(){
//        try {
//
//
//
//
//
//
//
//
//
//
//        }catch(JMSException e){
//            e.printStackTrace();
//        }
//    }

    public void run(){

        try {
            //logger.error("Starting sub");
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
            Connection connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Destination destination = session.createTopic("Changes");

            MessageConsumer consumer = session.createConsumer(destination);
            while(running) {
                Message message = consumer.receive();
                TextMessage textmessage = (TextMessage) message;

                System.out.println(textmessage.getText());
            }

            session.close();
            connection.close();
        }catch(JMSException e){
            e.printStackTrace();
        }



    }
}
