package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.domain.PhotoPipeline;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoProcessingUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoCompleteServiceTest {

    @Mock
    private PhotoCommandService photoCommandService;

    @Mock
    private PhotoPipeline photoPipeline;

    @InjectMocks
    private PhotoCompleteService photoCompleteService;

    @Test
    @DisplayName("완료 트랜잭션이 끝난 뒤 처리를 넘기고 완료 결과를 돌려준다")
    void handsOverAfterComplete() {
        // given
        PhotoCompleteResult completed = new PhotoCompleteResult(12L, PhotoStatus.UPLOADED);
        given(photoCommandService.completePhoto(1L, 12L)).willReturn(completed);

        // when
        PhotoCompleteResult result = photoCompleteService.completePhoto(1L, 12L);

        // then
        assertThat(result).isEqualTo(completed);
        InOrder order = inOrder(photoCommandService, photoPipeline);
        order.verify(photoCommandService).completePhoto(1L, 12L);
        order.verify(photoPipeline).afterCommit(12L);
    }

    @Test
    @DisplayName("이미 완료된 사진이어도 커밋 뒤 처리를 넘긴다 (동기 방식의 재시도)")
    void handsOverAgainForAlreadyCompletedPhoto() {
        // given
        given(photoCommandService.completePhoto(1L, 12L))
                .willReturn(new PhotoCompleteResult(12L, PhotoStatus.UPLOADED));
        photoCompleteService.completePhoto(1L, 12L);

        // when
        photoCompleteService.completePhoto(1L, 12L);

        // then
        then(photoPipeline).should(times(2)).afterCommit(12L);
    }

    @Test
    @DisplayName("완료가 실패하면 처리를 넘기지 않는다")
    void doesNotHandOverWhenCompleteFails() {
        // given
        given(photoCommandService.completePhoto(1L, 12L)).willThrow(new PhotoFileMissingException());

        // when & then
        assertThatThrownBy(() -> photoCompleteService.completePhoto(1L, 12L))
                .isInstanceOf(PhotoFileMissingException.class);
        then(photoPipeline).should(never()).afterCommit(any());
    }

    @Test
    @DisplayName("처리를 넘기지 못하면 예외를 그대로 알린다")
    void propagatesPipelineFailure() {
        // given
        given(photoCommandService.completePhoto(1L, 12L))
                .willReturn(new PhotoCompleteResult(12L, PhotoStatus.UPLOADED));
        willThrow(new PhotoProcessingUnavailableException())
                .given(photoPipeline)
                .afterCommit(12L);

        // when & then
        assertThatThrownBy(() -> photoCompleteService.completePhoto(1L, 12L))
                .isInstanceOf(PhotoProcessingUnavailableException.class);
    }
}
