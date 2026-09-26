package com.sabyeguyz.clinic.cypress;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Repository boundary double for v2 service scenarios; assertions belong to Cypress. */
public final class V2RepositoryStub<T> {
    private final T proxy;
    private final Map<String, Function<Object[], Object>> answers = new HashMap<>();
    private final Map<String, List<Object[]>> invocations = new HashMap<>();

    public V2RepositoryStub(Class<T> type) {
        proxy = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            Object[] arguments = args == null ? new Object[0] : args;
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RepositoryDouble(" + type.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == arguments[0];
                    default -> null;
                };
            }
            invocations.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(arguments.clone());
            if (answers.containsKey(method.getName())) return answers.get(method.getName()).apply(arguments);
            if (method.getName().equals("save")) return arguments[0];
            Class<?> result = method.getReturnType();
            if (result == Optional.class) return Optional.empty();
            if (result == List.class || result == Iterable.class || result == Collection.class) return List.of();
            if (result == boolean.class) return false;
            if (result == long.class) return 0L;
            if (result == int.class) return 0;
            return null;
        }));
    }

    public T proxy() { return proxy; }
    public V2RepositoryStub<T> on(String method, Function<Object[], Object> answer) {
        answers.put(method, answer);
        return this;
    }
    public int calls(String method) { return arguments(method).size(); }
    public List<Object[]> arguments(String method) { return invocations.getOrDefault(method, List.of()); }

    public static Map<String, Object> outcome(Supplier<?> action) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("value", action.get());
        } catch (RuntimeException exception) {
            result.put("error", exception.getClass().getSimpleName());
            result.put("errorMessage", exception.getMessage());
        }
        return result;
    }
}
