package com.yheriatovych.reductor;

import com.yheriatovych.reductor.annotations.ActionCreator;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class Actions {
    private static ConcurrentHashMap<Class<?>, Object> classCache = new ConcurrentHashMap<>();

    /**
     * Create instance of interface with action creator functions.
     * <p>
     * When reductor processor is available, will instantiate generated implementation.
     * If generated class isn't available, fallback to dynamic proxy implementation.
     * <p>
     * Created instances are cached, so it's cheap to call this method many times for the same interface.
     *
     * @param actionCreator interface class
     * @param <T>           type of provided interface
     * @return instance of type T
     */
    public static <T> T from(Class<T> actionCreator) {
        Object creator = classCache.get(actionCreator);
        if (creator == null) {
            creator = createCreator(actionCreator);
            classCache.put(actionCreator, creator);
        }
        return (T) creator;
    }

    private static Object createCreator(Class<?> actionCreator) {
        String className = actionCreator.getName();
        String generatedActionCreator = className + "_AutoImpl";

        try {
            Class<?> generatedClass = Class.forName(generatedActionCreator);
            //Ok, if class exists then it was generated by our annotation processor
            //so no need to validate
            return generatedClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            //should not happen with auto generated classes
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            //if there are not generated action creators just fallback to dynamic proxy
            return createDynamicProxy(actionCreator);
        }
    }

    private static Object createDynamicProxy(Class<?> actionCreator) {
        if (actionCreator.getAnnotation(ActionCreator.class) == null) {
            throw new IllegalStateException(String.format(
                    "%s should be interface annotated with @%s", actionCreator, ActionCreator.class.getSimpleName()));
        }

        Method[] methods = actionCreator.getMethods();
        final HashMap<Method, String> actionsMap = new HashMap<>(methods.length);
        for (Method method : methods) {
            ActionCreator.Action annotation = method.getAnnotation(ActionCreator.Action.class);
            if (annotation == null) {
                throw new IllegalStateException(
                        String.format("Method %s should be annotated with @%s",
                                method, ActionCreator.Action.class.getCanonicalName()));
            }
            actionsMap.put(method, annotation.value());
        }
        return Proxy.newProxyInstance(actionCreator.getClassLoader(), new Class<?>[]{actionCreator},
                (instance, method, args) -> {
                    String action = actionsMap.get(method);
                    if (args == null) {
                        args = new Object[0];
                    }
                    return new Action(action, args);
                });
    }

}
