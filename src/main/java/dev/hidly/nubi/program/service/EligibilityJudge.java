package dev.hidly.nubi.program.service;

import dev.hidly.nubi.program.data.entity.EligibilityReasons;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.*;
import dev.hidly.nubi.support.data.entity.CareItem;
import dev.hidly.nubi.support.data.entity.CarePerson;
import dev.hidly.nubi.support.data.entity.GroupSupport;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EligibilityJudge {
    public static final Set<SupportCategory> REQUIRED_CATEGORIES = Set.of(SupportCategory.MOBILITY, SupportCategory.TOILET);
    public static final BigDecimal AI_CONFIDENCE_THRESHOLD = new BigDecimal("0.80");

    private static final List<ConditionSource> SOURCE_PRIORITY = List.of(ConditionSource.HOST, ConditionSource.REVIEW, ConditionSource.AI);
    private static final List<FacilityType> STEP_FREE_FACILITIES = List.of(FacilityType.ELEVATOR, FacilityType.RAMP, FacilityType.NO_STEP);
    private static final Map<String, List<FacilityType>> FACILITY_FALLBACK = Map.of(
            "WHEELCHAIR", STEP_FREE_FACILITIES,
            "POWER_WHEELCHAIR", STEP_FREE_FACILITIES,
            "NO_STAIRS", STEP_FREE_FACILITIES,
            "ACCESSIBLE_TOILET", List.of(FacilityType.ACCESSIBLE_TOILET));

    public record ProgramFacts(Program program, List<ProgramCondition> conditions, Map<FacilityType, FacilityStatus> facilities,
                               long takenSeats) {
    }

    public record Judgement(EligibilityResult result, EligibilityReasons reasons) {
    }

    public Judgement judge(SupportGroup group, List<CarePerson> carePersons, ProgramFacts facts) {
        Integer capacity = facts.program().getCapacity();
        boolean capacityExceeded = capacity != null && facts.takenSeats() + group.getMemberCount() > capacity;

        List<EligibilityReasons.Item> items = group.getSupports().stream()
                .map(support -> toItem(support, evaluate(support.getSupportTag(), facts)))
                .toList();

        List<EligibilityReasons.CareCheck> careChecks = new ArrayList<>();
        for (CarePerson carePerson : carePersons) {
            for (CareItem item : carePerson.getItems()) {
                careChecks.add(toCareCheck(carePerson, item,
                        item.getSupportTag() == null ? null : evaluate(item.getSupportTag(), facts)));
            }
        }

        EligibilityReasons reasons = new EligibilityReasons(capacityExceeded, items, careChecks);
        return new Judgement(decide(reasons), reasons);
    }

    private EligibilityResult decide(EligibilityReasons reasons) {
        boolean requiredMissing = reasons.items().stream()
                .anyMatch(item -> item.result() == ItemResult.MISSING && REQUIRED_CATEGORIES.contains(item.category()));
        if (reasons.capacityExceeded() || requiredMissing) {
            return EligibilityResult.NOT;
        }

        boolean allMet = reasons.items().stream().allMatch(item -> item.result() == ItemResult.MET);
        boolean careClear = reasons.careChecks().stream()
                .allMatch(check -> check.result() == ItemResult.MET || check.result() == ItemResult.NOTE);
        return allMet && careClear ? EligibilityResult.ELIGIBLE : EligibilityResult.CHECK;
    }

    private Evaluation evaluate(SupportTag tag, ProgramFacts facts) {
        List<ProgramCondition> conditions = facts.conditions().stream()
                .filter(condition -> condition.getSupportTag().getId().equals(tag.getId()))
                .sorted(Comparator.comparingInt(condition -> SOURCE_PRIORITY.indexOf(condition.getSource())))
                .toList();

        for (ProgramCondition condition : conditions) {
            if (!isTrusted(condition)) {
                continue;
            }
            if (condition.getStatus() == ConditionStatus.PROVIDED) {
                return Evaluation.of(ItemResult.MET, condition);
            }
            if (condition.getStatus() == ConditionStatus.NOT_PROVIDED) {
                return Evaluation.of(ItemResult.MISSING, condition);
            }
        }

        List<FacilityType> fallback = FACILITY_FALLBACK.getOrDefault(tag.getCode(), List.of());
        if (!fallback.isEmpty()) {
            List<FacilityStatus> statuses = fallback.stream()
                    .map(type -> facts.facilities().getOrDefault(type, FacilityStatus.UNKNOWN))
                    .toList();
            if (statuses.contains(FacilityStatus.YES)) {
                return new Evaluation(ItemResult.MET, JudgementBasis.VENUE_FACILITY, null, null);
            }
            if (statuses.stream().allMatch(status -> status == FacilityStatus.NO)) {
                return new Evaluation(ItemResult.MISSING, JudgementBasis.VENUE_FACILITY, null, null);
            }
        }

        return conditions.isEmpty()
                ? new Evaluation(ItemResult.UNKNOWN, null, null, null)
                : Evaluation.of(ItemResult.UNKNOWN, conditions.getFirst());
    }

    private boolean isTrusted(ProgramCondition condition) {
        if (condition.getSource() != ConditionSource.AI) {
            return true;
        }
        return condition.getConfidence() != null && condition.getConfidence().compareTo(AI_CONFIDENCE_THRESHOLD) >= 0;
    }

    private EligibilityReasons.Item toItem(GroupSupport support, Evaluation evaluation) {
        SupportTag tag = support.getSupportTag();
        return new EligibilityReasons.Item(tag.getId(), tag.getCategory(), tag.getCode(), tag.getLabel(),
                support.getQuantity(), support.getRatio(), evaluation.result(), evaluation.basis(),
                evaluation.evidence(), evaluation.confidence());
    }

    private EligibilityReasons.CareCheck toCareCheck(CarePerson carePerson, CareItem item, Evaluation evaluation) {
        SupportTag tag = item.getSupportTag();
        return new EligibilityReasons.CareCheck(carePerson.getId(), carePerson.getAlias(),
                tag == null ? null : tag.getId(),
                tag == null ? null : tag.getCode(),
                tag == null ? null : tag.getLabel(),
                item.getContent(),
                evaluation == null ? ItemResult.NOTE : evaluation.result(),
                evaluation == null ? null : evaluation.basis(),
                evaluation == null ? null : evaluation.evidence());
    }

    private record Evaluation(ItemResult result, JudgementBasis basis, String evidence, BigDecimal confidence) {
        static Evaluation of(ItemResult result, ProgramCondition condition) {
            return new Evaluation(result, JudgementBasis.valueOf(condition.getSource().name()),
                    condition.getEvidence(), condition.getConfidence());
        }
    }
}
