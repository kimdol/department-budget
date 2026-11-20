package kr.budget.closing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class StatementComparison {
    public record Difference(String category, Long legacyCount, Long legacySpent, Long ledgerCount, Long ledgerSpent) {}
    private record Amounts(long count, Long spent) {}

    public static List<Difference> compare(PeriodClosing.Statement statement, String csv) {
        if (csv == null || csv.length() > 2048) throw new IllegalArgumentException("CSV는 2,048자까지 입력하세요.");
        var rows = csv.strip().lines().toList();
        if (rows.isEmpty() || !rows.getFirst().equals("period,category,count,spent"))
            throw new IllegalArgumentException("첫 행은 period,category,count,spent여야 합니다.");
        Map<String, Amounts> legacy = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            var fields = rows.get(i).split(",", -1);
            if (fields.length != 4 || !fields[0].equals(statement.period()) ||
                    !List.of("EDUCATION", "CONFERENCE", "TRAVEL").contains(fields[1]) ||
                    !fields[2].matches("[0-9]{1,15}") || !fields[3].matches("(-?[0-9]{1,15})?"))
                throw new IllegalArgumentException((i + 1) + "행의 기간·비목·건수·집행액을 확인하세요.");
            var values = new Amounts(Long.parseLong(fields[2]), fields[3].isEmpty() ? null : Long.valueOf(fields[3]));
            if (legacy.putIfAbsent(fields[1], values) != null)
                throw new IllegalArgumentException((i + 1) + "행에 중복 비목이 있습니다.");
        }
        Map<String, Amounts> ledger = new LinkedHashMap<>();
        for (var line : statement.lines()) ledger.put(line.category(), new Amounts(line.claimCount(), line.spent()));
        var categories = new TreeSet<>(legacy.keySet());
        categories.addAll(ledger.keySet());
        var differences = new ArrayList<Difference>();
        for (String category : categories) {
            var old = legacy.get(category);
            var current = ledger.get(category);
            if (!java.util.Objects.equals(old, current)) differences.add(new Difference(category,
                    old == null ? null : old.count(), old == null ? null : old.spent(),
                    current == null ? null : current.count(), current == null ? null : current.spent()));
        }
        return differences;
    }
}
