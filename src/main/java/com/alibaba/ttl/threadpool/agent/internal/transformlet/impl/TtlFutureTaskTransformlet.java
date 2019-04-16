package com.alibaba.ttl.threadpool.agent.internal.transformlet.impl;

import com.alibaba.ttl.threadpool.agent.internal.logging.Logger;
import com.alibaba.ttl.threadpool.agent.internal.transformlet.JavassistTransformlet;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.IOException;
import java.lang.reflect.Modifier;

import static com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.*;

/**
 * TTL {@link JavassistTransformlet} for {@link java.util.TimerTask}.
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @author wuwen5 (wuwen.55 at aliyun dot com)
 * @see java.util.TimerTask
 * @see java.util.Timer
 * @since 2.7.0
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


                // 更新 构造方法
                CtConstructor[] constructMethods = clazz.getConstructors();
                for (CtConstructor ctMethod : constructMethods) {
                    String code = "capturedRef = new java.util.concurrent.atomic.AtomicReference(com.alibaba.ttl.TransmittableThreadLocal.Transmitter.capture());";
                    ctMethod.insertBefore(code);
                }


                // 更新 run方法
                CtMethod method = clazz.getDeclaredMethod("run");


                String code2 = "Object captured = capturedRef.get();";
                String code3 = "backup = com.alibaba.ttl.TransmittableThreadLocal.Transmitter.replay(captured);";
                final String beforeCode = code2 + code3;
//                method.insertBefore(code2 + code3);
//
                final String afterCode = "com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(backup);";
//                method.insertAfter(code4);


                method.instrument(new ExprEditor() {
                    public void edit(MethodCall m)
                            throws CannotCompileException {
                        System.out.println(m.getClassName());
                        System.out.println("method:" + m.getMethodName());
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
