package com.example.rest_api.Service;

import java.io.*;
import java.net.Socket;
import org.springframework.stereotype.Service;

@Service
public class TcpClientService {

    private final String Host_Name = "localhost";
    private final int Port = 8081;


    public String getExchangeRates(String currencyPair){
        try(
                Socket socket = new Socket(Host_Name,Port);
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter output = new PrintWriter(socket.getOutputStream() , true)
        ) {
            output.println("subscribe|" + currencyPair);
            return input.readLine(); 
        } catch (IOException e){
            e.printStackTrace();
            return "TCP sunucuya bağlanılamadı";
        }

    }
}
