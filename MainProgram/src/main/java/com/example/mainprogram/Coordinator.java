package com.example.mainprogram;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Farklı platformlardan (ör. PF1, PF2) gelen ham kur güncellemelerini toplayan,
 * bunları ortalayan ve hesaplanan (türetilmiş) kurları
 * kaydeden ve {@link CalculatedRateListener} kayıtlı dinleyicilerine ileten koordinatör sınıfı.
 */
public class Coordinator implements IAbstractFetcherCallBack {
    private final List<IAbstractDataFetcher> dataFetchers = new ArrayList<>();
    private final Logger logger = LogManager.getLogger(Coordinator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> missingWarnings = ConcurrentHashMap.newKeySet();

    /**
     * Koordinatörü başlatır: yapılandırılan veri alıcılarını (fetcher) dinamik olarak yükler ve bağlanır.
     *
     * @throws Exception fetcher sınıfları yüklenemez veya yapılandırılamazsa fırlatılır
     */
    public void init() throws Exception {
        loadDataFetchersDynamically();
    }

    private final Map<String, Map<String, RateFields>> rawBySource = new ConcurrentHashMap<>();
    private final List<CalculatedRateListener> calculatedRateListeners = new CopyOnWriteArrayList<>();

    /**
     * Hesaplanan kurların dinleyicisini ekler.
     *
     * @param listener hesaplanan kurları almak isteyen dinleyici
     */
    public void addCalculatedListener(CalculatedRateListener listener) {
        calculatedRateListeners.add(listener);
    }

    /**
     * Daha önce eklenmiş bir hesaplanan kur dinleyicisini kaldırır.
     *
     * @param listener kaldırılacak dinleyici
     */
    public void removeCalculatedRateListener(CalculatedRateListener listener) {
        calculatedRateListeners.remove(listener);
    }

    /**
     * Ham kur güncellemesi alındığında çağrılır.
     * Güncellemeyi saklar, hem PF1 hem PF2 için değer varsa ortalama bid/ask hesaplar
     * ve "USDTRY", "EURTRY", "GBPTRY" gibi türetilmiş kurları oluşturup iletir.
     *
     * @param platformName veri kaynağının platform adı (ör. "PF1", "PF2")
     * @param rateName     tam kur adı, önek dahil (ör. "PF1_USDTRY")
     * @param fields       bid, ask ve zaman damgası bilgilerini içeren nesne
     */
    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields fields) {
        rawBySource
                .computeIfAbsent(platformName, k -> new ConcurrentHashMap<>())
                .put(rateName, fields);

        String symbol = rateName.contains("_")
                ? rateName.substring(rateName.indexOf('_') + 1)
                : rateName;

        rawBySource
                .computeIfAbsent(platformName, k -> new ConcurrentHashMap<>())
                .put(symbol, fields);

        Map<String, RateFields> pf1 = rawBySource.get("PF1");
        Map<String, RateFields> pf2 = rawBySource.get("PF2");

        if (pf2 == null) {
            if (missingWarnings.add(symbol)) {
                logger.error("PF2 henüz başlatılmadı: {}, atlanıyor…", symbol);
            }
            return;
        }

        if (pf1.containsKey("USDTRY") && pf2.containsKey("USDTRY")) {
            RateFields usd1 = pf1.get("USDTRY");
            RateFields usd2 = pf2.get("USDTRY");

            BigDecimal avgUsdBid = usd1.getBid().add(usd2.getBid())
                    .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
            BigDecimal avgUsdAsk = usd1.getAsk().add(usd2.getAsk())
                    .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

            dispatchDerived("USDTRY", avgUsdBid, avgUsdAsk);

            BigDecimal usdMid = avgUsdBid.add(avgUsdAsk)
                    .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

            if (pf1.containsKey("EURUSD") && pf2.containsKey("EURUSD")) {
                RateFields eur1 = pf1.get("EURUSD");
                RateFields eur2 = pf2.get("EURUSD");

                BigDecimal avgEurBid = eur1.getBid().add(eur2.getBid())
                        .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                BigDecimal avgEurAsk = eur1.getAsk().add(eur2.getAsk())
                        .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

                BigDecimal eurTryBid = usdMid.multiply(avgEurBid);
                BigDecimal eurTryAsk = usdMid.multiply(avgEurAsk);

                dispatchDerived("EURTRY", eurTryBid, eurTryAsk);
            }

            if (pf1.containsKey("GBPUSD") && pf2.containsKey("GBPUSD")) {
                RateFields gbp1 = pf1.get("GBPUSD");
                RateFields gbp2 = pf2.get("GBPUSD");

                BigDecimal avgGbpBid = gbp1.getBid().add(gbp2.getBid())
                        .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                BigDecimal avgGbpAsk = gbp1.getAsk().add(gbp2.getAsk())
                        .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

                BigDecimal gbpTryBid = usdMid.multiply(avgGbpBid);
                BigDecimal gbpTryAsk = usdMid.multiply(avgGbpAsk);

                dispatchDerived("GBPTRY", gbpTryBid, gbpTryAsk);
            }
        }
    }

