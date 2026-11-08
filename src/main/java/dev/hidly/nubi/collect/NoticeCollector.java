package dev.hidly.nubi.collect;

import java.util.List;
import java.util.function.Predicate;

public interface NoticeCollector {
    String dataset();

    List<NoticeData> collect(Predicate<String> alreadyCollected);
}
