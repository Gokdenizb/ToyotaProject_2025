package com.example.mainprogram;

import com.example.mainprogram.Kafka.KafkaRateProducer;
import com.example.mainprogram.Rate.Rate;
import com.example.mainprogram.Rate.RateFields;
import com.example.mainprogram.Rate.RateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
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
import java.util.concurrent.*;
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
    private final ScheduledExecutorService reconnector =
            Executors.newSingleThreadScheduledExecutor();
    private final Map<String,Integer> retryCounters = new ConcurrentHashMap<>();
    private static final int MAX_RETRIES = 10;


    private final Set<String> symbolsDispatchedInCycle = ConcurrentHashMap.newKeySet();

    @Autowired
    private RedisTemplate<String , Rate> redisTemplate;
    @Autowired
    private KafkaRateProducer kafkaRateProducer;

    private final Map<String, Rate> lastDerived = new ConcurrentHashMap<>();
    private static final Duration MIN_GAP = Duration.ofSeconds(1);   // 1 sn eşiği
    private static final BigDecimal EPS   = new BigDecimal("0.0001"); // ±0.0001


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

    @Override
    public void onRateUpdate(String platformName,
                             String rateName,
                             RateFields fields)
    {
        onRateAvailable(platformName,
                rateName,
                toRate(platformName, rateName, fields));
    }


    /** Ham veriyi bellekte tutar ve Redis’e yazar. */
    private void storeRawRate(String platform, String rateName, RateFields f) {
        String symbol = toSymbol(rateName);

        rawBySource
                .computeIfAbsent(platform, k -> new ConcurrentHashMap<>())
                .put(symbol, f);

        try {
            Rate raw = new Rate(platform, symbol, f.getBid(), f.getAsk(), LocalDateTime.now());

            redisTemplate.opsForValue().set("RAW:" + symbol, raw);

            ObjectRecord<String, Rate> rec = StreamRecords.newRecord()
                    .in("STREAM:RAW:" + symbol)
                    .ofObject(raw);
            redisTemplate.opsForStream().add(rec);

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


    private synchronized void dispatchDerived(String sym,
                                              BigDecimal bid,
                                              BigDecimal ask) {

        Rate prev = lastDerived.get(sym);
        LocalDateTime now = LocalDateTime.now();

        // duplicate-within-1-sec guard
        if (prev != null &&
                Duration.between(prev.getTimestamp(), now).compareTo(MIN_GAP) < 1) {
            logger.debug("Throttled duplicate for {}", sym);
            return;
        }

        Rate r = new Rate("DERIVED", sym, bid, ask, now);
        lastDerived.put(sym, r);

        try {
            redisTemplate.opsForValue().set("DERIVED:" + sym, r);

            ObjectRecord<String, Rate> rec = StreamRecords.newRecord()
                    .in("STREAM:DERIVED:" + sym)
                    .ofObject(r);
            redisTemplate.opsForStream().add(rec);

        } catch (Exception e) {
            logger.error("Derived veriyi Redis’e yazarken hata: {}", e.getMessage());
        }

        kafkaRateProducer.sendRateData(r);          // Kafka → PostgreSQL
        logger.info("{}", r);
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



    @Autowired
    MailService mailService;

    @Scheduled(fixedRate = 10000)
    public void checkDataTimeouts() {
        logger.info("checkDataTimeouts() çalıştı");
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, LocalDateTime> entry : lastDataTimestamps.entrySet()) {
            String platform = entry.getKey();
            Duration duration = Duration.between(entry.getValue(), now);
            if (duration.getSeconds() > 5) {
                logger.warn("[ALERT] {} platformu {} saniyedir veri göndermiyor", platform, duration.getSeconds());
                onRateStatus(platform, "*", RateStatus.ERROR);
            } else {
                onRateStatus(platform, "*", RateStatus.OK);
            }
        }
    }

    private void scheduleReconnect(IAbstractDataFetcher fetcher) {
        String platform = fetcher.getPlatformName();
        int attempt = retryCounters.getOrDefault(platform, 0);

        if (attempt >= MAX_RETRIES) {
            logger.error("[RECONNECT] {} max retry ({}) reached – giving up.", platform, MAX_RETRIES);
            return;
        }

        long delaySec = 5L * (attempt + 1);
        retryCounters.put(platform, attempt + 1);

        logger.info("[RECONNECT] {} will retry in {} s (attempt {})",
                platform, delaySec, attempt + 1);

        reconnector.schedule(() -> {
            try {
                fetcher.disConnect(platform, null, null);
                fetcher.connect(platform, null, null);

            } catch (Exception e) {
                logger.error("[RECONNECT] {} attempt {} failed: {}", platform, attempt + 1, e.getMessage());
                scheduleReconnect(fetcher);
            }
        }, delaySec, TimeUnit.SECONDS);
    }

    private volatile ScheduledFuture<?> retryJob;

    private void scheduleReconnect(IAbstractDataFetcher f, int attempt, long delaySec) {
        if (retryJob != null && !retryJob.isDone()) return;     // already scheduled
        retryJob = reconnector.schedule(() -> doReconnect(f, attempt), delaySec, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        if (retryJob != null) retryJob.cancel(false);
    }

    private void doReconnect(IAbstractDataFetcher fetcher, int attempt) {
        try {
            fetcher.disConnect(fetcher.getPlatformName(), null, null);
            fetcher.connect(fetcher.getPlatformName(), null, null);
        } catch (Exception e) {
            logger.error("[RECONNECT] {} attempt {} failed: {}",
                    fetcher.getPlatformName(), attempt + 1, e.getMessage());
            scheduleReconnect(fetcher);
        }
    }

    private Rate toRate(String platform,
                        String rateName,
                        RateFields f)
    {
        // If the fetcher did not supply a timestamp, fall back to "now".
        LocalDateTime ts = (f.getTimestamp() != null) ? f.getTimestamp()
                : LocalDateTime.now();

        return new Rate(platform,               // platformName
                rateName,               // rateName   (→ becomes symbol after you strip PF?_)
                f.getBid(),
                f.getAsk(),
                ts);
    }



    @Override public void onConnect(String platform, Boolean ok) {
        if (ok) {
            logger.info("[CONNECT] {} SUCCESS", platform);
            retryCounters.remove(platform);
            onRateStatus(platform, "*", RateStatus.OK);
        } else {
            onDisConnect(platform, false);
        }
    }
    @Override public void onDisConnect(String platform, Boolean graceful) {

        logger.warn("[DISCONNECT] {} graceful={}", platform, graceful);
        onRateStatus(platform, "*", RateStatus.ERROR);

        dataFetchers.stream()
                .filter(f -> platform.equals(f.getPlatformName()))
                .findFirst()
                .ifPresent(this::scheduleReconnect);
    }
    @Override
    public void onRateAvailable(String platform,
                                String rateName,
                                Rate rate)
    {
        String symbol = toSymbol(rate.getRateName());
        /* 1️⃣  In-memory cache (latest quote) */
        rawBySource
                .computeIfAbsent(platform, k -> new ConcurrentHashMap<>())
                .put(symbol,                    // symbol already stripped?
                        new RateFields(rate.getBid(),
                                rate.getAsk(),
                                rate.getTimestamp()));

        /* 2️⃣  Persist to Redis (current + history stream) */
        try {
            redisTemplate.opsForValue().set("RAW:" + rate.getRateName(), rate);

            ObjectRecord<String, Rate> rec = StreamRecords.newRecord()
                    .in("STREAM:RAW:" + rate.getRateName())
                    .ofObject(rate);
            redisTemplate.opsForStream().add(rec);

            logger.info("[RAW][{}] BID={} ASK={} platform={}",
                    rate.getRateName(), rate.getBid(), rate.getAsk(), platform);

        } catch (Exception e) {
            logger.error("Raw veriyi Redis’e yazarken hata: {}", e.getMessage());
        }

        /* 3️⃣  Heart-beat */
        touchLastTimestamp(platform);

        /* 4️⃣  Derived-rate trigger */
        if ("PF2".equals(platform)) {
            processDerivedRates(rateName);
        }
    }

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