    /**
     * Türetilmiş bir kur (
     * {@link Rate} nesnesi) oluşturur, dinleyicilere iletir ve log kaydı oluşturur.
     *
     * @param symbol para birimi kodu (ör. "USDTRY")
     * @param bid    hesaplanan bid fiyatı
     * @param ask    hesaplanan ask fiyatı
     */
    public void dispatchDerived(String symbol, BigDecimal bid, BigDecimal ask) {
        Rate derived = new Rate(
                "DERIVED",
                symbol,
                bid,
                ask,
                LocalDateTime.now()
        );

        for (CalculatedRateListener l : calculatedRateListeners) {
            l.onCalculatedRate(derived);
        }
        logger.info("Hesaplanan kur: {}", derived);
    }

    /**
     * data-fetchers.json konfigürasyon dosyasını okuyarak tanımlı veri alıcılarını
     * yansıma (reflection) ile oluşturur, bağlantı ayarlarını yapar ve abonelikleri başlatır.
     *
     * @throws Exception konfigürasyon dosyası okunamaz veya fetcher oluşturulamazsa fırlatılır
     */
    public void loadDataFetchersDynamically() throws Exception {
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("data-fetchers.json");

        List<FetcherConfig> configs = objectMapper.readValue(
                is, new TypeReference<List<FetcherConfig>>() {});

        for (FetcherConfig fc : configs) {
            Class<?> cls = Class.forName(fc.className);
            IAbstractDataFetcher fetcher;

            if (fc.config.containsKey("host") && fc.config.containsKey("port")) {
                String host = (String) fc.config.get("host");
                int port = (Integer) fc.config.get("port");

                Constructor<?> constructor = cls.getConstructor(String.class, int.class);
                fetcher = (IAbstractDataFetcher) constructor.newInstance(host, port);

            } else if (fc.config.containsKey("url") && fc.config.containsKey("interval")) {
                String url = (String) fc.config.get("url");
                int interval = (Integer) fc.config.get("interval");

                Constructor<?> constructor = cls.getConstructor(String.class, int.class);
                fetcher = (IAbstractDataFetcher) constructor.newInstance(url, interval);

            } else {
                fetcher = (IAbstractDataFetcher) cls.getConstructor().newInstance();
            }

            fetcher.setCallBack(this);
            fetcher.setPlatformName(fc.name);

            String user = (String) fc.config.get("user");
            String password = (String) fc.config.get("password");

            fetcher.connect(fc.name, user, password);

            for (String rate : fc.rates) {
                fetcher.subscribe(fc.name, rate);
            }

            dataFetchers.add(fetcher);
        }
    }

    /**
     * Veri alıcısı başarıyla bağlandığında çağrılır.
     *
     * @param platformName bağlanan platform adı
     * @param status       bağlantı durumu (true=başarılı, false=başarısız)
     */
    @Override
    public void onConnect(String platformName, Boolean status) {
        System.out.printf(">> %s bağlandı: %s%n", platformName, status);
    }

    /**
     * Veri alıcısı bağlantısı kesildiğinde çağrılır.
     *
     * @param platformName bağlantısı kesilen platform adı
     * @param status       bağlantı kesilme durumu
     */
    @Override
    public void onDisConnect(String platformName, Boolean status) {
        // no-op
    }

    /**
     * Tam bir {@link Rate} nesnesi kullanılabilir olduğunda çağrılır.
     *
     * @param platformName kurun geldiği platform adı
     * @param rateName     kurun adı
     * @param rate         Rate nesnesi
     */
    @Override
    public void onRateAvailable(String platformName, String rateName, Rate rate) {
        // kullanılmıyor
    }

    /**
     * Kurun durumu (OK, DELAYED, ERROR) raporlandığında çağrılır.
     *
     * @param platformName kurun geldiği platform adı
     * @param rateName     kurun adı
     * @param rateStatus   raporlanan durum
     */
    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        // kullanılmıyor
    }

    /**
     * Koordinatöre elle eklenmiş bir fetcher ekler.
     *
     * @param fetcher eklenecek fetcher
     */
    public void addDataFetcher(IAbstractDataFetcher fetcher) {
        dataFetchers.add(fetcher);
    }
}
