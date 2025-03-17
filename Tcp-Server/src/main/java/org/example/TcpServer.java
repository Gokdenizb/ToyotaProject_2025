package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {
    private final int port;

    private final ExecutorService threadPool;

    public TcpServer(int port){
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public void startServer(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("✅ TCP Server başlatıldı, port: " + port);
            while(true){
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
