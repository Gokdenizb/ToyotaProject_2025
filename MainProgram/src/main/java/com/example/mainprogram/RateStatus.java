package com.example.mainprogram;


public enum RateStatus {
    /**
     * Veri akışı sorunsuz, zamanında geliyor.
     */
    OK,

    /**
     * Veri güncellemesi beklenenden yavaş; gecikme oluştu.
     */
    DELAYED,

    /**
     * Veri akışı sırasında bir hata meydana geldi.
     */
    ERROR
}
