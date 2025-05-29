package com.example.mainprogram;

import com.example.mainprogram.Kafka.KafkaRateProducer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Coordinator implements IAbstractFetcherCallBack {
    private static final Logger logger = LogManager.getLogger(Coordinator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    private final Map<String, DerivedFormula> formulaMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, RateFields>> rawBySource = new ConcurrentHashMap<>();
    private final Map<String, RateStatus> rateStatuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CalculatedRateListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IAbstractDataFetcher> dataFetchers = new CopyOnWriteArrayList<>();
    Map<String, LocalDateTime> lastDataTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Rate> lastDerivedRates = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastDispatchTimes = new ConcurrentHashMap<>();
    private static final Duration MIN_DISPATCH_INTERVAL = Duration.ofSeconds(5);
    private static final BigDecimal CHANGE_THRESHOLD = new BigDecimal("0.0001");

    private final Set<String> symbolsDispatchedInCycle = ConcurrentHashMap.newKeySet();

    @Autowired
    private RedisTemplate<String , Rate> redisTemplate;
    @Autowired
    private KafkaRateProducer kafkaRateProducer;

    private final Map<String, Rate> lastDerived = new ConcurrentHashMap<>();
    private static final Duration MIN_GAP = Duration.ofSeconds(1);   // 1 sn eşiği
    private static final BigDecimal EPS   = new BigDecimal("0.0001"); // ±0.0001

    private boolean almostEqual(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(EPS) < 0;
    }

    public void init() throws Exception {
        loadDerivedFormulas();
        loadDataFetchersDynamically();
    }

    private void loadDerivedFormulas() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("derived‐formulas.json")) {
            if (is == null) {
                throw new FileNotFoundException("derived-formulas.json not found in classpath!");
            }
            List<DerivedFormula> formulas = objectMapper.readValue(is, new TypeReference<List<DerivedFormula>>() {});
            formulas.forEach(f -> formulaMap.put(f.getSymbol(), f));
            logger.info("Yüklenen derived-formulas sayısı: {}", formulas.size());
        }
    }

    public void addCalculatedListener(CalculatedRateListener l) { listeners.add(l); }
    public void removeCalculatedListener(CalculatedRateListener l) { listeners.remove(l); }

    private boolean isEffectivelySame(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(CHANGE_THRESHOLD) < 0;
    }


    @Override
    public void onRateUpdate(String platformName,
                             String rateName,
                             RateFields fields) {

        // 1️⃣  Ham veriyi sakla + Redis’e yaz
        storeRawRate(platformName, rateName, fields);

        // 2️⃣  “Son veri geldi” zaman-damgasını güncelle
        touchLastTimestamp(platformName);

        // 3️⃣  PF2’den geldiyse türetilmiş kurları hesapla
        if ("PF2".equals(platformName)) {
            processDerivedRates(rateName);          // sadece PF2 tetikler
        }
    }

    /* ------------------------------------------------------------------ */
    /*  ALT  METODLAR                                                     */
    /* ------------------------------------------------------------------ */

    /** Ham veriyi bellekte tutar ve Redis’e yazar. */
    private void storeRawRate(String platform, String rateName, RateFields f) {
        String symbol = toSymbol(rateName);

        // bellek-içi saklama
        rawBySource
                .computeIfAbsent(platform, k -> new ConcurrentHashMap<>())
                .put(symbol, f);

        // Redis cache
        try {
            Rate raw = new Rate(platform, symbol, f.getBid(), f.getAsk(), LocalDateTime.now());
            redisTemplate.opsForValue().set("RAW_" + symbol, raw);
            logger.info("[RAW][{}] BID={} ASK={} platform={}", symbol, f.getBid(), f.getAsk(), platform);
        } catch (Exception e) {
            logger.error("Raw veriyi Redis’e yazarken hata: {}", e.getMessage());
        }
    }

    /** Son veri alma zamanını günceller. */
    private void touchLastTimestamp(String platform) {
        lastDataTimestamps.put(platform, LocalDateTime.now());
    }

    /** PF1 & PF2 verileriyle formülleri değerlendirir, gerekirse dispatch eder. */
    private void processDerivedRates(String triggeringRateName) {

        Map<String, RateFields> pf1 = rawBySource.get("PF1");
        Map<String, RateFields> pf2 = rawBySource.get("PF2");
        if (pf1 == null || pf2 == null) return;

        String triggeringSym = toSymbol(triggeringRateName);
        symbolsDispatchedInCycle.clear();

        for (DerivedFormula df : formulaMap.values()) {
            if (symbolsDispatchedInCycle.contains(df.getSymbol())) continue;
            if (!formulaReferencesSymbol(df, "pf2_", triggeringSym)) continue;

            // ihtiyaç duyulan sembolleri topla, eksikse pas geç
            Set<String> needPf1 = extractSymbols(df, "pf1_");
            Set<String> needPf2 = extractSymbols(df, "pf2_");
            if (!pf1.keySet().containsAll(needPf1) || !pf2.keySet().containsAll(needPf2)) continue;

            evaluateAndDispatch(df, pf1, pf2);
        }
    }

    /** SpEL ifadesini değerlendirir ve dispatchDerived çağırır. */
    private void evaluateAndDispatch(DerivedFormula df,
                                     Map<String, RateFields> pf1,
                                     Map<String, RateFields> pf2) {

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        extractSymbols(df, "pf1_").forEach(s -> ctx.setVariable("pf1_" + s, pf1.get(s)));
        extractSymbols(df, "pf2_").forEach(s -> ctx.setVariable("pf2_" + s, pf2.get(s)));

        try {
            BigDecimal bid = parser.parseExpression(df.getFormulaBid()).getValue(ctx, BigDecimal.class);
            BigDecimal ask = parser.parseExpression(df.getFormulaAsk()).getValue(ctx, BigDecimal.class);
            dispatchDerived(df.getSymbol(), bid, ask);
            symbolsDispatchedInCycle.add(df.getSymbol());
        } catch (Exception ex) {
            logger.error("Formül değerlendirme hatası ({}): {}", df.getSymbol(), ex.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  YARDIMCI  METODLAR                                                */
    /* ------------------------------------------------------------------ */
    private String toSymbol(String rateName) {
        return rateName.contains("_") ? rateName.substring(rateName.indexOf('_') + 1) : rateName;
    }

    /** İfade, belirli bir sembolü (#pfX_symbol.) içeriyor mu? */
    private boolean formulaReferencesSymbol(DerivedFormula df,
                                            String prefix,
                                            String symbol) {
        return df.getFormulaBid().contains("#" + prefix + symbol + ".")
                || df.getFormulaAsk().contains("#" + prefix + symbol + ".");
    }

    /** Bir formülden ‘pf1_XXX’ veya ‘pf2_XXX’ kalıplarını çeker. */
    private Set<String> extractSymbols(DerivedFormula df, String prefix) {
        Pattern p = Pattern.compile("#" + prefix + "([A-Z]+)\\.");
        Set<String> all = new HashSet<>();
        Matcher m = p.matcher(df.getFormulaBid());
        while (m.find()) all.add(m.group(1));
        m = p.matcher(df.getFormulaAsk());
        while (m.find()) all.add(m.group(1));
        return all;
    }


    private synchronized void dispatchDerived(String sym, BigDecimal bid, BigDecimal ask) {

        Rate prev = lastDerived.get(sym);
        LocalDateTime now = LocalDateTime.now();

        // 1️⃣  Değer değişmediyse VE son gönderim 1 saniyeden yeni ise => atla
        if (prev != null &&
                Duration.between(prev.getTimestamp(), now).compareTo(MIN_GAP) < 1) {
            logger.debug("Throttled duplicate for {}", sym);
            return;
        }

        // 2️⃣  Yeni kayıt hazırla ve önbelleği güncelle
        Rate r = new Rate("DERIVED", sym, bid, ask, now);
        lastDerived.put(sym, r);

        // 3️⃣  Yayın akışı değişmedi
        redisTemplate.opsForValue().set("DERIVED_" + sym, r);
        kafkaRateProducer.sendRateData(r);          // Kafka → PostgreSQL
        listeners.forEach(l -> l.onCalculatedRate(r));
    }

    public void loadDataFetchersDynamically() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("data-fetchers.json")) {
            List<FetcherConfig> configs = objectMapper.readValue(is, new TypeReference<List<FetcherConfig>>() {});
            for (FetcherConfig fc : configs) {
                try {
                    Class<?> cls = Class.forName(fc.className);
                    IAbstractDataFetcher fetcher;
                    if (fc.name.equalsIgnoreCase("PF1")) {
                        String host = (String) fc.config.get("host");
                        int port = (Integer) fc.config.get("port");
                        fetcher = (IAbstractDataFetcher) cls.getConstructor(String.class, int.class).newInstance(host, port);
                    } else if (fc.name.equalsIgnoreCase("PF2")) {
                        String url = (String) fc.config.get("url");
                        int interval = (Integer) fc.config.get("interval");
                        fetcher = (IAbstractDataFetcher) cls.getConstructor(String.class, int.class).newInstance(url, interval);
                    } else {
                        fetcher = (IAbstractDataFetcher) cls.getConstructor().newInstance();
                    }
                    fetcher.setCallBack(this);
                    fetcher.setPlatformName(fc.name);
                    fetcher.connect(fc.name, (String) fc.config.get("user"), (String) fc.config.get("password"));
                    fc.rates.forEach(rate -> fetcher.subscribe(fc.name, rate));
                    dataFetchers.add(fetcher);
                    logger.info("Fetcher başarıyla yüklendi: {} ({})", fc.name, cls.getSimpleName());
                } catch (Exception e){
                    logger.error("Fetcherlar oluşturulurken bir hata meydana geldi: {}", e.getMessage());
                }
            }
        }
    }

    public Rate getCachedRate(String symbol) {
        try {
            return redisTemplate.opsForValue().get(symbol);
        } catch (Exception e) {
            logger.error("Redis cache okuma hatası: {}", e.getMessage());
            return null;
        }
    }

    @Autowired
    MailService mailService;

    @Scheduled(fixedRate = 10000)
    public void checkDataTimeouts() {
        logger.info("checkDataTimeouts() çalıştı");
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, LocalDateTime> entry : lastDataTimestamps.entrySet()) {
            String platform = entry.getKey();
            Duration duration = Duration.between(entry.getValue(), now);
            if (duration.getSeconds() > 30) {
                logger.warn("[ALERT] {} platformu {} saniyedir veri göndermiyor", platform, duration.getSeconds());
                onRateStatus(platform, "*", RateStatus.ERROR);
            } else {
                onRateStatus(platform, "*", RateStatus.OK);
            }
        }
    }

    @Override public void onConnect(String p, Boolean s) {}
    @Override public void onDisConnect(String p, Boolean s) {}
    @Override public void onRateAvailable(String p, String r, Rate rate) {}
    @Override public void onRateStatus(String platformName, String rate, RateStatus rs) {
        String key = platformName + "_" + rate;
        RateStatus previous = rateStatuses.get(key);
        if (previous != rs) {
            rateStatuses.put(key, rs);
            logger.info("[STATUS] {} → {} → {}", key, previous, rs);
            if (rs == RateStatus.ERROR || rs == RateStatus.DELAYED) {
                mailService.sendAlert(platformName, Duration.ofSeconds(10));
            }
            if (rs == RateStatus.OK && previous == RateStatus.ERROR) {
                logger.info("[RECOVERY] {} platformu normale döndü.", key);
            }
        }
    }
}
