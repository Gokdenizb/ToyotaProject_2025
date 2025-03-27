package com.example.mainprogram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;




@SpringBootApplication
public class MainProgramApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainProgramApplication.class, args);

        Logger logger = LogManager.getLogger(MainProgramApplication.class);

        Coordinator coordinator = new Coordinator();

        coordinator.addDataFetcher(new TcpDataFetcher("localhost" , 8081));

        coordinator.loadDataFetchersDynamically();

        coordinator.startFetching();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            logger.error("Error while communicating with the server: {}" , e.getMessage());
        }

        coordinator.stopFetching();
    }
}
