package com.example.mainprogram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hazelcast.security.SecurityInterceptorConstants.GET;

public class RestDataFetcher implements IAbstractDataFetcher{
    private String Url;
    private int pollIntervalsMs;
    private final Logger logger = LogManager.getLogger(RestDataFetcher.class);
    private ScheduledExecutorService scheduler;

    public RestDataFetcher(){
        this("http://localhost:8082", 5000);
    }

    public RestDataFetcher(String Url , int pollIntervalsMs){
        this.Url = Url;
        this.pollIntervalsMs = pollIntervalsMs;
    }

    @Override
    public void connect(String platformName , String userId , String userPassword){
        logger.info("REST fetcher ready to poll {}", Url);
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void subscribe(String platformName , String rateName){
        scheduler.scheduleAtFixedRate(() -> {
            try{
                String url = String.format("%s/api/rates/%s", Url , rateName);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

                conn.setRequestMethod("GET");

                try(BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()))){
                    String body = input.lines().collect(Collectors.joining("\n"));
                    logger.info("Received exchange rate update {}" , body);
                }

            } catch (Exception e){
                logger.error("Error while connecting to url {}" , e.getMessage());
            }

        },0 , pollIntervalsMs , TimeUnit.MILLISECONDS);

        logger.info("Subscribed to {} via REST" , rateName);
    }

    @Override
    public void unSubscribe(String platformName , String rateName){
        if(scheduler != null){
            scheduler.shutdownNow();
            logger.info("Unsubscribed from {} rate via REST", rateName);
        }

    }

    @Override
    public void disConnect(String platformName , String userId , String userPassword){
        //------------------------------------
        logger.info("Disconnection successful");
    }

}
