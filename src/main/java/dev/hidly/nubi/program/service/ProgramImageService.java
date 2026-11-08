package dev.hidly.nubi.program.service;

import dev.hidly.nubi.common.file.FileStorageService;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.dto.request.ProgramImageOrderRequestDto;
import dev.hidly.nubi.program.data.dto.response.ProgramImageResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.exception.ImageCountExceededException;
import dev.hidly.nubi.program.data.exception.ImageNotFoundException;
import dev.hidly.nubi.program.data.exception.ImageOrderInvalidException;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramImageService {
    private static final int MAX_IMAGES = 10;

    private final ProgramRepository programRepository;
    private final ProgramImageRepository programImageRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GlobalResponse<List<ProgramImageResponseDto>> upload(Long eventId, List<MultipartFile> images) {
        getHostedProgram(eventId);
        Program program = programRepository.findLockedById(eventId).orElseThrow(ProgramNotFoundException::new);
        List<ProgramImage> current = programImageRepository.findAllByProgramIdOrderBySortOrderAsc(program.getId());
        if (images == null || images.isEmpty() || current.size() + images.size() > MAX_IMAGES) {
            throw new ImageCountExceededException();
        }

        int nextOrder = current.isEmpty() ? 0 : current.getLast().getSortOrder() + 1;
        List<String> savedUrls = new ArrayList<>();
        try {
            for (MultipartFile image : images) {
                String url = fileStorageService.save(image);
                savedUrls.add(url);
                current.add(programImageRepository.save(ProgramImage.builder()
                        .program(program)
                        .url(url)
                        .sortOrder(nextOrder++)
                        .build()));
            }
        } catch (RuntimeException e) {
            savedUrls.forEach(fileStorageService::delete);
            throw e;
        }

        return GlobalResponse.ok(current.stream().map(ProgramImageResponseDto::from).toList());
    }

    @Transactional
    public GlobalResponse<List<ProgramImageResponseDto>> reorder(Long eventId, ProgramImageOrderRequestDto request) {
        Program program = getHostedProgram(eventId);
        Map<Long, ProgramImage> images = programImageRepository.findAllByProgramIdOrderBySortOrderAsc(program.getId())
                .stream()
                .collect(Collectors.toMap(ProgramImage::getId, Function.identity()));
        List<Long> imageIds = request.imageIds();
        if (imageIds.size() != images.size() || !Set.copyOf(imageIds).equals(images.keySet())) {
            throw new ImageOrderInvalidException();
        }

        List<ProgramImage> ordered = new ArrayList<>();
        for (int i = 0; i < imageIds.size(); i++) {
            ProgramImage image = images.get(imageIds.get(i));
            image.changeSortOrder(i);
            ordered.add(image);
        }
        return GlobalResponse.ok(ordered.stream().map(ProgramImageResponseDto::from).toList());
    }

    @Transactional
    public GlobalResponse<List<ProgramImageResponseDto>> delete(Long eventId, Long imageId) {
        Program program = getHostedProgram(eventId);
        List<ProgramImage> images = programImageRepository.findAllByProgramIdOrderBySortOrderAsc(program.getId());
        ProgramImage target = images.stream()
                .filter(image -> image.getId().equals(imageId))
                .findFirst()
                .orElseThrow(ImageNotFoundException::new);

        programImageRepository.delete(target);
        images.remove(target);
        for (int i = 0; i < images.size(); i++) {
            images.get(i).changeSortOrder(i);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.delete(target.getUrl());
            }
        });
        return GlobalResponse.ok(images.stream().map(ProgramImageResponseDto::from).toList());
    }

    private Program getHostedProgram(Long eventId) {
        Program program = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!program.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }
        return program;
    }
}
