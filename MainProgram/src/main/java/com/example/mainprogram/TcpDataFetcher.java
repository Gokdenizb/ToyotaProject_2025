package com.example.mainprogram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;


public class TcpDataFetcher implements IAbstractDataFetcher {

    private String hostName;
    private int portNumber;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private final Logger logger = LogManager.getLogger(TcpDataFetcher.class);


    public TcpDataFetcher(){
        this.hostName = "localhost";
        this.portNumber = 8081;
    }

    public TcpDataFetcher(String hostName , int portNumber){
        this.hostName = hostName;
        this.portNumber = portNumber;
    }

    @Override
    public void connect(String platformName, String userId, String password) {

        try{
                socket = new Socket(hostName , portNumber);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()) , true);

                logger.info("Connected to Tcp server: {}" , platformName);

                new Thread(this::listenForRates).start();

        } catch (Exception e) {
            logger.error("Couldn't connect to server: {} ", e.getMessage());
        }
    }

    @Override
    public void disConnect(String platformName, String userId, String password) {
        try{
                if(socket != null){
                    socket.close();
                    logger.info("You successfully disconnected from the server: {}" , platformName);
                }

        } catch (IOException e) {
            logger.error("Disconnection failed: {}" , e.getMessage());
        }
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        try{
            output.println("subscribe|" + rateName);
            logger.info("You have subscribed to {} currency" , rateName);


        } catch (Exception e) {
            logger.error("Error while subscribing: {}" , e.getMessage());
        }
    }

    @Override
    public void unSubscribe(String platformName, String rateName) {
        
    }

    private void listenForRates() {
        String receivedMessage;
        try {
            while ((receivedMessage = input.readLine()) != null) {
                logger.info("Received exchange rate update: {}" , receivedMessage);
            }
        } catch (IOException e){
            logger.error("Error while receiving updated exchange rate: {}" , e.getMessage());
        }
    }

}
