package kr.budget.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ExpenseFormula {
    public static final String EDUCATION = "floor(min(eligible_cost * support_rate, event_cap))";
    public static final String TRAVEL = "floor(min(eligible_cost, days * daily_cap))";

    public record Inputs(long cost, BigDecimal rate, long cap, int days, long dailyCap) {
        public Inputs {
            if (cost < 0 || cap < 0 || days < 1 || dailyCap < 0 || rate == null ||
                    rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("계산 입력 범위를 확인하세요.");
            checked(rate);
        }
    }

    private static BigDecimal checked(BigDecimal value) {
        if (value.precision() > 38 || Math.abs(value.scale()) > 38)
            throw new IllegalArgumentException("계산 범위를 초과했습니다.");
        return value;
    }

    private record Node(int money, int days, int height, Function<Inputs,BigDecimal> operation) {
        BigDecimal evaluate(Inputs input) {
            return checked(operation.apply(input));
        }

        void requireCompatible(Node other, int position) {
            if (money != other.money || days != other.days)
                throw diagnostic(position, "단위가 일치하지 않습니다.");
        }
    }

    private static final Map<String,Node> VARIABLES = Map.of(
            "eligible_cost", new Node(1,0,1,i -> BigDecimal.valueOf(i.cost())),
            "event_cap", new Node(1,0,1,i -> BigDecimal.valueOf(i.cap())),
            "support_rate", new Node(0,0,1,Inputs::rate),
            "days", new Node(0,1,1,i -> BigDecimal.valueOf(i.days())),
            "daily_cap", new Node(1,-1,1,i -> BigDecimal.valueOf(i.dailyCap())));

    private record CacheKey(String digest, String evaluator) {}
    private static final int CACHE_LIMIT = 256;
    private static final LinkedHashMap<CacheKey,Compiled> CACHE = new LinkedHashMap<>(32,0.75f,true);

    static final class Compiled {
        private final Node root;
        private final int end;

        private Compiled(Node root, int end) {
            this.root = root;
            this.end = end;
        }

        long calculate(Inputs input) {
            BigDecimal value = root.evaluate(input);
            if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(input.cost())) > 0)
                throw diagnostic(end,"인정액은 실비 이하의 원 단위 금액이어야 합니다.");
            return value.setScale(0, RoundingMode.FLOOR).longValueExact();
        }
    }

    private final String source;
    private final boolean enforceTreeLimits;
    private int pos;
    private int atoms;
    private int nodes;

    private ExpenseFormula(String source, boolean enforceTreeLimits) {
        this.source = source;
        this.enforceTreeLimits = enforceTreeLimits;
    }

    public static long calculate(String source, Inputs input) {
        return calculate(source, input, false);
    }

    static long calculate(String source, Inputs input, boolean enforceTreeLimits) {
        return compile(source,enforceTreeLimits).calculate(input);
    }

    static Compiled compile(String source, boolean enforceTreeLimits) {
        if (source == null || source.length() > 2048 || source.isBlank())
            throw new IllegalArgumentException("수식 길이를 확인하세요.");
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var key = new CacheKey(digest,enforceTreeLimits ? PolicyVersion.DECIMAL_V2 : PolicyVersion.DECIMAL_V1);
        synchronized (CACHE) {
            Compiled cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        var parser = new ExpenseFormula(source,enforceTreeLimits);
        Node root = parser.sum(0);
        parser.skipSpace();
        if (parser.pos != source.length() || root.money != 1 || root.days != 0)
            throw diagnostic(parser.pos,"인정액은 실비 이하의 원 단위 금액이어야 합니다.");
        var compiled = new Compiled(root,parser.pos);
        synchronized (CACHE) {
            Compiled existing = CACHE.putIfAbsent(key,compiled);
            if (CACHE.size() > CACHE_LIMIT) CACHE.pollFirstEntry();
            return existing == null ? compiled : existing;
        }
    }

    private Node node(Node value) {
        if (enforceTreeLimits && (++nodes > 256 || value.height > 32))
            throw diagnostic(pos,"수식 트리는 깊이 32단계, 노드 256개까지 허용됩니다.");
        return value;
    }

    private Node sum(int depth) {
        Node result = product(depth);
        for (;;) {
            boolean plus = take('+');
            if (!plus && !take('-')) return result;
            skipSpace();
            int start = pos;
            Node left = result;
            Node right = product(depth);
            left.requireCompatible(right,start);
            result = node(new Node(left.money,left.days,1 + Math.max(left.height,right.height),
                    plus ? i -> left.evaluate(i).add(right.evaluate(i)) : i -> left.evaluate(i).subtract(right.evaluate(i))));
        }
    }

    private Node product(int depth) {
        Node result = atom(depth);
        for (;;) {
            boolean multiply = take('*');
            if (!multiply && !take('/')) return result;
            Node left = result;
            Node right = atom(depth);
            int end = pos;
            int height = 1 + Math.max(left.height,right.height);
            result = multiply ? node(new Node(left.money + right.money,left.days + right.days,height,
                    i -> left.evaluate(i).multiply(right.evaluate(i)))) :
                    node(new Node(left.money - right.money,left.days - right.days,height,
                            i -> divide(left.evaluate(i),right.evaluate(i),end)));
        }
    }

    private static BigDecimal divide(BigDecimal left, BigDecimal right, int end) {
        if (right.signum() == 0) throw diagnostic(end,"0으로 나눌 수 없습니다.");
        return left.divide(right,8,RoundingMode.HALF_EVEN);
    }

    private Node atom(int depth) {
        if ((!enforceTreeLimits && ++atoms > 256) || depth > 32) throw diagnostic(pos,"수식이 너무 복잡합니다.");
        if (take('(')) {
            Node value = sum(depth + 1);
            require(')');
            return value;
        }
        int start = pos;
        while (pos < source.length() && (Character.isLetter(source.charAt(pos)) || source.charAt(pos) == '_')) pos++;
        if (pos != start) {
            String name = source.substring(start,pos);
            Node variable = VARIABLES.get(name);
            if (variable != null) return node(variable);
            if (!name.equals("floor") && !name.equals("min") && !name.equals("max"))
                throw diagnostic(pos,"등록되지 않은 이름입니다.");
            require('(');
            Node left = sum(depth + 1);
            Node result;
            if (name.equals("floor")) {
                result = new Node(left.money,left.days,1 + left.height,i -> left.evaluate(i).setScale(0,RoundingMode.FLOOR));
            } else {
                require(',');
                skipSpace();
                int argumentStart = pos;
                Node right = sum(depth + 1);
                left.requireCompatible(right,argumentStart);
                result = new Node(left.money,left.days,1 + Math.max(left.height,right.height),
                        name.equals("min") ? i -> left.evaluate(i).min(right.evaluate(i)) :
                                i -> left.evaluate(i).max(right.evaluate(i)));
            }
            require(')');
            return node(result);
        }
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) pos++;
        try {
            BigDecimal number = checked(new BigDecimal(source.substring(start,pos)));
            return node(new Node(0,0,1,i -> number));
        } catch (NumberFormatException e) {
            throw diagnostic(pos,"숫자 또는 변수가 필요합니다.");
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
        if (!take(token)) throw diagnostic(pos,token + " 문자가 필요합니다.");
    }

    private static IllegalArgumentException diagnostic(int position, String reason) {
        return new IllegalArgumentException((position + 1) + "번째 문자: " + reason);
    }
}
