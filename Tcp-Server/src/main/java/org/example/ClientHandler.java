package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
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
    private String lastRateData = null;
    private Double lastBid = null;
    private Double lastAsk = null;
    private final double Threshold = 0.01;
    private final DateTimeFormatter dateTimeFormatter
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


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
            output.println(" Bağlandınız! Abonelik için: subscribe|PF1_USDTRY , EURUSD veya GBPUSD");

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

            try{

            if(subscribedRate == null) {return;}

            String candidate = CurrencyDataGenerator.generateRate(subscribedRate);

            double newBid = parseValue(candidate , 1);
            double newAsk = parseValue(candidate , 2);


            if(lastBid != null){
                double diffBid = Math.abs(newBid - lastBid) / lastBid;
                double diffAsk = Math.abs(newAsk - lastAsk) / lastAsk;

                if(diffBid > Threshold || diffAsk > Threshold){
                    output.println("Updated rate: " + lastRateData);
                    return;
                }
            }

            lastBid = newBid;
            lastAsk = newAsk;
            lastRateData = candidate;
            output.println("Updated rate: " + candidate);
        } catch (Exception e){
                logger.error("Error in rate scheduler for {}: {}", subscribedRate, e.getMessage(), e);
            }
        }, 0 , updateFrequency , TimeUnit.MILLISECONDS);
    }

    private double parseValue(String rateData , int partIndex){

        String[] parts = rateData.split("\\|");
        return Double.parseDouble(parts[partIndex].split(":")[2]);
    }
}
