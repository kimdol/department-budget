package kr.budget.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public sealed interface EligibilityRule<T> {
    enum Verdict { PASS, FAIL, MISSING }
    record Step(String name, Verdict verdict) {}
    record Decision(Verdict verdict, List<Step> evaluated, List<String> skipped) {
        public Decision {
            evaluated = List.copyOf(evaluated);
            skipped = List.copyOf(skipped);
        }
    }
    String name();
    Decision evaluate(T input);

    record Fact<T>(String name, Function<T,Verdict> check) implements EligibilityRule<T> {
        public Decision evaluate(T input) {
            var value = check.apply(input);
            return new Decision(value,List.of(new Step(name,value)),List.of());
        }
    }

    enum Operator { ALL, ANY }
    record Group<T>(String name, Operator operator, List<EligibilityRule<T>> children) implements EligibilityRule<T> {
        public Group {
            children = List.copyOf(children);
            if (children.isEmpty()) throw new IllegalArgumentException("조건을 하나 이상 지정하세요.");
        }

        public Decision evaluate(T input) {
            Verdict decisive = operator == Operator.ALL ? Verdict.FAIL : Verdict.PASS;
            Verdict outcome = operator == Operator.ALL ? Verdict.PASS : Verdict.FAIL;
            var evaluated = new ArrayList<Step>();
            var skipped = new ArrayList<String>();
            for (var child : children) {
                if (outcome == decisive) {
                    skipped.add(child.name());
                    continue;
                }
                var result = child.evaluate(input);
                evaluated.addAll(result.evaluated());
                skipped.addAll(result.skipped());
                if (result.verdict() == decisive || result.verdict() == Verdict.MISSING) outcome = result.verdict();
            }
            evaluated.add(new Step(name,outcome));
            return new Decision(outcome,evaluated,skipped);
        }
    }
}
