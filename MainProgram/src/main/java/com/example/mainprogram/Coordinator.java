package com.example.mainprogram;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Coordinator implements IAbstractFetcherCallBack {
    private static final Logger logger = LogManager.getLogger(Coordinator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    // JSON’dan yüklenecek formüller
    private final Map<String, DerivedFormula> formulaMap = new ConcurrentHashMap<>();

    // Platform → (rawKey veya symbol) → RateFields
    private final Map<String, Map<String, RateFields>> rawBySource = new ConcurrentHashMap<>();

    // Dinleyiciler ve fetcher’lar
    private final CopyOnWriteArrayList<CalculatedRateListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IAbstractDataFetcher> dataFetchers = new CopyOnWriteArrayList<>();

    /** Başlatma: formülleri yükle, ardından fetcher’ları dinamik ayağa kaldır */
    public void init() throws Exception {
        loadDerivedFormulas();
        loadDataFetchersDynamically();
    }

    /** JSON’daki tüm DerivedFormula nesnelerini oku ve `formulaMap`’e koy */
    private void loadDerivedFormulas() throws Exception {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("derived‐formulas.json")) {
            if (is == null) {
                throw new FileNotFoundException("derived-formulas.json not found in classpath!");
            }
            List<DerivedFormula> formulas = objectMapper.readValue(
                    is, new TypeReference<List<DerivedFormula>>() {});
            formulas.forEach(f -> formulaMap.put(f.getSymbol(), f));
            logger.info("Yüklenen derived-formulas sayısı: {}", formulas.size());
        }
    }

    public void addCalculatedListener(CalculatedRateListener l)    { listeners.add(l); }
    public void removeCalculatedListener(CalculatedRateListener l) { listeners.remove(l); }

    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields fields) {
        // 1) Ham veriyi sakla
        rawBySource
                .computeIfAbsent(platformName, k -> new ConcurrentHashMap<>())
                .put(rateName, fields);
        String symbol = rateName.contains("_")
                ? rateName.substring(rateName.indexOf('_') + 1)
                : rateName;
        rawBySource.get(platformName).put(symbol, fields);

        // 2) Sadece PF2 tetikliyor
        if (!"PF2".equals(platformName)) return;
        Map<String, RateFields> pf1 = rawBySource.get("PF1");
        Map<String, RateFields> pf2 = rawBySource.get("PF2");
        if (pf1 == null || pf2 == null) return;

        // 3) Gelen sembole bağlı formülleri çalıştır
        String marker = "#pf2_" + symbol + ".";
        for (DerivedFormula df : formulaMap.values()) {
            String exprBid = df.getFormulaBid();
            String exprAsk = df.getFormulaAsk();
            if (!exprBid.contains(marker) && !exprAsk.contains(marker)) {
                continue;
            }

            // 4) Gerekli tüm değişkenleri tespit et
            Set<String> needPf1 = new HashSet<>();
            Set<String> needPf2 = new HashSet<>();
            Pattern p1 = Pattern.compile("#pf1_([A-Z]+)\\.");
            Pattern p2 = Pattern.compile("#pf2_([A-Z]+)\\.");
            Matcher m1 = p1.matcher(exprBid);
            while (m1.find()) needPf1.add(m1.group(1));
            m1 = p1.matcher(exprAsk);
            while (m1.find()) needPf1.add(m1.group(1));
            Matcher m2 = p2.matcher(exprBid);
            while (m2.find()) needPf2.add(m2.group(1));
            m2 = p2.matcher(exprAsk);
            while (m2.find()) needPf2.add(m2.group(1));

            // 5) Eğer eksikse atla
            if (!pf1.keySet().containsAll(needPf1) ||
                    !pf2.keySet().containsAll(needPf2)) {
                continue;
            }

            // 6) Hepsi varsa SpEL ile değerlendir
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            needPf1.forEach(sym -> ctx.setVariable("pf1_" + sym, pf1.get(sym)));
            needPf2.forEach(sym -> ctx.setVariable("pf2_" + sym, pf2.get(sym)));

            try {
                BigDecimal bid = parser.parseExpression(exprBid)
                        .getValue(ctx, BigDecimal.class);
                BigDecimal ask = parser.parseExpression(exprAsk)
                        .getValue(ctx, BigDecimal.class);
                dispatchDerived(df.getSymbol(), bid, ask);
            } catch (Exception ex) {
                logger.error("Formül değerlendirme hatası ({}): {}",
                        df.getSymbol(), ex.getMessage());
            }
        }
    }

    /** Derived Rate oluşturup dinleyicilere ilet ve logla */
    public void dispatchDerived(String symbol, BigDecimal bid, BigDecimal ask) {
        Rate derived = new Rate("DERIVED", symbol, bid, ask, LocalDateTime.now());
        listeners.forEach(l -> l.onCalculatedRate(derived));
        logger.info("Hesaplanan kur: {}", derived);
    }

    /** data-fetchers.json’daki konfigürasyona göre fetcher’ları ayağa kaldır */
    public void loadDataFetchersDynamically() throws Exception {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("data-fetchers.json")) {
            List<FetcherConfig> configs = objectMapper.readValue(
                    is, new TypeReference<List<FetcherConfig>>() {});
            for (FetcherConfig fc : configs) {
                Class<?> cls = Class.forName(fc.className);
                IAbstractDataFetcher fetcher;
                if (fc.config.containsKey("host") && fc.config.containsKey("port")) {
                    String host = (String) fc.config.get("host");
                    int port = (Integer) fc.config.get("port");
                    fetcher = (IAbstractDataFetcher)
                            cls.getConstructor(String.class, int.class)
                                    .newInstance(host, port);
                } else if (fc.config.containsKey("url") && fc.config.containsKey("interval")) {
                    String url = (String) fc.config.get("url");
                    int interval = (Integer) fc.config.get("interval");
                    fetcher = (IAbstractDataFetcher)
                            cls.getConstructor(String.class, int.class)
                                    .newInstance(url, interval);
                } else {
                    fetcher = (IAbstractDataFetcher) cls.getConstructor().newInstance();
                }
                fetcher.setCallBack(this);
                fetcher.setPlatformName(fc.name);
                fetcher.connect(fc.name,
                        (String) fc.config.get("user"),
                        (String) fc.config.get("password"));
                fc.rates.forEach(rate -> fetcher.subscribe(fc.name, rate));
                dataFetchers.add(fetcher);
            }
        }
    }

    // Diğer IAbstractFetcherCallBack metotları (no-op)
    @Override public void onConnect(String p, Boolean s) {}
    @Override public void onDisConnect(String p, Boolean s) {}
    @Override public void onRateAvailable(String p, String r, Rate rate) {}
    @Override public void onRateStatus(String p, String r, RateStatus rs) {}
}
