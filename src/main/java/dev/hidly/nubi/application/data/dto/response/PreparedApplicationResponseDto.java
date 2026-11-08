package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;

import java.time.LocalDateTime;

// 신청 준비(API-25): 만든 신청과 "접수하는 곳"(화면 08 오른쪽) 정보
public record PreparedApplicationResponseDto(
        ApplicationResponseDto application,
        ChannelInfo channel
) {
    public record ChannelInfo(ApplyMethod applyMethod, String hostName, String contactPhone, String applyUrl,
                              LocalDateTime deadlineAt) {
        static ChannelInfo from(Program program) {
            return new ChannelInfo(program.getApplyMethod(), program.getDisplayHostName(), program.getContactPhone(),
                    program.getApplyUrl(), program.getDeadlineAt());
        }
    }

    public static PreparedApplicationResponseDto of(Application application) {
        return new PreparedApplicationResponseDto(ApplicationResponseDto.from(application),
                ChannelInfo.from(application.getProgram()));
    }
}
