package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {
    private final int port;
    private final ConfigLoader configLoader;
    private final ExecutorService threadPool;
    private final Logger logger = LogManager.getLogger(TcpServer.class);


    public TcpServer(int port , ConfigLoader configLoader){
        this.port = port;
        this.configLoader = configLoader;
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public void startServer(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("✅ TCP Server başlatıldı, port: " + port);
            while(true){
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket , configLoader.getStreamFrequency()));
            }
        } catch (Exception e) {
            logger.error("An error occurred: {}" , e.getMessage());
        }
    }
}
