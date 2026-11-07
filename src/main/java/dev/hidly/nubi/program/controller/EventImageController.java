package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.program.data.dto.response.ProgramImageResponseDto;
import dev.hidly.nubi.program.service.ProgramImageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/images")
@RequiredArgsConstructor
public class EventImageController {
    private final ProgramImageService programImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "행사 사진 올리기", description = "주최 기관만 올릴 수 있고, 행사당 10장까지 저장합니다. 처음 올린 사진(sortOrder 0)이 대표 사진입니다.")
    public GlobalResponse<List<ProgramImageResponseDto>> upload(@PathVariable Long eventId,
                                                                @RequestPart("images") List<MultipartFile> images) {
        return programImageService.upload(eventId, images);
    }
}
