package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ClientHandler implements Runnable{
    private final Socket clientSocket;
    private final int updateFrequency;
    private String subscribedRate = null;
    private PrintWriter output;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Logger logger = LogManager.getLogger(ClientHandler.class);


    public ClientHandler(Socket clientSocket , int updateFrequency){
        this.clientSocket = clientSocket;
        this.updateFrequency = updateFrequency;
    }

    @Override
    public void run(){
        try(
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter output = new PrintWriter(clientSocket.getOutputStream() , true)
        )
        {
            this.output = output;
            output.println(" Bağlandınız! Abonelik için: subscribe|PF1_USDTRY");

            String clientMessage;
            while((clientMessage = input.readLine()) != null){
                if(clientMessage.startsWith("subscribe|")){
                    subscribedRate = clientMessage.split("\\|")[1];
                    output.println("✅ " + subscribedRate + " kuruna abone oldunuz.");
                    startSendingRates();
                }
            }
        } catch (Exception e){
            logger.error("An error occurred: {}" , e.getMessage());
        }
    }

    public void startSendingRates(){
        scheduler.scheduleAtFixedRate(() -> {
            if(subscribedRate != null){
                String rateData = CurrencyDataGenerator.generateRateUSDTRY(subscribedRate);
                output.println("Updated rate " + rateData);
            }

        }, 0 , updateFrequency , TimeUnit.MILLISECONDS);
    }
}
