package com.alibaba.ttl.threadpool.agent.internal.transformlet.impl;

import com.alibaba.ttl.TtlCallable;
import com.alibaba.ttl.threadpool.agent.internal.logging.Logger;
import com.alibaba.ttl.threadpool.agent.internal.transformlet.JavassistTransformlet;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.IOException;
import java.lang.reflect.Modifier;

import static com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.*;

/**
 * @author miaoshui
 */
public class TtlFutureTaskTransformlet implements JavassistTransformlet {
    private static final Logger logger = Logger.getLogger(TtlFutureTaskTransformlet.class);

    @Override
    public byte[] doTransform(String className, byte[] classFileBuffer, ClassLoader loader) throws IOException, NotFoundException, CannotCompileException {
        try {

            if (className.equals("java.util.concurrent.FutureTask")) {
                final CtClass clazz = getCtClass(classFileBuffer, loader);

                // 添加变量
                CtField f = CtField.make("private java.util.concurrent.atomic.AtomicReference capturedRef;", clazz);
                clazz.addField(f);
                CtField f2 = CtField.make("private Object backup;", clazz);
                clazz.addField(f2);
                CtField f3 = CtField.make("private boolean isApiTtl;", clazz);
                clazz.addField(f3);


                // 更新 构造方法
                CtConstructor[] constructMethods = clazz.getConstructors();
                for (CtConstructor ctMethod : constructMethods) {
                    String consCode = "capturedRef = new java.util.concurrent.atomic.AtomicReference(com.alibaba.ttl.TransmittableThreadLocal.Transmitter.capture());";
                    String consCode2 = "isApiTtl=($1 instanceof com.alibaba.ttl.TtlCallable || $1 instanceof com.alibaba.ttl.TtlRunnable)?true:false;";

                    ctMethod.insertBefore(consCode + consCode2);
                }

                // 更新 run方法
                CtMethod method = clazz.getDeclaredMethod("run");


                String code1 = "if(!isApiTtl){";
                String code2 = "Object captured = capturedRef.get();";
                String code3 = "backup = com.alibaba.ttl.TransmittableThreadLocal.Transmitter.replay(captured);}";
                final String beforeCode = code1 + code2 + code3;
//
                final String afterCode = "if(!isApiTtl){com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(backup);}";


                method.instrument(new ExprEditor() {
                    public void edit(MethodCall m)
                            throws CannotCompileException {
                        //System.out.println(m.getClassName());
                        //System.out.println("method:" + m.getMethodName());
                        // 替换run方法中的 c.call()
                        if (m.getClassName().equals("java.util.concurrent.Callable")
                                && m.getMethodName().equals("call"))
                            m.replace("{ try {" + beforeCode + "$_ = $proceed($$);}finally{" + afterCode + "} }");
                    }
                });

                return clazz.toBytecode();
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
