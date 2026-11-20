package kr.budget.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class ExpenseFormula {
    public static final String EDUCATION = "floor(min(eligible_cost * support_rate, event_cap))";
    public static final String TRAVEL = "floor(min(eligible_cost, days * daily_cap))";

    public record Inputs(long cost, BigDecimal rate, long cap, int days, long dailyCap) {
        public Inputs {
            if (cost < 0 || cap < 0 || days < 1 || dailyCap < 0 || rate == null ||
                    rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("계산 입력 범위를 확인하세요.");
        }
    }

    private record Value(BigDecimal amount, int money, int days, int height) {
        Value(BigDecimal amount, int money, int days) {
            this(amount, money, days, 1);
        }
        Value {
            if (amount.precision() > 38 || Math.abs(amount.scale()) > 38)
                throw new IllegalArgumentException("계산 범위를 초과했습니다.");
        }
        Value compatible(Value other, int position) {
            if (money != other.money || days != other.days)
                throw new IllegalArgumentException((position + 1) + "번째 문자: 단위가 일치하지 않습니다.");
            return other;
        }
    }

    private final String source;
    private final Map<String, Value> variables;
    private int pos;
    private int nodes;
    private int expressionNodes;
    private boolean enforceTreeLimits;

    private ExpenseFormula(String source, Inputs i) {
        if (source == null || source.isBlank() || source.length() > 2048)
            throw new IllegalArgumentException("수식 길이를 확인하세요.");
        this.source = source;
        variables = Map.of("eligible_cost", new Value(BigDecimal.valueOf(i.cost()), 1, 0),
                "event_cap", new Value(BigDecimal.valueOf(i.cap()), 1, 0),
                "support_rate", new Value(i.rate(), 0, 0),
                "days", new Value(BigDecimal.valueOf(i.days()), 0, 1),
                "daily_cap", new Value(BigDecimal.valueOf(i.dailyCap()), 1, -1));
    }

    public static long calculate(String source, Inputs input) {
        return calculate(source, input, false);
    }

    static long calculate(String source, Inputs input, boolean enforceTreeLimits) {
        ExpenseFormula parser = new ExpenseFormula(source, input);
        parser.enforceTreeLimits = enforceTreeLimits;
        Value value = parser.sum(0);
        parser.skipSpace();
        if (parser.pos != source.length() || value.money != 1 || value.days != 0 ||
                value.amount.signum() < 0 || value.amount.compareTo(BigDecimal.valueOf(input.cost())) > 0)
            throw parser.error("인정액은 실비 이하의 원 단위 금액이어야 합니다.");
        return value.amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private Value node(Value value) {
        if (enforceTreeLimits && (++expressionNodes > 256 || value.height > 32))
            throw error("수식 트리는 깊이 32단계, 노드 256개까지 허용됩니다.");
        return value;
    }

    private Value sum(int depth) {
        Value left = product(depth);
        for (;;) {
            boolean plus = take('+');
            if (!plus && !take('-')) return left;
            skipSpace();
            int operandStart = pos;
            Value right = left.compatible(product(depth), operandStart);
            left = node(new Value(plus ? left.amount.add(right.amount) : left.amount.subtract(right.amount),
                    left.money, left.days, 1 + Math.max(left.height, right.height)));
        }
    }

    private Value product(int depth) {
        Value left = atom(depth);
        for (;;) {
            boolean multiply = take('*');
            if (!multiply && !take('/')) return left;
            Value right = atom(depth);
            int height = 1 + Math.max(left.height, right.height);
            if (multiply) {
                left = node(new Value(left.amount.multiply(right.amount), left.money + right.money, left.days + right.days, height));
            } else {
                if (right.amount.signum() == 0) throw error("0으로 나눌 수 없습니다.");
                left = node(new Value(left.amount.divide(right.amount, 8, RoundingMode.HALF_EVEN), left.money - right.money, left.days - right.days, height));
            }
        }
    }

    private Value atom(int depth) {
        if ((!enforceTreeLimits && ++nodes > 256) || depth > 32) throw error("수식이 너무 복잡합니다.");
        if (take('(')) {
            Value value = sum(depth + 1);
            require(')');
            return value;
        }
        int start = pos;
        while (pos < source.length() && (Character.isLetter(source.charAt(pos)) || source.charAt(pos) == '_')) pos++;
        if (pos != start) {
            String name = source.substring(start, pos);
            if (variables.containsKey(name)) return node(variables.get(name));
            if (!name.equals("floor") && !name.equals("min") && !name.equals("max")) throw error("등록되지 않은 이름입니다.");
            require('(');
            Value a = sum(depth + 1);
            BigDecimal result;
            int height = 1 + a.height;
            if (name.equals("floor")) {
                result = a.amount.setScale(0, RoundingMode.FLOOR);
            } else {
                require(',');
                skipSpace();
                int argumentStart = pos;
                Value b = a.compatible(sum(depth + 1), argumentStart);
                height = 1 + Math.max(a.height, b.height);
                result = name.equals("min") ? a.amount.min(b.amount) : a.amount.max(b.amount);
            }
            require(')');
            return node(new Value(result, a.money, a.days, height));
        }
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) pos++;
        try {
            return node(new Value(new BigDecimal(source.substring(start, pos)), 0, 0));
        } catch (NumberFormatException e) {
            throw error("숫자 또는 변수가 필요합니다.");
        }
    }

    private boolean take(char token) {
        skipSpace();
        if (pos >= source.length() || source.charAt(pos) != token) return false;
        pos++;
        return true;
    }

    private void skipSpace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
    }

    private void require(char token) {
        if (!take(token)) throw error(token + " 문자가 필요합니다.");
    }

    private IllegalArgumentException error(String reason) {
        return new IllegalArgumentException((pos + 1) + "번째 문자: " + reason);
    }
}
