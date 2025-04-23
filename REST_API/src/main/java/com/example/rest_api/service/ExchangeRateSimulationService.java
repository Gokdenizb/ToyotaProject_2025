package com.example.rest_api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Yapılandırma dosyasındaki para birimi çiftleri için
 * rastgele değişimler uygulayarak döviz kuru simülasyonu yapar.
 * <p>
 * Uygulama başlatıldığında JSON konfigürasyonunu okur ve
 * belirlenen frekansta fiyat güncellemeleri üretir.
 * </p>
 */
@Service
public class ExchangeRateSimulationService {

    /**
     * Simülasyonun üst seviye ayarlarını temsil eden POJO.
     */
    public static class SimulationConfigPOJO {
        /**
         * Başlangıç fiyatlarına dönmeden önce üretilecek güncelleme sayısı.
         */
        @JsonProperty("stream_amount")
        public int streamAmount;

        /**
         * Güncellemelerin üretileceği zaman aralığı (milisaniye cinsinden).
         */
        @JsonProperty("stream_frequency")
        public int streamFrequency;

        /**
         * Simülasyonu yapılacak para birimi çiftlerinin listesi.
         */
        @JsonProperty("currency_pairs")
        public List<PairConfig> currencyPairs;
    }

    /**
     * Tek bir para birimi çifti için ayarları tutar.
     */
    public static class PairConfig {
        /**
         * Para birimi çiftinin benzersiz adı (örn. "EURUSD").
         */
        @JsonProperty("name")
        public String name;

        /**
         * Simülasyonda kullanılacak başlangıç bid fiyatı.
         */
        @JsonProperty("initial_bid")
        public BigDecimal initialBid;

        /**
         * Simülasyonda kullanılacak başlangıç ask fiyatı.
         */
        @JsonProperty("initial_ask")
        public BigDecimal initialAsk;

        /**
         * Fiyat güncellemelerinde maksimum rastgele oynama faktörü.
         */
        @JsonProperty("update_factor")
        public BigDecimal updateFactor;
    }

    /**
     * İç sınıf: Tek bir para birimi çifti için simülasyon durumunu
     * ve güncellemeleri yönetir.
     */
    private static class Simulator {
        private final String name;
        private final BigDecimal initialBid, initialAsk, updateFactor;
        private final int streamAmount;
        private int streamCount;
        private BigDecimal currentBid, currentAsk;
        private LocalDateTime lastUpdate;
        private final Random random = new Random();
        private ExchangeRateDTO lastEmitted;

        /**
         * Simulator nesnesini yapılandırmadan oluşturur.
         *
         * @param cfg          Para birimi çiftine ait konfigürasyon
         * @param streamAmount Sıfırlamadan önceki adım sayısı
         */
        Simulator(PairConfig cfg, int streamAmount) {
            this.name = cfg.name;
            this.initialBid = cfg.initialBid;
            this.initialAsk = cfg.initialAsk;
            this.updateFactor = cfg.updateFactor;
            this.streamAmount = streamAmount;
            reset();
        }

        /**
         * Simülatörü başlangıç değerlerine sıfırlar.
         */
        private void reset() {
            this.currentBid = initialBid;
            this.currentAsk = initialAsk;
            this.streamCount = 0;
            this.lastUpdate = LocalDateTime.now();
            this.lastEmitted = buildDTO();
        }

        /**
         * Mevcut duruma göre DTO oluşturur.
         *
         * @return Son üretilen ExchangeRateDTO
         */
        private ExchangeRateDTO buildDTO() {
            ExchangeRateDTO dto = new ExchangeRateDTO();
            dto.setRateName(name);
            dto.setBid(currentBid);
            dto.setAsk(currentAsk);
            dto.setTimeStamp(lastUpdate);
            return dto;
        }

