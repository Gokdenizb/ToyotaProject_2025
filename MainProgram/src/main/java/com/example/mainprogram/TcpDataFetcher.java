package com.example.mainprogram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TCP sunucusundan metin tabanlı kur güncellemelerini alan ve
 * bu güncellemeleri {@link IAbstractFetcherCallBack} arayüzüne ileten sınıf.
 */
public class TcpDataFetcher implements IAbstractDataFetcher {
    private String hostName;
    private int portNumber;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private final Logger logger = LogManager.getLogger(TcpDataFetcher.class);
    private IAbstractFetcherCallBack callBack;
    private String platformName;

    /**
     * Varsayılan olarak "localhost:8081" adresine bağlanan yapıcı.
     */
    public TcpDataFetcher() {
        this.hostName = "localhost";
        this.portNumber = 8081;
    }

    /**
     * Belirtilen sunucu adresi ve port numarası ile bağlantı kurmak için kullanılan yapıcı.
     *
     * @param hostName   sunucu ana bilgisayar adı veya IP adresi
     * @param portNumber sunucu port numarası
     */
    public TcpDataFetcher(String hostName, int portNumber) {
        this.hostName = hostName;
        this.portNumber = portNumber;
    }

    /**
     * Sunucuya TCP bağlantısı oluşturur, giriş/çıkış akışlarını başlatır ve
     * gelen mesajları dinlemek için ayrı bir iş parçacığı başlatır.
     *
     * @param platformName dinleyiciye iletilecek platform adı (örn. "PF1")
     * @param userId       kullanıcı adı (ileride kimlik doğrulama için saklanabilir)
     * @param password     şifre (ileride kimlik doğrulama için saklanabilir)
     */
    @Override
    public void connect(String platformName, String userId, String password) {
        this.platformName = platformName;
        try {
            socket = new Socket(hostName, portNumber);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            logger.info("TCP sunucusuna bağlanıldı: {}", platformName);
            new Thread(this::listenForRates).start();
        } catch (IOException e) {
            logger.error("TCP bağlantısı kurulamadı ({}): {}", platformName, e.getMessage());
        }
    }

    /**
     * Açık olan TCP bağlantısını kapatır ve kaynakları serbest bırakır.
     *
     * @param platformName bağlantısı kesilen platform adı
     * @param userId       kullanıcı adı
     * @param password     şifre
     */
    @Override
    public void disConnect(String platformName, String userId, String password) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("{} platformundan bağlantı kesildi.", platformName);
            }
        } catch (IOException e) {
            logger.error("Bağlantı kesilirken hata ({}): {}", platformName, e.getMessage());
        }
    }

    /**
     * Sunucuya "subscribe|<rateName>" mesajı göndererek belirli bir kur için
     * güncelleme aboneliği başlatır.
     *
     * @param platformName platform adı
     * @param rateName     abone olunacak kur adı (örn. "USDTRY")
     */
    @Override
    public void subscribe(String platformName, String rateName) {
        try {
            output.println("subscribe|" + rateName);
            logger.info("{} için abonelik mesajı gönderildi.", rateName);
        } catch (Exception e) {
            logger.error("Abonelik mesajı gönderilemedi ({}): {}", rateName, e.getMessage());
        }
    }

    /**
     * Sunucuya aboneliği iptal etmek için "unsubscribe|<rateName>" mesajı gönderir
     * ve varsa planlanmış dinleme görevlerini durdurur.
     *
     * @param platformName platform adı
     * @param rateName     abonelik iptali yapılacak kur adı
     */
    @Override
    public void unSubscribe(String platformName, String rateName) {
        try {
            if (output != null) {
                output.println("unsubscribe|" + rateName);
                logger.info("{} için abonelik iptali mesajı gönderildi.", rateName);
            }
        } catch (Exception e) {
            logger.error("Abonelik iptali mesajı gönderilemedi ({}): {}", rateName, e.getMessage());
        }
    }

    /**
     * Gelen kur güncelleme olaylarını iletecek callback işleyicisini ayarlar.
     *
     * @param fetcherCallBack kur güncellemesi alındığında çağrılacak callback
     */
    @Override
    public void setCallBack(IAbstractFetcherCallBack fetcherCallBack) {
        this.callBack = fetcherCallBack;
    }

    /**
     * Bu fetcher için platform adını ayarlar.
     *
     * @param platformName platform adı
     */
    @Override
    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    /**
     * Sunucudan gelen satır satır metin verilerini okur, güncelleme formatındaysa ayrıştırır
     * ve {@link IAbstractFetcherCallBack#onRateUpdate(String, String, RateFields)} çağrısını yapar.
     */
    private void listenForRates() {
        String receivedMessage;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try {
            while ((receivedMessage = input.readLine()) != null) {
                logger.info("Gelen mesaj: {}", receivedMessage);

                if (!receivedMessage.startsWith("Updated rate:")) {
                    continue;
                }

                String[] parts = receivedMessage.split("\\|");
                String fullName = parts[0].substring("Updated rate: ".length());
                String rateName = fullName.substring(fullName.indexOf('_') + 1);

                BigDecimal bid = new BigDecimal(parts[1].split(":")[2]);
                BigDecimal ask = new BigDecimal(parts[2].split(":")[2]);
                String tsRaw = receivedMessage.substring(receivedMessage.indexOf("timestamp:") + "timestamp:".length()).trim();
                LocalDateTime ts = LocalDateTime.parse(tsRaw, dtf);

                if (callBack != null) {
                    callBack.onRateUpdate(platformName, rateName, new RateFields(bid, ask, ts));
                }
            }
        } catch (IOException e) {
            logger.error("Güncelleme dinlenirken hata: {}", e.getMessage());
        }
    }
}
