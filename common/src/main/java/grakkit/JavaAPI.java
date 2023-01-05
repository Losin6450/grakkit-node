package grakkit;

import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.interop.converters.JavetProxyConverter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;

public class JavaAPI {

   private Instance instance;

   public JavaAPI(Instance i){
      instance = i;
   }

   public Class<?> type(String type) throws ClassNotFoundException {
      return Class.forName(type);
   }

   public Object proxy(ArrayList<String> interfaces, HashMap<String,String> handler) throws ClassNotFoundException {
      Class<?>[] classes = new Class[interfaces.size()];
      int i = 0;
      for (String interfac : interfaces){
         classes[i] = Class.forName(interfac);
         i++;
      }
      return Proxy.newProxyInstance(JavaAPI.class.getClassLoader(), classes, new MyInvocationHandler(handler));
   }

   private class MyInvocationHandler implements InvocationHandler {

      private HashMap<String, String> handler;


      private MyInvocationHandler(HashMap<String,String> h){
         handler = h;
      }
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         NodeRuntime runtime = instance.engine.getV8Runtime();
         runtime.setConverter(new JavetProxyConverter());
         runtime.getGlobalObject().set("Java", new JavaAPI(instance));
         runtime.getGlobalObject().set("Grakkit", new GrakkitAPI(instance));
         runtime.getExecutor(handler.get(method.getName())).executeVoid();
         Object retu = runtime.getGlobalObject().invoke(method.getName(), proxy, args);
         runtime.close();
         return retu;
      }
   }
}