        /**
         * Bir simülasyon adımı gerçekleştirir: fiyatları rastgele günceller,
         * yüzde fark sınırını ve bid-ask sırasını korur, gerekli ise sıfırlar.
         */
        void tick() {
            if (streamCount >= streamAmount) {
                reset();
            } else {
                double diffBid = random.nextDouble() * updateFactor.doubleValue();
                double diffAsk = random.nextDouble() * updateFactor.doubleValue();
                BigDecimal candBid = currentBid.add(BigDecimal.valueOf(random.nextBoolean() ? diffBid : -diffBid));
                BigDecimal candAsk = currentAsk.add(BigDecimal.valueOf(random.nextBoolean() ? diffAsk : -diffAsk));

                // Ask fiyatı mutlaka bid fiyatından 0.01 fazla olmalı
                if (candAsk.compareTo(candBid) <= 0) {
                    candAsk = candBid.add(BigDecimal.valueOf(0.01));
                }
                LocalDateTime now = LocalDateTime.now();

                // %1'den fazla fiyat sıçraması varsa değişikliği yoksay
                if (pctDiff(currentBid, candBid) > 1 || pctDiff(currentAsk, candAsk) > 1) {
                    candBid = currentBid;
                    candAsk = currentAsk;
                    now = lastUpdate;
                }
                currentBid = candBid;
                currentAsk = candAsk;
                lastUpdate = now;
                streamCount++;
                lastEmitted = buildDTO();
            }
        }

        /**
         * Son üretilen rate DTO'sunu döner.
         *
         * @return En son ExchangeRateDTO
         */
        ExchangeRateDTO getLastEmitted() {
            return lastEmitted;
        }

        /**
         * İki değerin yüzde farkını hesaplar: |old - new| / old * 100.
         * 8 ondalık hane ile yuvarlanır.
         *
         * @param oldVal Eski referans değeri
         * @param newVal Yeni değer
         * @return Yüzde değişim (örn. 2.0 => %%2 değişim)
         */
        private double pctDiff(BigDecimal oldVal, BigDecimal newVal) {
            if (oldVal.compareTo(newVal) == 0) {
                return 0;
            }
            return oldVal.subtract(newVal).abs()
                    .divide(oldVal, 8, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
    }

    /**
     * Para birimi çiftlerine göre simülatörleri tutan harita.
     */
    private final Map<String, Simulator> sim = new ConcurrentHashMap<>();

    /**
     * Uygulama başladığında konfigürasyonu okuyup simülasyonu başlatır.
     *
     * @throws Exception Konfigürasyon okunamazsa fırlatılır
     */
    @PostConstruct
    public void init() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try (InputStream is = new ClassPathResource("config.json").getInputStream()) {
            SimulationConfigPOJO cfg = objectMapper.readValue(is, SimulationConfigPOJO.class);
            for (PairConfig pc : cfg.currencyPairs) {
                sim.put(pc.name, new Simulator(pc, cfg.streamAmount));
            }

            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleAtFixedRate(
                    () -> sim.values().forEach(Simulator::tick), 0,
                    cfg.streamFrequency,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Belirtilen para birimi çifti için en son simüle edilen rate'i döner.
     *
     * @param currencyPair Para birimi çiftinin adı (örn. "EURUSD")
     * @return En son oluşturulan ExchangeRateDTO
     * @throws NoSuchElementException Bilinmeyen çift adı verilirse fırlatılır
     */
    public ExchangeRateDTO getCurrentRate(String currencyPair) {
        Simulator sim = this.sim.get(currencyPair);
        if (sim == null) {
            throw new NoSuchElementException("Bilinmeyen çift: " + currencyPair);
        }
        return sim.getLastEmitted();
    }

    /**
     * API üzerinden döndürülecek rate bilgilerini tutan DTO sınıfı.
     */
    public static class ExchangeRateDTO {

        private String rateName;
        private BigDecimal bid;
        private BigDecimal ask;
        private LocalDateTime timeStamp;

        /**
         * @return Para birimi çiftinin adı
         */
        public String getRateName() { return rateName; }

        /**
         * @param rateName Para birimi çiftinin adını ayarlar
         */
        public void setRateName(String rateName) { this.rateName = rateName; }

        /**
         * @return Bid fiyatı
         */
        public BigDecimal getBid() { return bid; }

        /**
         * @param bid Bid fiyatını ayarlar
         */
        public void setBid(BigDecimal bid) { this.bid = bid; }

        /**
         * @return Ask fiyatı
         */
        public BigDecimal getAsk() { return ask; }

        /**
         * @param ask Ask fiyatını ayarlar
         */
        public void setAsk(BigDecimal ask) { this.ask = ask; }

        /**
         * @return Güncelleme zaman damgası
         */
        public LocalDateTime getTimeStamp() { return timeStamp; }

        /**
         * @param timeStamp Zaman damgasını ayarlar
         */
        public void setTimeStamp(LocalDateTime timeStamp) { this.timeStamp = timeStamp; }
    }
}
