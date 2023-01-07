package grakkit.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {

    public static Object getValue(Method method, Object target, boolean Static, Object ...args) throws InvocationTargetException, IllegalAccessException {
        method.setAccessible(true);
        Object value = Static ? method.invoke(null, args) : method.invoke(target, args);
        return value;
    }

    public static Object getValue(Field field, Object target, boolean Static) throws IllegalAccessException {
        field.setAccessible(true);
        Object value = Static ? field.get(null) : field.get(target);
        return value;
    }

    public static void setField(Field field, Object target, Object value) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(target, value);
    }
}
