package com.test.exercise.kafka.tutorial1;


import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {

    private ConsumerDemoWithThread(){

    }

    public static void main(String[] args) {
        new ConsumerDemoWithThread().run();
    }

    private void run(){
        final Logger logger= LoggerFactory.getLogger(ConsumerDemoWithThread.class);

        String bootstrapServer = "127.0.0.1:9092";
        String groupId = "fourth-consumer-group";
        String topic = "first_topic";

        //latch for dealing with multiple threads
        CountDownLatch latch = new CountDownLatch(1);

        logger.info("Creating the consumer thread");
        Runnable myConsumerThread = new ConsumerThread(bootstrapServer,groupId,topic, latch);

        Thread myThread = new Thread(myConsumerThread);
        myThread.start();

        //add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Caught shutdown hook");
            ((ConsumerThread) myConsumerThread).shutdown();

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Application has exited.");

        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Application got interrupted ", e);
        } finally {
            logger.info("Application is closing.");
        }

    }

    public class ConsumerThread implements Runnable{

        private CountDownLatch latch;
        private KafkaConsumer<String,String>  consumer;
        final Logger logger= LoggerFactory.getLogger(ConsumerThread.class);

        public ConsumerThread(String bootstrapServer, String groupId, String topic, CountDownLatch latch){
            this.latch=latch;
            Properties properties=new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServer);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG,groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            //create consumer
            consumer = new KafkaConsumer<String, String>(properties);
            //subscribe consumer to our topic(s)
            consumer.subscribe(Arrays.asList(topic));
        }


        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Key: " + record.key() + ", Value: " + record.value());
                        logger.info("Partition: " + record.partition() + ", Offset: " + record.offset());
                    }
                }
            }
            catch(WakeupException ex){
                logger.info("Received shutdown signal!");
            }
            finally {
                consumer.close();
                //tell our main code we're done with the consumer
                latch.countDown();
            }
        }


        public void shutdown(){
            //special method to interrupt consumer.poll()
            //it will throw the exception - WakeUpException
            consumer.wakeup();
        }
    }

}
