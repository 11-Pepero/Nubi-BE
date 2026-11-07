package dev.hidly.nubi.support.data.dto.request;

import java.util.List;

public interface CarePersonRequest {
    String getAlias();

    List<CareItemRequestDto> getItems();

    String getMemo();

    Boolean getApplyToJudgement();

    default List<CareItemRequestDto> itemsOrEmpty() {
        return getItems() == null ? List.of() : getItems();
    }

    default boolean applyToJudgementOrDefault() {
        return getApplyToJudgement() == null || getApplyToJudgement();
    }
}
