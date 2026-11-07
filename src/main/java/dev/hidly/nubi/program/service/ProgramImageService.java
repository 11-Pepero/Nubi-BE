package dev.hidly.nubi.program.service;

import dev.hidly.nubi.common.file.FileStorageService;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.dto.response.ProgramImageResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.exception.ImageCountExceededException;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramImageService {
    private static final int MAX_IMAGES = 10;

    private final ProgramRepository programRepository;
    private final ProgramImageRepository programImageRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GlobalResponse<List<ProgramImageResponseDto>> upload(Long eventId, List<MultipartFile> images) {
        Program program = getHostedProgram(eventId);
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
