package com.example.mainprogram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class MainProgramApplication {

    public static void main(String[] args) throws Exception {
        var context = SpringApplication.run(MainProgramApplication.class, args);

        Logger logger = LogManager.getLogger(MainProgramApplication.class);

        Coordinator coord = context.getBean(Coordinator.class);
        coord.addCalculatedListener(rate -> System.out.println());
        coord.init();

        Thread.sleep(20000);
    }
}

