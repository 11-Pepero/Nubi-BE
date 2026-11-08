package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.program.data.enums.ItemResult;
import dev.hidly.nubi.program.data.enums.JudgementBasis;
import dev.hidly.nubi.support.data.enums.SupportCategory;

import java.math.BigDecimal;
import java.util.List;

public record EligibilityReasons(boolean capacityExceeded, List<Item> items, List<CareCheck> careChecks) {
    public record Item(Long supportTagId, SupportCategory category, String code, String label, Integer quantity,
                       String ratio, ItemResult result, JudgementBasis basis, String evidence, BigDecimal confidence) {
    }

    public record CareCheck(Long careId, String alias, Long supportTagId, String code, String label, String content,
                            ItemResult result, JudgementBasis basis, String evidence) {
    }

    public long count(ItemResult result) {
        return items.stream().filter(item -> item.result() == result).count();
    }

    public long careCheckCount() {
        return careChecks.stream().filter(check -> check.result() != ItemResult.MET).count();
    }
}
