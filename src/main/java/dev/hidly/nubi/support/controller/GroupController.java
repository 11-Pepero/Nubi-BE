package dev.hidly.nubi.support.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.support.data.dto.request.GroupRequestDto;
import dev.hidly.nubi.support.data.dto.response.GroupResponseDto;
import dev.hidly.nubi.support.data.dto.response.GroupSummaryResponseDto;
import dev.hidly.nubi.support.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @GetMapping
    @Operation(summary = "그룹 목록", description = "우리 기관의 공통 지원 그룹과 요약 숫자")
    public GlobalResponse<List<GroupResponseDto>> getGroups() {
        return groupService.getGroups();
    }

    @PostMapping
    @Operation(summary = "그룹 만들기", description = "그룹과 공통 지원 등록")
    public GlobalResponse<GroupResponseDto> createGroup(@Valid @RequestBody GroupRequestDto request) {
        return groupService.createGroup(request);
    }

    @GetMapping("/summary")
    @Operation(summary = "기관 전체 요약", description = "그룹 수, 전체 인원, 지원 항목별 필요 인원")
    public GlobalResponse<GroupSummaryResponseDto> getSummary() {
        return groupService.getSummary();
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "그룹 상세", description = "공통 지원 목록과 개별 케어 수")
    public GlobalResponse<GroupResponseDto> getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(groupId);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "그룹 수정", description = "이름, 인원, 공통 지원을 요청 내용으로 바꿉니다.")
    public GlobalResponse<GroupResponseDto> updateGroup(@PathVariable Long groupId,
                                                        @Valid @RequestBody GroupRequestDto request) {
        return groupService.updateGroup(groupId, request);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "그룹 삭제", description = "관리자만 가능, 소프트 삭제")
    public GlobalResponse<Void> deleteGroup(@PathVariable Long groupId) {
        return groupService.deleteGroup(groupId);
    }
}
