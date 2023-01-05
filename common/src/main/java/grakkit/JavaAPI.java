package grakkit;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.NodeRuntime;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.reference.V8ValueArray;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;

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

   public Object proxy(V8ValueArray interfaces, V8ValueObject handler) throws JavetException {
      ArrayList<?> classes = new ArrayList<>();
      int i = 0;
      for (Integer interfac : interfaces.getKeys()){
         classes.add(interfaces.getObject(interfac));
         i++;
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
}
