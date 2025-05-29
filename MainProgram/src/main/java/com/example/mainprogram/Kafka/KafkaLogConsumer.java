package com.example.mainprogram.Kafka;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class KafkaLogConsumer {

    private final RestHighLevelClient client;

    public KafkaLogConsumer(RestHighLevelClient client) {
        this.client = client;
    }

    @KafkaListener(topics = "log-topic", groupId = "opensearch-group")
    public void consumeLog(String log) throws IOException {
        IndexRequest indexRequest = new IndexRequest("app-logs")
                .source(Map.of("log", log, "timestamp", Instant.now().toString()));
        client.index(indexRequest, RequestOptions.DEFAULT);

    }
}
