package com.example.mainprogram.DataFetchers;

import com.example.mainprogram.IAbstractDataFetcher;
import com.example.mainprogram.IAbstractFetcherCallBack;
import com.example.mainprogram.Rate.RateFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * REST API üzerinden belirli bir kuru periyodik olarak çeken fetcher sınıfı.
 * TCPDataFetcher ile aynı davranışı gösterecek biçimde yeniden düzenlenmiştir.
 * {@link IAbstractDataFetcher} arayüzünü implemente eder.
 */
public class RestDataFetcher implements IAbstractDataFetcher {
    private String Url;
    private int pollIntervalsMs;
    private final Logger logger = LogManager.getLogger(RestDataFetcher.class);

    /** Aktif görevleri ve istenen kurların listesini tutar */
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<String> wantedRates = ConcurrentHashMap.newKeySet();

    private IAbstractFetcherCallBack callBack;
    private String platformName;

    /** Çalışır durumda olup olmadığını belirten bayrak */
    private volatile boolean running;
    /** onDisConnect tekrar tekrar tetiklenmesin diye kontrol bayrağı */
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);

    /**
     * Varsayılan URL ve zaman aralığıyla RestDataFetcher oluşturur.
     */
    public RestDataFetcher() {
        this("http://localhost:8082", 5000);
    }

    /**
     * Belirtilen temel URL ve sorgu aralığıyla RestDataFetcher oluşturur.
     *
     * @param Url             HTTP isteklerinin yapılacağı temel URL
     * @param pollIntervalsMs İstekler arasındaki gecikme (milisaniye)
     */
    public RestDataFetcher(String Url, int pollIntervalsMs) {
        this.Url = Url.endsWith("/") ? Url.substring(0, Url.length() - 1) : Url;
        this.pollIntervalsMs = pollIntervalsMs;
    }

    /* ------------------------------------------------- */
    /* IAbstractDataFetcher arayüz metotları             */
    /* ------------------------------------------------- */

    /**
     * REST fetcher için gerekli planlayıcıyı başlatır ve callback'i bilgilendirir.
     *
     * @param platformName  veri kaynağı platform adı (örn. "PF2")
     * @param userId        kullanıcı adı (ileride kullanılmak üzere saklanabilir)
     * @param userPassword  şifre (ileride kullanılmak üzere saklanabilir)
     */
    @Override
    public void connect(String platformName, String userId, String userPassword) {
        this.platformName = platformName;
        try {
            scheduler = Executors.newSingleThreadScheduledExecutor(r ->
                    new Thread(r, "rest-data-fetcher-" + platformName));
            running = true;
            logger.info("REST fetcher polling başlatıldı: {}", Url);

            // Önceden istenmiş kurları planla
            wantedRates.forEach(this::scheduleRate);

            if (callBack != null) {
                callBack.onConnect(platformName, true);
            }
        } catch (Exception e) {
            logger.error("REST fetcher başlatılamadı ({}): {}", platformName, e.getMessage());
            running = false;
            if (callBack != null) {
                callBack.onConnect(platformName, false);
            }
        }
    }

    /**
     * Belirtilen kur için periyodik GET isteği yaparak güncellemeleri çeker.
     *
     * @param platformName platform adı
     * @param rateName     çekilecek kur adı (örn. "USDTRY")
     */
    @Override
    public void subscribe(String platformName, String rateName) {
        wantedRates.add(rateName);
        if (scheduler != null && running) {
            scheduleRate(rateName);
        }
        logger.info("{} için REST aboneliği başlatıldı.", rateName);
    }

    /**
     * Planlanan polling görevini durdurarak abonelikten çıkar.
     *
     * @param platformName platform adı
     * @param rateName     abonelikten çıkarılacak kur adı
     */
    @Override
    public void unSubscribe(String platformName, String rateName) {
        wantedRates.remove(rateName);
        ScheduledFuture<?> future = scheduledTasks.remove(rateName);
        if (future != null) {
            future.cancel(true);
            logger.info("{} için REST aboneliği durduruldu.", rateName);
        }
    }

    /**
     * Gelen kur güncelleme callback işleyicisini ayarlar.
     *
     * @param callBack Güncellemeleri alacak callback
     */
    @Override
    public void setCallBack(IAbstractFetcherCallBack callBack) {
        this.callBack = callBack;
    }

    @Override
    public void setPlatformName(String platformName) {

    }

    /**
     * Bağlantıyı ve tüm planlanmış görevleri durdurur.
     *
     * @param platformName platform adı
     * @param userId       kullanıcı adı (ileride kullanılabilir)
     * @param userPassword şifre (ileride kullanılabilir)
     */
    @Override
    public void disConnect(String platformName, String userId, String userPassword) {
        running = false;
        disconnectNotified.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        scheduledTasks.clear();
        logger.info("REST fetcher bağlantısı sonlandırıldı: {}", platformName);
    }

    public String getPlatformName() {
        return platformName;
    }

    /* ------------------------------------------------- */
    /* İç yardımcı metotlar                              */
    /* ------------------------------------------------- */

    /**
     * Bir kur için periyodik çekme görevi planlar.
     */
    private void scheduleRate(String rateName) {
        // Aynı isimli görev zaten varsa tekrar ekleme
        if (scheduledTasks.containsKey(rateName)) {
            return;
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (!running) {
                return;
            }
            try {
                String url = String.format("%s/api/rates/%s", Url, rateName);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");

                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {

                    String body = input.lines().collect(Collectors.joining("\n"));
                    logger.info("Güncellenen kur verisi alındı: {}", body);

                    JsonNode node = new ObjectMapper().readTree(body);
                    String rn = node.get("rateName").asText();
                    BigDecimal bid = new BigDecimal(node.get("bid").asText());
                    BigDecimal ask = new BigDecimal(node.get("ask").asText());
                    LocalDateTime ts = LocalDateTime.parse(node.get("timeStamp").asText());

                    if (callBack != null) {
                        logger.info("Calling onRateUpdate from REST: {} → BID={} ASK={}", rn, bid, ask);
                        callBack.onRateUpdate(platformName, rn, new RateFields(bid, ask, ts));
                    }
                }
            } catch (Exception e) {
                logger.error("URL {} ile bağlantı sırasında hata: {}", Url, e.getMessage());

                // Hata devam ederse tek seferlik disconnect bildir.
                if (disconnectNotified.compareAndSet(false, true) && callBack != null) {
                    callBack.onDisConnect(platformName, false);
                }
            }
        }, 0, pollIntervalsMs, TimeUnit.MILLISECONDS);

        scheduledTasks.put(rateName, future);
    }
}
