package com.example.mainprogram;

import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Coordinator {
    private final List<IAbstractDataFetcher> dataFetchers = new ArrayList<>();
    private final Logger logger = LogManager.getLogger(Coordinator.class);

    public void loadDataFetchersDynamically(){

        try{
            Properties properties = new Properties();
            properties.load(new FileInputStream
                    ("/Users/gokdenizbayik/IdeaProjects/MainProgram/src/main/resources/config.properties"));

            String[] classNames = properties.getProperty("fetcher.classes").split(",");

            for(String className : classNames){
                Class<?> classes = Class.forName(className);
                Constructor<?> constructor = classes.getConstructor();
                IAbstractDataFetcher abstractDataFetcher = (IAbstractDataFetcher) constructor.newInstance();
                dataFetchers.add(abstractDataFetcher);
            }
            System.out.println("Data fetchers loaded successfully");
        } catch (Exception e){
            logger.error("An error occurred while reading data fetchers: {}" , e.getMessage());
        }
    }

    public void addDataFetcher(IAbstractDataFetcher fetcher){
        dataFetchers.add(fetcher);
    }

    public void startFetching(){
        for(IAbstractDataFetcher fetcher : dataFetchers){
            fetcher.connect("Tcp" , "admin" , "12345");
            fetcher.subscribe("Tcp" , "USDTRY");
        }
    }

    public void stopFetching(){
        for(IAbstractDataFetcher fetcher : dataFetchers){
            fetcher.unSubscribe("Tcp" , "USDTRY");
            fetcher.disConnect("Tcp" , "admin" ,  "12345");
        }
    }
}
