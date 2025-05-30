package org.example;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    public static void main(String[] args) {

        final Logger logger = LogManager.getLogger(Main.class);


        try{
            ConfigLoader configLoader = ConfigLoader.loadFromResource("config.json");

            CurrencyDataGenerator.configure(configLoader);

            new TcpServer(8081 , configLoader).startServer();

        } catch (Exception e){

            logger.error("An error occurred: {}", e.getMessage());
        }
    }
}