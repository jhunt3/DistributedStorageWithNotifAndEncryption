package app_kvServer;
import javax.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;


public class publisher implements Runnable {

    Session session=null;
    MessageProducer producer=null;
    boolean running = true;
    public publisher(){

        try {
            ActiveMQConnectionFactory connectionFactory=new ActiveMQConnectionFactory("tcp://localhost:61616");
            Connection connection = connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Destination destination = session.createTopic("Changes");

            producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            String text = "test";



//            session.close();
//            connection.close();

        }catch(JMSException e){
            e.printStackTrace();
        }
    }

    public void pub(String msg){
        try {
            TextMessage message = session.createTextMessage(msg);

            producer.send(message);
        }catch(JMSException e){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        while(running){

        }


    }
}
