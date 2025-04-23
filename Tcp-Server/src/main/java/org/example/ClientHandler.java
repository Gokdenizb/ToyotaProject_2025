package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Her bir istemci bağlantısı için işlem akışını yöneten sınıf.
 * <p>
 * TCP soketi üzerinden gelen abonelik isteklerini dinler,
 * belirlenen frekansta güncellenen döviz kurlarını gönderir.
 * </p>
 */
public class ClientHandler implements Runnable {
    /** İstemci soketi referansı */
    private final Socket clientSocket;

    /** Kur güncelleme yayım periyodu (ms cinsinden) */
    private final int updateFrequency;

    /** İstemciye veri yazmak için kullanılan PrintWriter */
    private PrintWriter output;

    /** Loglama için Logger örneği */
    private final Logger logger = LogManager.getLogger(ClientHandler.class);

    /**
     * Sabit değer: maksimum kabul edilebilir yüzde fark (örn. 0.01 => %%1)
     */
    private static final double THRESHOLD = 0.01;

    /**
     * Zaman damgası formatı: yyyy-MM-dd HH:mm:ss
     */
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Güncelleme görevlerini planlamak için kullanılan scheduler.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * İstemci soketini ve güncelleme frekansını belirterek handler oluşturur.
     *
     * @param clientSocket    Bağlanan istemcinin soketi
     * @param updateFrequency Kur güncelleme sıklığı (ms)
     */
    public ClientHandler(Socket clientSocket, int updateFrequency) {
        this.clientSocket = clientSocket;
        this.updateFrequency = updateFrequency;
    }

    /**
     * Handler başlatıldığında çağrılır: bağlantıyı dinler ve abonelikleri yönetir.
     */
    @Override
    public void run() {
        try (
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        clientSocket.getOutputStream(), true)
        ) {
            this.output = out;
            // Bağlantı mesajı ve abonelik talimatı
            out.println("Bağlandınız! Abonelik için: subscribe|PF1_USDTRY , PF1_EURUSD veya PF1_GBPUSD");

            String line;
            // İstemci mesajlarını okuma döngüsü
            while ((line = input.readLine()) != null) {
                if (line.startsWith("subscribe|")) {
                    String rate = line.split("\\|", 2)[1];
                    out.println("✅ " + rate + " kuruna abone oldunuz.");
                    scheduleRatePublisher(rate);
                }
            }
        } catch (IOException e) {
            logger.error("Bir hata oluştu: {}", e.getMessage(), e);
        }
    }

    /**
     * Belirtilen kur için düzenli aralıklarla güncelleme mesajı planlar.
     * <p>
     * Son gönderilen bid/ask değerlerini tutarak, eğer yüzde fark
     * eşik değerinden küçükse mesajı atlar.
     * </p>
     *
     * @param rateName Abone olunan kur adı (örn. "PF1_USDTRY")
     */
    private void scheduleRatePublisher(String rateName) {
        // Son yayınlanan ham veri, bid ve ask değerlerini saklar
        AtomicReference<String> lastRateData = new AtomicReference<>(null);
        AtomicReference<Double> lastBid = new AtomicReference<>(null);
        AtomicReference<Double> lastAsk = new AtomicReference<>(null);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Yeni kur bilgisini üret
                String candidate = CurrencyDataGenerator.generateRate(rateName);
                String[] parts = candidate.split("\\|");
                double newBid = Double.parseDouble(parts[1].split(":")[2]);
                double newAsk = Double.parseDouble(parts[2].split(":")[2]);

                Double prevBid = lastBid.get();
                Double prevAsk = lastAsk.get();
                // Eğer daha önce değer varsa, yüzde farkları kontrol et
                if (prevBid != null) {
                    double diffBid = Math.abs(newBid - prevBid) / prevBid;
                    double diffAsk = Math.abs(newAsk - prevAsk) / prevAsk;
                    if (diffBid <= THRESHOLD && diffAsk <= THRESHOLD) {
                        return; // eşiğin altındaki değişimleri atla
                    }
                }

                // Yeni değerleri kaydet
                lastBid.set(newBid);
                lastAsk.set(newAsk);
                lastRateData.set(candidate);

                // İstemciye gönder
                output.println("Updated rate: " + candidate);

            } catch (Exception e) {
                logger.error("Error in rate scheduler for {}: {}", rateName, e.getMessage(), e);
            }
        }, 0, updateFrequency, TimeUnit.MILLISECONDS);
    }
}
