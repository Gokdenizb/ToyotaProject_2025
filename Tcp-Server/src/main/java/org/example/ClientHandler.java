package org.example;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ClientHandler implements Runnable{
    private final Socket clientSocket;
    private String subscribedRate = null;
    private PrintWriter output;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ClientHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    @Override
    public void run(){
        try(
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter output = new PrintWriter(clientSocket.getOutputStream() , true)

        )
        {

            this.output = output;
            output.println(" Bağlandınız! Abonelik için: subscribe|USDTRY");


            String clientMessage;
            while((clientMessage = input.readLine()) != null){
                if(clientMessage.startsWith("subscribe|")){
                    subscribedRate = clientMessage.split("\\|")[1];
                    output.println("✅ " + subscribedRate + " kuruna abone oldunuz.");
                    startSendingRates();
                }
            }
        } catch (IOException e){
            System.out.println("istemci ile bağlantı kesildi");
        }
    }

    public void startSendingRates(){
        scheduler.scheduleAtFixedRate(() -> {
            if(subscribedRate != null){
                String rateData = CurrencyDataGenerator.generateRate(subscribedRate);
                output.println("Updated rate " + rateData);
            }

        }, 0 , 5 , TimeUnit.SECONDS);
    }
}
