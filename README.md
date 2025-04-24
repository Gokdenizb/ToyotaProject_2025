ToyotaProject_2025
Genel Bakış:
ToyotaProject_2025, üç bağımsız bileşenden oluşan bir yabancı döviz kuru simülasyon ve dağıtım sistemidir:

TCP Veri Sağlayıcı (Tcp-Server)
Yapılandırılabilir FX kurlarını simüle eder ve basit bir TCP abonelik protokolü üzerinden sunar.

REST API Veri Sağlayıcı (Rest-Api)
Yapılandırılabilir FX kurlarını simüle eder ve HTTP uç noktaları aracılığıyla (ör. GET /rates/{rateName}) sunar.

Ana Koordinatör Programı (MainProgram)
Her iki sağlayıcıdan gelen verileri tüketir, türev çapraz kurları (ör. EURTRY, GBPTRY) hesaplar ve birleştirilmiş güncellemeleri iletir.

Her üç modül de Maven tabanlı Java 17 projeleridir. Proje kök dizinindeki docker-compose.yaml ile hepsi kolayca bir arada çalıştırılabilir.



Ön Koşullar:
Java 17 (Amazon Corretto 17 önerilir)

Maven 3.8+

Docker & Docker Compose (isteğe bağlı, konteyner ile çalıştırma için)

Git

Kullanım:

TCP Aboneliği:
Örneğin telnet localhost 8081 ile bağlanın, sonra:

subscribe|PF1_USDTRY
Abonelik onayı gelir ve düzenli olarak:


Updated rate: PF1_USDTRY|22:number:38.1234|25:number:38.2345|5:timestamp:2025-04-24 12:00:00

REST API:


GET http://localhost:8081/api/rates/{rateName}

Örnek JSON yanıt:


{
  "name": "PF2_USDTRY",
  "bid": 38.1234,
  "ask": 38.2345,
  "timestamp": "2025-04-24T12:00:00"
}

Ana Koordinatör:
PF1 (TCP) ve PF2 (REST) akışlarını birleştirir

USD çiftlerinin ortalamasını alıp USDTRY orta kuruyla çarpıp EURTRY, GBPTRY gibi türev kurlar hesaplar

dispatchDerived("EURTRY", bid, ask) ile çıktı verir





Lisans
Bu proje MIT Lisansı ile lisanslanmıştır.
