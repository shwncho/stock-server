package com.stock.stockserver.infrastructure.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.dto.AnalysisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KafkaAnalysisRequestPublisherTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private KafkaAnalysisRequestPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new KafkaAnalysisRequestPublisher(kafkaTemplate, objectMapper);
    }

    @ParameterizedTest
    @MethodSource("analysisTargets")
    @DisplayName("publish - 분석 대상별 Kafka 이벤트 발행")
    void publish_byTarget(AnalysisTarget target) throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish("analysis-id", target);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("analysis-requests"), eq("analysis-id"), messageCaptor.capture());

        AnalysisEvent event = objectMapper.readValue(messageCaptor.getValue(), AnalysisEvent.class);
        assertEquals("analysis-id", event.analysisId());
        assertEquals(target, event.target());
    }

    @ParameterizedTest
    @MethodSource("analysisTargets")
    @DisplayName("publishAndWaitForAck - 분석 대상별 Kafka 이벤트 발행 대기")
    void publishAndWaitForAck_byTarget(AnalysisTarget target) throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishAndWaitForAck("analysis-id", target);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("analysis-requests"), eq("analysis-id"), messageCaptor.capture());

        AnalysisEvent event = objectMapper.readValue(messageCaptor.getValue(), AnalysisEvent.class);
        assertEquals(target, event.target());
    }

    private static Stream<AnalysisTarget> analysisTargets() {
        return Stream.of(AnalysisTarget.DOMESTIC, AnalysisTarget.OVERSEAS, AnalysisTarget.ALL);
    }
}
