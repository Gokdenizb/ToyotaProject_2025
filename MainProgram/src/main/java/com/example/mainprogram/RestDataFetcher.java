package com.example.mainprogram;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST API üzerinden belirli bir kuru periyodik olarak çeken fetcher sınıfı.
 * {@link IAbstractDataFetcher} arayüzünü implemente eder.
 */
public class RestDataFetcher implements IAbstractDataFetcher {
    private String Url;
    private int pollIntervalsMs;
    private final Logger logger = LogManager.getLogger(RestDataFetcher.class);
    private ScheduledExecutorService scheduler;
    private IAbstractFetcherCallBack callBack;
    private String platformName;

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
        this.Url = Url;
        this.pollIntervalsMs = pollIntervalsMs;
    }

    /**
     * REST fetcher için gerekli planlayıcıyı başlatır.
     *
     * @param platformName  veri kaynağı platform adı (örn. "PF2")
     * @param userId        kullanıcı adı (ileride kullanılmak üzere saklanabilir)
     * @param userPassword  şifre (ileride kullanılmak üzere saklanabilir)
     */
    @Override
    public void connect(String platformName, String userId, String userPassword) {

        logger.info("REST fetcher polling başlatıldı: {}", Url);
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Belirtilen kur için periyodik GET isteği yaparak güncellemeleri çeker.
     *
     * @param platformName platform adı
     * @param rateName     çekilecek kur adı (örn. "USDTRY")
     */
    @Override
    public void subscribe(String platformName, String rateName) {
        this.platformName = platformName;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String url = String.format("%s/api/rates/%s", Url, rateName);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");

                try (BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
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
            }

        }, 0, pollIntervalsMs, TimeUnit.MILLISECONDS);

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
        if (scheduler != null) {
            scheduler.shutdownNow();
            logger.info("{} için REST aboneliği durduruldu.", rateName);
        }
    }

    /**
     * Gelen kur güncelleme callback işleyicisini ayarlar.
     *
     * @param fetcherCallBack güncelleme alındığında çağrılacak callback
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
     * REST fetcher bağlantısını sonlandırır.
     *
     * @param platformName platform adı
     * @param userId       kullanıcı adı (ileride kullanılabilir)
     * @param userPassword şifre (ileride kullanılabilir)
     */
    @Override
    public void disConnect(String platformName, String userId, String userPassword) {
        logger.info("REST fetcher bağlantısı sonlandırıldı: {}", platformName);
    }

    public String getPlatformName(){
        return platformName;
    }
}
