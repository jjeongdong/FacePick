package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.back.facepick.photo.application.PhotoOutboxRelay;
import com.back.facepick.photo.domain.PhotoEventPublisher;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import com.back.facepick.photo.domain.PhotoPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class PhotoPipelineSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EventPhotoPipeline.class, SyncPhotoPipelineConfig.class, PhotoOutboxRelay.class)
            .withBean(PhotoOutboxRepository.class, () -> mock(PhotoOutboxRepository.class))
            .withBean(PhotoEventPublisher.class, () -> mock(PhotoEventPublisher.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues(
                    "facepick.pipeline.thumbnail-url=http://thumbnail",
                    "facepick.pipeline.face-url=http://face",
                    "facepick.pipeline.connect-timeout-millis=2000",
                    "facepick.pipeline.thumbnail-read-timeout-millis=30000",
                    "facepick.pipeline.face-read-timeout-millis=60000");

    @Test
    @DisplayName("모드를 정하지 않으면 이벤트 방식과 outbox 발행기를 등록한다")
    void registersEventPipelineByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PhotoPipeline.class);
            assertThat(context.getBean(PhotoPipeline.class)).isInstanceOf(EventPhotoPipeline.class);
            assertThat(context).hasSingleBean(PhotoOutboxRelay.class);
        });
    }

    @Test
    @DisplayName("sync 모드면 동기 방식만 등록하고 outbox 발행기는 등록하지 않는다")
    void registersSyncPipelineWithoutRelay() {
        contextRunner.withPropertyValues("facepick.pipeline.mode=sync").run(context -> {
            assertThat(context).hasSingleBean(PhotoPipeline.class);
            assertThat(context.getBean(PhotoPipeline.class)).isInstanceOf(SyncPhotoPipeline.class);
            assertThat(context).doesNotHaveBean(PhotoOutboxRelay.class);
        });
    }
}
