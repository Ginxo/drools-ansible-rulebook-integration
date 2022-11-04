package org.drools.ansible.rulebook.integration.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.drools.ansible.rulebook.integration.api.domain.conditions.OnceWithinDefinition;
import org.drools.ansible.rulebook.integration.api.rulesmodel.PrototypeFactory;
import org.drools.model.Drools;
import org.drools.model.PrototypeDSL;
import org.drools.model.PrototypeFact;
import org.drools.model.PrototypeVariable;
import org.drools.model.Rule;

import static org.drools.ansible.rulebook.integration.api.rulesmodel.PrototypeFactory.getPrototype;
import static org.drools.model.PrototypeDSL.protoPattern;
import static org.drools.model.PrototypeDSL.variable;

public class RuleGenerationContext {

    private final RuleConfigurationOptions options;

    private final StackedContext<String, PrototypeDSL.PrototypePatternDef> patterns = new StackedContext<>();

    private int bindingsCounter = 0;

    private OnceWithinDefinition onceWithin;

    public RuleGenerationContext(RuleConfigurationOptions options) {
        this.options = options;
    }

    public PrototypeDSL.PrototypePatternDef getOrCreatePattern(String binding, String name) {
        return patterns.computeIfAbsent(binding, b -> protoPattern(variable(getPrototype(name), b)));
    }

    public PrototypeVariable getPatternVariable(String binding) {
        PrototypeDSL.PrototypePatternDef patternDef = patterns.get(binding);
        return patternDef != null ? (PrototypeVariable) patternDef.getFirstVariable() : null;
    }

    public boolean isExistingBoundVariable(String binding) {
        return patterns.get(binding) != null;
    }

    public PrototypeDSL.PrototypePatternDef getBoundPattern(String binding) {
        return patterns.get(binding);
    }

    public void pushContext() {
        patterns.pushContext();
    }

    public void popContext() {
        patterns.popContext();
    }

    public String generateBinding() {
        String binding = bindingsCounter == 0 ? "m" : "m_" + bindingsCounter;
        bindingsCounter++;
        return binding;
    }

    public boolean hasOption(RuleConfigurationOption option) {
        return options != null && options.hasOption(option);
    }

    public static boolean isGeneratedBinding(String binding) {
        return binding.equals("m") || binding.startsWith("m_");
    }

    public OnceWithinDefinition getOnceWithin() {
        return onceWithin;
    }

    public void setOnceWithin(OnceWithinDefinition onceWithin) {
        this.onceWithin = onceWithin;
    }

    public PrototypeVariable getConsequenceVariable() {
        return onceWithin != null ? onceWithin.getGuardedVariable() : null;
    }

    public void executeSyntheticConsequence(Drools drools, PrototypeFact fact) {
        if (onceWithin != null) {
            onceWithin.insertGuardConsequence(this).accept(drools, fact);
        }
    }

    public Rule getSyntheticRule() {
        return onceWithin != null ? onceWithin.cleanupRule(this) : null;
    }

    private static class StackedContext<K, V> {
        private final Deque<Map<K, V>> stack = new ArrayDeque<>();

        public StackedContext() {
            pushContext();
        }

        public void pushContext() {
            stack.addFirst(new HashMap<>());
        }

        public void popContext() {
            stack.removeFirst();
        }

        public V get(K key) {
            for (Map<K,V> map : stack) {
                V value = map.get(key);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        public void put(K key, V value) {
            stack.getFirst().put(key, value);
        }

        public V computeIfAbsent(K key, Function<K, V> f) {
            V value = get(key);
            if (value != null) {
                return value;
            }
            value = f.apply(key);
            put(key, value);
            return value;
        }
    }
}
