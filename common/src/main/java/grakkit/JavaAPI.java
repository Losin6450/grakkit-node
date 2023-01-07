package grakkit;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.engine.JavetEngine;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.primitive.V8ValueBoolean;
import com.caoccao.javet.values.reference.V8ValueArray;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;
import grakkit.reflection.ReflectionUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class JavaAPI {

   private Instance instance;
   public static HashMap<Class<?>, HashMap<String, String>> classextends = new HashMap<>();

   public JavaAPI(Instance i){
      instance = i;
   }

   public Class<?> type(String type) throws ClassNotFoundException {
      return Class.forName(type);
   }

   public Object proxy(V8ValueArray interfaces, V8ValueObject handler) throws JavetException {
      ArrayList<?> classes = new ArrayList<>();
      for (Integer interfac : interfaces.getKeys()){
         classes.add(interfaces.getObject(interfac));
      }
      HashMap<String, String> nhandler = new HashMap<>();
      for(String key : handler.getOwnPropertyNameStrings()){
         nhandler.put(key, ((V8ValueFunction) handler.get(key)).getSourceCode());
      }
      JavetResourceUtils.safeClose(handler);
      return Proxy.newProxyInstance(JavaAPI.class.getClassLoader(), classes.toArray(new Class[0]), new MyInvocationHandler(nhandler));
   }

   private class MyInvocationHandler implements InvocationHandler {

      private final HashMap<String, String> handler;


      private MyInvocationHandler(HashMap<String,String> h){
         handler = h;
      }
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if(!handler.containsKey(method.getName())) return new Object[0];
         NodeRuntime runtime = instance.runtime;
         V8ValueFunction func = runtime.getExecutor(handler.get(method.getName())).execute();
         Object retu = func.call(null, proxy, args);
         JavetResourceUtils.safeClose(func);
         return retu;
      }
   }


   public Class<?> extend(Class<?> superClass, V8ValueArray interfaces, boolean thisInstance, V8ValueObject handler) throws JavetException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException {
      ByteBuddy buddy = new ByteBuddy();
      ArrayList<?> interfacess = new ArrayList<>();
      for(Integer i : interfaces.getKeys()){
         interfacess.add(interfaces.getObject(i));
      }
      DynamicType.Builder builder = buddy.subclass(superClass).implement(interfacess.toArray(new Class[0]));
      HashMap<String, String> nhandler = new HashMap<>();
      for(String key : handler.getOwnPropertyNameStrings()) {
         nhandler.put(key, ((V8ValueFunction) handler.get(key)).getSourceCode());
      }
      Optional<HashMap<String, String>> h = Optional.of(nhandler);
      Optional<Instance> inst = Optional.of(instance);
      Optional<Boolean> ui = Optional.of(thisInstance);
      DynamicType.Unloaded unloaded = builder
              .defineField("__v8instance__", Instance.class, Visibility.PRIVATE, Ownership.STATIC)
              .defineField("__handler__", HashMap.class, Visibility.PRIVATE, Ownership.STATIC)
              .defineField("__useinstance__", boolean.class, Visibility.PRIVATE, Ownership.STATIC)
              .method(ElementMatchers.any())
              .intercept(MethodDelegation.to(new Interceptor(h,inst,ui)))
      .make();
      Class<?> done = unloaded.load(JavaAPI.class.getClassLoader()).getLoaded();
      ReflectionUtils.setField(done.getDeclaredField("__v8instance__"), null, instance);
      ReflectionUtils.setField(done.getDeclaredField("__handler__"), null, nhandler);
      ReflectionUtils.setField(done.getDeclaredField("__useinstance__"), null, thisInstance);
      return done;


   }

   public static class Interceptor {

      private Optional<HashMap<String, String>> handler;
      private Optional<Instance> instance;
      private Optional<Boolean> useInstance;

      public Interceptor(Optional<HashMap<String,String>> h, Optional<Instance> i, Optional<Boolean> ui){
         this.handler = h;
         this.instance = i;
         this.useInstance = ui;
      }

      @RuntimeType
      public Object interceptSuper(@This Object self, @Origin Method method, @AllArguments(nullIfEmpty = true) Object[] args, @SuperMethod(nullIfImpossible = true) Method superMethod) throws Throwable {
         return intercept0(self, method, args, superMethod);
      }

      @RuntimeType
      public Object interceptStaticSuper(@Origin Method method, @AllArguments(nullIfEmpty = true) Object[] args, @SuperMethod(nullIfImpossible = true) Method superMethod) throws JavetException, NoSuchFieldException, IllegalAccessException {
         return intercept0(null, method, args, superMethod);
      }

      @RuntimeType
      public Object intercept(@This Object self, @Origin Method method, @AllArguments(nullIfEmpty = true) Object[] args) throws JavetException, NoSuchFieldException, IllegalAccessException {
         return intercept0(self, method, args, null);
      }

      @RuntimeType
      public Object interceptStatic(@Origin Method method, @AllArguments(nullIfEmpty = true) Object[] args) throws JavetException, NoSuchFieldException, IllegalAccessException {
         return intercept0(null, method, args, null);
      }

      private Object intercept0(Object self, Method method, Object[] args, Method superMethod) throws NoSuchFieldException, IllegalAccessException, JavetException {
         boolean useinst = useInstance.orElse(false);
         V8Runtime runtime;
         Instance instance = null;
         JavetEngine engine = null;
         if(useinst){
            instance = this.instance.get();
            runtime = instance.runtime;
         } else {
            engine = (JavetEngine) Grakkit.pool.getEngine();
            runtime = engine.getV8Runtime();
         }
         HashMap<String, String> handler = this.handler.get();
         if(!useinst){
            runtime.getGlobalObject().set("Java", new JavaAPI(instance));
         }
         V8ValueFunction func = runtime.getExecutor(handler.get(method.getName())).execute();
         Object retu = func.call(null, self, method, superMethod, args);
         JavetResourceUtils.safeClose(func);
         if(!useinst){
            JavetResourceUtils.safeClose(runtime);
            JavetResourceUtils.safeClose(engine);
         }
         if(retu instanceof V8ValueBoolean){
            retu = ((V8ValueBoolean) retu).getValue();
         }
         return retu;
      }
   }

   public Object getFieldValue(Class<?> cls, String field, Object target) throws NoSuchFieldException, IllegalAccessException {
      Field f = cls.getDeclaredField(field);
      f.setAccessible(true);
      return f.get(target);
   }

   public void setFieldValue(Class<?> cls, String field, Object target, Object value) throws NoSuchFieldException, IllegalAccessException {
      Field f = cls.getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
   }

}
