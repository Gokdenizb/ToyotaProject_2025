package com.example.mainprogram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static ch.qos.logback.core.util.CloseUtil.closeQuietly;

/**
 * TCP sunucusundan metin tabanlı kur güncellemelerini alan ve
 * bu güncellemeleri {@link IAbstractFetcherCallBack} arayüzüne ileten sınıf.
 * <p>
 * 2025‑06‑01: Otomatik yeniden bağlanma / yeniden abone olma yeteneği eklendi.
 */
public class TcpDataFetcher implements IAbstractDataFetcher {

    /* === Sabit alanlar ve kurulum === */
    private String hostName = "localhost";
    private int portNumber  = 8081;

    private Socket          socket;
    private BufferedReader  input;
    private PrintWriter     output;

    private final Logger logger = LogManager.getLogger(TcpDataFetcher.class);

    private IAbstractFetcherCallBack callBack;
    private String                   platformName;

    /** Bu flag MainProgram kapatılırken set edilir. */
    private volatile boolean running;

    private Thread reconnectThread;

    /** Uzun sürmesini istemediğimiz çift bildirimleri engeller. */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** Daha sonra yeniden göndermek için saklanan tüm istenen kurlar. */
    private final Set<String> wantedRates = ConcurrentHashMap.newKeySet();

    /* === Yapıcılar === */

    public TcpDataFetcher() { /* varsayılan "localhost:8081"; alanlar zaten set */ }

    public TcpDataFetcher(String hostName, int portNumber) {
        this.hostName   = hostName;
        this.portNumber = portNumber;
    }

    /* === IAbstractDataFetcher arabirimi === */

    @Override
    public void connect(String platformName, String userId, String password) {
        this.platformName = platformName;
        if (running) {
            logger.warn("{} için zaten çalışıyor.", platformName);
            return;
        }
        running = true;
        reconnectThread = new Thread(this::reconnectLoop, "tcp-data-fetcher-" + platformName);
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    @Override
    public void disConnect(String platformName, String userId, String password) {
        running = false;
        closeQuietly(socket);
        if (reconnectThread != null) reconnectThread.interrupt();
        // son bağlantı durumunu bildir (yalnızca ilk kez)
        if (connected.compareAndSet(true, false) && callBack != null) {
            callBack.onDisConnect(platformName, true); // isFinal = true
        }
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        wantedRates.add(rateName);
        if (output != null) {
            output.println("subscribe|" + rateName);
            logger.info("{} için abonelik mesajı gönderildi.", rateName);
        }
    }

    @Override
    public void unSubscribe(String platformName, String rateName) {
        wantedRates.remove(rateName);
        if (output != null) {
            output.println("unsubscribe|" + rateName);
            logger.info("{} için abonelik iptali mesajı gönderildi.", rateName);
        }
    }

    @Override
    public void setCallBack(IAbstractFetcherCallBack fetcherCallBack) {
        this.callBack = fetcherCallBack;
    }

    @Override
    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public String getPlatformName() {
        return platformName;
    }

    /* === Özel yardımcılar === */

    /**
     * ana döngü: bağlantı kur, dinle, hata alırsan geri dön → yeniden dene.
     */
    private void reconnectLoop() {
        while (running) {
            try {
                openSocket();
                notifyConnectedOnce();
                resubscribeAll();
                listenForRates();                 // blok; hata aldığında fall‑through
            } catch (IOException io) {
                logger.warn("{} için bağlantı koptu: {}", platformName, io.getMessage());
            } finally {
                closeQuietly(socket);
                notifyDisconnectedOnce(false);
            }
            // kapatılmadıysa bekle & tekrar dene
            sleepQuietly(3000);
        }
        logger.info("{} fetcher stopped.", platformName);
    }

    private void openSocket() throws IOException {
        socket = new Socket(hostName, portNumber);
        input  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        logger.info("TCP sunucusuna bağlanıldı: {}", platformName);
    }

    private void notifyConnectedOnce() {
        if (connected.compareAndSet(false, true) && callBack != null) {
            callBack.onConnect(platformName, true);
        }
    }

    private void notifyDisconnectedOnce(boolean isFinal) {
        if (connected.compareAndSet(true, false) && callBack != null) {
            callBack.onDisConnect(platformName, isFinal);
        }
    }

    private void resubscribeAll() {
        wantedRates.forEach(r -> output.println("subscribe|" + r));
    }

    private void listenForRates() throws IOException {
        String receivedMessage;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        while (running && (receivedMessage = input.readLine()) != null) {
            logger.info("Gelen mesaj: {}", receivedMessage);
            if (!receivedMessage.startsWith("Updated rate:")) continue;

            String[] parts = receivedMessage.split("\\|");
            String fullName = parts[0].substring("Updated rate: ".length());
            String rateName = fullName.substring(fullName.indexOf('_') + 1);

            BigDecimal bid = new BigDecimal(parts[1].split(":" )[2]);
            BigDecimal ask = new BigDecimal(parts[2].split(":" )[2]);
            String tsRaw   = receivedMessage.substring(receivedMessage.indexOf("timestamp:") + "timestamp:".length()).trim();
            LocalDateTime ts = LocalDateTime.parse(tsRaw, dtf);

            if (callBack != null) {
                callBack.onRateUpdate(platformName, rateName, new RateFields(bid, ask, ts));
            }
        }
        // null döndüyse veya running=false olduysa IOException fırlatma → reconnect
        throw new IOException("Sunucu akışı kapandı");
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { /* çık */ }
    }
}
